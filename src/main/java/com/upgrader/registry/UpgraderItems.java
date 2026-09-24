package com.upgrader.registry;

import com.upgrader.Upgrader;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * DeferredRegister for the few items Upgrader itself owns. The engine is item-agnostic, so the
 * roster stays intentionally small: materials that anchor the economy and the upgrade core used
 * by the GUI workflow.
 */
public final class UpgraderItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Upgrader.MODID);

    /** The unstable value-crystal used as upgrade catalyst in the GUI. */
    public static final RegistryObject<Item> UPGRADE_CORE = ITEMS.register("upgrade_core",
            () -> new Item(new Item.Properties().stacksTo(16)));

    /** Reference material with a strong tag identity (circuit family) for pricing demos/tests. */
    public static final RegistryObject<Item> VALUE_ANCHOR = ITEMS.register("value_anchor",
            () -> new Item(new Item.Properties()));

    private UpgraderItems() {
    }

    public static void register(final IEventBus modBus) {
        ITEMS.register(modBus);
        Upgrader.LOGGER.debug("Item deferred register attached ({})", ITEMS.getEntries().size());
    }
}
