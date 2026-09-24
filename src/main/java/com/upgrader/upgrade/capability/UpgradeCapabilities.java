package com.upgrader.upgrade.capability;

import com.upgrader.Upgrader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Capability token holder + registration entry point.
 *
 * <p>Forge 1.20 auto-registers capabilities discovered via {@code @AutoRegister}, but we keep an
 * explicit {@link #init()} so the token creation happens inside a controlled lifecycle hook and
 * the id is stable for addons referencing it.</p>
 */
public final class UpgradeCapabilities {

    public static final ResourceLocation UPGRADEABLE_ID =
            new ResourceLocation(Upgrader.MODID, "upgradeable_item");

    /** The public token addons can query stacks with. */
    public static final Capability<IUpgradeableItem> UPGRADEABLE =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private UpgradeCapabilities() {
    }

    /** No-op today; kept as an explicit hook for future storage/defaults wiring. */
    public static void init() {
        Upgrader.LOGGER.debug("Upgrade capability token ready: {}", UPGRADEABLE_ID);
    }
}
