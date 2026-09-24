package com.upgrader.upgrade.effect;

/**
 * Per-transaction knobs resolved from config by the upgrade engine before effects run.
 *
 * @param growthFactor     cost curve base from {@code UpgraderConfig}
 * @param blocksPerTickLimit hard cap for area-mining style effects (server protection)
 * @param areaMiningEnabled  master switch for block-breaking effects
 */
public record UpgradeContext(double growthFactor, int blocksPerTickLimit, boolean areaMiningEnabled) {

    public UpgradeContext {
        if (blocksPerTickLimit < 1) {
            blocksPerTickLimit = 1;
        }
    }
}
