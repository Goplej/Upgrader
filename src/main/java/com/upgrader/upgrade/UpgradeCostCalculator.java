package com.upgrader.upgrade;

import com.upgrader.config.UpgraderConfig;
import com.upgrader.value.ItemValue;

/**
 * Resolves the effective {@link UpgradeCurve} from live config and prices the next level of a
 * concrete item using its computed value as the base.
 *
 * <p>Base selection: an already-snapshotted base value wins over a freshly computed one, so
 * mid-game recipe rebalances never retroactively change the price of gear players started
 * upgrading under the old economy.</p>
 */
public final class UpgradeCostCalculator {

    private UpgradeCostCalculator() {
    }

    /** Current curve built from the COMMON config section. Cheap to call per request. */
    public static UpgradeCurve currentCurve() {
        return new UpgradeCurve(UpgraderConfig.growthFactor(),
                UpgraderConfig.diminishingFactor(),
                UpgraderConfig.maxUpgradeLevel());
    }

    /** Next-level cost for an item at {@code level}, priced off its engine value. */
    public static long nextCost(ItemValue value, int level) {
        if (value == null || !value.isKnown()) {
            return -1L; // honest "cannot price an unknown item" signal for callers
        }
        return currentCurve().costOf(Math.max(1L, value.numericValue()), level);
    }

    /** Same but with an explicit base snapshot (capability path). */
    public static long nextCostFromBase(long baseValue, int level) {
        return currentCurve().costOf(baseValue, level);
    }
}
