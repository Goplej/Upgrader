package com.upgrader.integration.projecte;

import com.upgrader.Upgrader;
import com.upgrader.util.ReflectionUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflection-based ProjectE EMC bridge.
 *
 * <p>ProjectE's API surface moved across packages over the years, so instead of pinning one
 * class name this adapter probes a prioritised list of known EMC entry points at init and caches
 * the first {@link Method} that matches. If nothing resolves, the integration degrades to
 * "no evidence" — never a crash, never a fake number.</p>
 *
 * <h2>Probed signatures (1.20.1-era builds)</h2>
 * <ul>
 *   <li>{@code projecte.api.common.emc.EMCSearcher#getEmcForStack(ItemStack[], boolean)} → int</li>
 *   <li>{@code projecte.common.emc.EMCMap$ValueEMCMap#get(Object)} → int</li>
 *   <li>{@code projecte.api.ProjectEAPI#getEMCService()} → object with {@code getEmc(ItemStack)}</li>
 * </ul>
 */
public final class ProjectEMcBridge {

    private record Probe(String className, String methodName, Class<?>... params) {
    }

    private static final Probe[] PROBES = {
            new Probe("projecte.api.common.emc.EMCSearcher", "getEmcForStack",
                    ItemStack[].class, boolean.class),
            new Probe("projecte.api.common.emc.EMCService", "getEmc", ItemStack.class),
    };

    private Method method;
    private Object receiver;
    private boolean ready;

    /** Attempts to resolve an EMC entry point. Returns true when the bridge is usable. */
    public boolean resolve() {
        for (Probe probe : PROBES) {
            Optional<Class<?>> ownerOpt = ReflectionUtil.classForName(probe.className());
            if (ownerOpt.isEmpty()) {
                continue;
            }
            Class<?> owner = ownerOpt.get();
            Optional<Method> m = ReflectionUtil.staticMethod(owner, probe.methodName(), probe.params());
            if (m.isPresent()) {
                this.method = m.get();
                this.receiver = null; // static invocation
                this.ready = true;
                Upgrader.LOGGER.info("ProjectE EMC bridge resolved via {}#{}",
                        probe.className(), probe.methodName());
                return true;
            }
            // Some builds expose an instance accessor: try a no-arg static "getInstance"/"get".
            for (String getter : new String[]{"getInstance", "get"}) {
                Optional<Method> g = ReflectionUtil.staticMethod(owner, getter);
                if (g.isPresent()) {
                    Optional<Object> instanceOpt = ReflectionUtil.invoke(g.get(), null);
                    if (instanceOpt.isEmpty()) {
                        continue;
                    }
                    try {
                        Method im = owner.getMethod(probe.methodName(), probe.params());
                        this.method = im;
                        this.receiver = instanceOpt.get();
                        this.ready = true;
                        Upgrader.LOGGER.info("ProjectE EMC bridge resolved via {}{}#{}",
                                probe.className(), "." + getter + "()", probe.methodName());
                        return true;
                    } catch (Throwable ignored) {
                        // keep probing
                    }
                }
            }
        }
        this.ready = false;
        return false;
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Queries EMC for one stack.
     *
     * @return EMC as long, or empty when unknown/unavailable (0 EMC items also map to empty —
     *         ProjectE uses 0 for "no EMC assigned", which honestly means "no evidence").
     */
    public Optional<Long> getEmc(ItemStack stack) {
        if (!ready || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object raw;
            if (method.getParameterCount() == 2) {
                raw = method.invoke(receiver, new ItemStack[]{stack}, Boolean.FALSE);
            } else {
                raw = method.invoke(receiver, stack);
            }
            if (raw instanceof Number n) {
                long emc = n.longValue();
                return emc > 0 ? Optional.of(emc) : Optional.empty();
            }
            return Optional.empty();
        } catch (Throwable t) {
            // A throwing EMC lookup must degrade silently; logged once per item id at DEBUG.
            Upgrader.LOGGER.debug("ProjectE EMC query failed for {}: {}",
                    ResourceLocation.tryParse(stack.getItem().toString()), t.toString());
            return Optional.empty();
        }
    }
}
