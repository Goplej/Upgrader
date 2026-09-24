package com.upgrader.cache;

import com.upgrader.Upgrader;
import com.upgrader.api.UpgraderAPI;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;

/**
 * Forge-bus hooks that keep {@link ValueCache} honest: any world whose recipes or tags changed
 * invalidates every cached value (values are cheap to recompute, wrong cached values are not).
 */
public final class CacheInvalidationListener {

    private CacheInvalidationListener() {
    }

    /** Fired by Forge right before the recipe map is swapped in. */
    @SubscribeEvent
    public static void onRecipesUpdated(final RecipesUpdatedEvent event) {
        UpgraderAPI.getValueEngine().invalidateAll();
        Upgrader.LOGGER.debug("Cache invalidated: recipes updated");
    }

    /** Tag re-loads change material inference just as much as recipe changes. */
    @SubscribeEvent
    public static void onTagsUpdated(final TagsUpdatedEvent event) {
        UpgraderAPI.getValueEngine().invalidateAll();
        Upgrader.LOGGER.debug("Cache invalidated: tags updated ({})", event.getUpdateCause());
    }
}
