package com.example.upgradermod.logic.providers;

import com.example.upgradermod.config.UpgraderConfig;
import com.example.upgradermod.logic.ValueContext;
import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Priority 500 &ndash; tag based pricing from {@code config/upgradermod/tags.json}.
 *
 * <p>Every tag the item belongs to is looked up in the config table and the <b>maximum</b> of the
 * configured values wins.</p>
 */
public class TagValueProvider implements ValueProvider {

    /** Execution priority. */
    public static final int PRIORITY = 500;

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getName() {
        return "tag";
    }

    @Override
    public long getValue(ItemStack stack, ValueContext context) {
        try {
            if (stack == null || stack.isEmpty()) {
                return UNKNOWN;
            }

            long best = UNKNOWN;
            for (TagKey<Item> tag : stack.getTags().toList()) {
                long configured = UpgraderConfig.getTagValue(tag.location());
                if (configured > 0L && configured > best) {
                    best = configured;
                }
            }
            return best;
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader could not resolve the tags of a stack", throwable);
            return UNKNOWN;
        }
    }
}
