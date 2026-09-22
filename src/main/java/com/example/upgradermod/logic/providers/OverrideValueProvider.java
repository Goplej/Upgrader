package com.example.upgradermod.logic.providers;

import com.example.upgradermod.config.UpgraderConfig;
import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueContext;
import com.example.upgradermod.logic.ValueProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Priority 1000 &ndash; hard overrides from {@code config/upgradermod/overrides.json}.
 *
 * <p>This provider always wins when the item is listed in the file, which makes it the escape hatch
 * for pack authors.</p>
 */
public class OverrideValueProvider implements ValueProvider {

    /** Execution priority. */
    public static final int PRIORITY = 1000;

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getName() {
        return "override";
    }

    @Override
    public long getValue(ItemStack stack, ValueContext context) {
        try {
            if (stack == null || stack.isEmpty()) {
                return UNKNOWN;
            }

            ResourceLocation id = ItemRegistryCache.id(stack.getItem());
            if (id == null) {
                return UNKNOWN;
            }

            long configured = UpgraderConfig.getOverride(id);
            return configured > 0L ? configured : UNKNOWN;
        } catch (Throwable throwable) {
            return UNKNOWN;
        }
    }
}
