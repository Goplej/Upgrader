package com.upgrader.upgrade;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.effect.AreaMiningUpgradeEffect;
import com.upgrader.upgrade.effect.AutoSmeltUpgradeEffect;
import com.upgrader.upgrade.effect.DamageUpgradeEffect;
import com.upgrader.upgrade.effect.DurabilityUpgradeEffect;
import com.upgrader.upgrade.effect.EfficiencyUpgradeEffect;
import com.upgrader.upgrade.effect.LootUpgradeEffect;
import com.upgrader.upgrade.effect.MagnetUpgradeEffect;
import com.upgrader.upgrade.effect.UpgradeEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all {@link UpgradeEffect}s the engine applies on level-up (Registry pattern).
 *
 * <p>Built-ins register during common setup; addons can add their own through
 * {@code UpgraderAPI.registerUpgradeEffect}. Duplicate ids replace with a WARN, mirroring the
 * value-source registry semantics.</p>
 */
public final class UpgradeEffectRegistry {

    private static final Map<ResourceLocation, UpgradeEffect> EFFECTS = new ConcurrentHashMap<>();

    private UpgradeEffectRegistry() {
    }

    /** Registers the seven effects shipped with Upgrader. Idempotent. */
    public static synchronized void registerBuiltins() {
        if (!EFFECTS.isEmpty()) {
            return;
        }
        register(new DamageUpgradeEffect());
        register(new DurabilityUpgradeEffect());
        register(new EfficiencyUpgradeEffect());
        register(new AreaMiningUpgradeEffect());
        register(new AutoSmeltUpgradeEffect());
        register(new MagnetUpgradeEffect());
        register(new LootUpgradeEffect());
        Upgrader.LOGGER.info("Upgrade effects registered: {}", EFFECTS.size());
    }

    public static void register(UpgradeEffect effect) {
        if (effect == null || effect.getId() == null) {
            throw new IllegalArgumentException("effect and its id must not be null");
        }
        UpgradeEffect previous = EFFECTS.put(effect.getId(), effect);
        if (previous != null) {
            Upgrader.LOGGER.warn("Upgrade effect {} re-registered; replacing {}", effect.getId(),
                    previous.getClass().getName());
        }
    }

    public static UpgradeEffect get(ResourceLocation id) {
        return EFFECTS.get(id);
    }

    public static Collection<UpgradeEffect> all() {
        return List.copyOf(EFFECTS.values());
    }

    public static int size() {
        return EFFECTS.size();
    }
}
