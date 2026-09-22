package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueContext;
import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Priority 900 &ndash; ProjectE EMC values.
 *
 * <p><b>STRICT: no direct imports.</b> ProjectE is an optional soft dependency and is never on the
 * compile classpath, so the {@code moze_intel.projecte.api.proxy.IEMCProxy} interface is reached
 * through reflection only. Two historical shapes of that interface are supported:</p>
 * <ul>
 *     <li>modern (1.20.x): static field {@code INSTANCE} and {@code long getValue(ItemStack)};</li>
 *     <li>legacy: static field {@code instance} and {@code long getEMC(ItemStack)}.</li>
 * </ul>
 *
 * <p>The lookup is bound once, lazily, on the first price request. When ProjectE is absent or its
 * API changed, the provider stays silent and the pipeline continues with the next provider.</p>
 */
public class ProjectEValueProvider implements ValueProvider {

    /** Execution priority. */
    public static final int PRIORITY = 900;

    /** Mod id of ProjectE. */
    public static final String PROJECTE_MOD_ID = "projecte";

    /** Fully qualified name of the ProjectE EMC proxy interface. */
    public static final String PROXY_CLASS_NAME = "moze_intel.projecte.api.proxy.IEMCProxy";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] INSTANCE_FIELD_NAMES = {"INSTANCE", "instance"};
    private static final String[] VALUE_METHOD_NAMES = {"getValue", "getEMC"};

    private static final Object BIND_LOCK = new Object();

    private static boolean resolved;
    private static Object proxy;
    private static Method valueMethod;

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getName() {
        return "projecte";
    }

    @Override
    public long getValue(ItemStack stack, ValueContext context) {
        if (stack == null || stack.isEmpty()) {
            return UNKNOWN;
        }
        if (!bind()) {
            return UNKNOWN;
        }

        try {
            Object result = valueMethod.invoke(proxy, stack);
            if (result instanceof Number number) {
                long emc = number.longValue();
                return emc > 0L ? emc : UNKNOWN;
            }
        } catch (Throwable throwable) {
            LOGGER.debug("ProjectE EMC lookup failed, falling through to the next provider", throwable);
        }
        return UNKNOWN;
    }

    /**
     * @return {@code true} when ProjectE is installed and its EMC proxy is usable
     */
    public static boolean isProjectELoaded() {
        try {
            return ModList.get().isLoaded(PROJECTE_MOD_ID);
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Binds the reflective handles exactly once.
     *
     * @return {@code true} when the proxy and the value method are available
     */
    private static boolean bind() {
        if (resolved) {
            return valueMethod != null;
        }

        synchronized (BIND_LOCK) {
            if (resolved) {
                return valueMethod != null;
            }
            resolved = true;

            try {
                if (!isProjectELoaded()) {
                    LOGGER.info("ProjectE is not installed, the EMC value provider stays inactive");
                    return false;
                }

                Class<?> proxyClass = Class.forName(PROXY_CLASS_NAME);

                Object instance = null;
                for (String fieldName : INSTANCE_FIELD_NAMES) {
                    try {
                        Field field = proxyClass.getField(fieldName);
                        instance = field.get(null);
                        if (instance != null) {
                            break;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // Try the next candidate field name.
                        continue;
                    }
                }

                if (instance == null) {
                    LOGGER.warn("ProjectE is installed but {} exposes no usable proxy instance", PROXY_CLASS_NAME);
                    return false;
                }

                Method method = null;
                for (String methodName : VALUE_METHOD_NAMES) {
                    try {
                        method = proxyClass.getMethod(methodName, ItemStack.class);
                        break;
                    } catch (ReflectiveOperationException ignored) {
                        // Try the next candidate method name.
                        continue;
                    }
                }

                if (method == null) {
                    LOGGER.warn("ProjectE is installed but {} offers no EMC lookup for ItemStack", PROXY_CLASS_NAME);
                    return false;
                }

                proxy = instance;
                valueMethod = method;
                LOGGER.info("ProjectE detected, EMC values are used with priority {}", PRIORITY);
                return true;
            } catch (Throwable throwable) {
                LOGGER.warn("ProjectE EMC binding failed, the provider stays inactive", throwable);
                proxy = null;
                valueMethod = null;
                return false;
            }
        }
    }
}
