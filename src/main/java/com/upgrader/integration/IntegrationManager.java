package com.upgrader.integration;

import com.upgrader.Upgrader;
import com.upgrader.api.UpgraderAPI;
import com.upgrader.integration.avaritia.AvaritiaIntegration;
import com.upgrader.integration.projecte.ProjectEIntegration;
import com.upgrader.integration.resourcealchemist.ResourceAlchemistIntegration;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Owns every {@link IntegrationProvider} and enforces the optional-dependency pattern:
 *
 * <ul>
 *   <li>Providers are registered as <em>suppliers</em>; the supplier (and therefore the adapter
 *       class) is never touched unless the target mod is actually loaded — so referencing
 *       ProjectE classes can never crash a ProjectE-free instance.</li>
 *   <li>{@link #initAvailable()} calls {@code isAvailable()} first, then {@code init()}, catching
 *       <strong>any</strong> {@link Throwable}: a broken integration logs ERROR and is dropped,
 *       the engine continues with the remaining sources.</li>
 *   <li>Successful providers contribute their {@code ValueSource} to the shared registry.</li>
 * </ul>
 */
public final class IntegrationManager {

    private static final Map<String, Supplier<IntegrationProvider>> PENDING = new LinkedHashMap<>();
    private static final List<IntegrationProvider> ACTIVE = new ArrayList<>();
    private static final List<IntegrationProvider> FAILED = new ArrayList<>();
    private static volatile boolean initialised;

    private IntegrationManager() {
    }

    /** Registers the integrations shipped with Upgrader. Idempotent. */
    public static synchronized void registerBuiltins() {
        registerIfLoaded("projecte", ProjectEIntegration::new);
        registerIfLoaded("avaritia", AvaritiaIntegration::new);
        // Re:Avaritia ships under its own mod ids depending on the fork build.
        registerIfLoaded("resourcealchemist", ResourceAlchemistIntegration::new);
        registerIfLoaded("avaritia_reborn", ResourceAlchemistIntegration::new);
    }

    /**
     * Defers a provider until the owning mod is confirmed present. The supplier is evaluated
     * lazily inside {@link #initAvailable()} — before the first touch of target-mod classes.
     */
    public static synchronized void registerIfLoaded(String modId, Supplier<IntegrationProvider> factory) {
        if (initialised) {
            Upgrader.LOGGER.warn("Integration '{}' registered after init; attempting immediate activation", modId);
            try {
                IntegrationProvider p = factory.get();
                activate(p);
            } catch (Throwable t) {
                Upgrader.LOGGER.error("Late integration {} failed", modId, t);
            }
            return;
        }
        PENDING.put(modId, factory);
    }

    /** External (addon) registration entry point used by {@link UpgraderAPI}. */
    public static synchronized void register(IntegrationProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (initialised) {
            activate(provider);
        } else {
            PENDING.put(provider.modId(), () -> provider);
        }
    }

    /** Instantiates pending suppliers whose mod is loaded and activates them safely. */
    public static synchronized void initAvailable() {
        if (initialised) {
            return;
        }
        for (Map.Entry<String, Supplier<IntegrationProvider>> e : PENDING.entrySet()) {
            String modId = e.getKey();
            if (!isLoadedSafely(modId)) {
                Upgrader.LOGGER.debug("Integration {} skipped: mod '{}' not present", e.getValue(), modId);
                continue;
            }
            try {
                activate(e.getValue().get());
            } catch (Throwable t) {
                Upgrader.LOGGER.error("Integration construction for '{}' failed; disabling it", modId, t);
            }
        }
        PENDING.clear();
        initialised = true;
        Upgrader.LOGGER.info("Integration init complete: {} active, {} failed", ACTIVE.size(), FAILED.size());
    }

    private static void activate(IntegrationProvider provider) {
        try {
            if (!provider.isAvailable()) {
                Upgrader.LOGGER.debug("Integration {} reports unavailable; skipping", provider.modId());
                return;
            }
            provider.init();
            ACTIVE.add(provider);
            provider.asValueSource().ifPresent(source -> {
                UpgraderAPI.registerValueSource(source);
                Upgrader.LOGGER.info("Integration {} contributed value source '{}'",
                        provider.modId(), source.name());
            });
            Upgrader.LOGGER.info("Integration {} enabled ({})", provider.modId(), provider.displayName());
        } catch (Throwable t) {
            FAILED.add(provider);
            Upgrader.LOGGER.error("Integration {} failed to initialise; Upgrader continues without it",
                    provider.modId(), t);
        }
    }

    private static boolean isLoadedSafely(String modId) {
        try {
            return ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static List<IntegrationProvider> active() {
        return List.copyOf(ACTIVE);
    }

    public static List<IntegrationProvider> failed() {
        return List.copyOf(FAILED);
    }

    /** Debug summary line for /upgrader debug. */
    public static String statusLine() {
        StringBuilder sb = new StringBuilder("integrations: ");
        if (ACTIVE.isEmpty() && FAILED.isEmpty()) {
            sb.append("none present");
        }
        ACTIVE.forEach(p -> sb.append(p.modId()).append("(ok) "));
        FAILED.forEach(p -> sb.append(p.modId()).append("(failed) "));
        return sb.toString().trim();
    }
}
