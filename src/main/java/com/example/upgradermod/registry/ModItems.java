package com.example.upgradermod.registry;

import com.example.upgradermod.UpgraderConstants;
import com.example.upgradermod.item.UpgraderItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Item registry of the Upgrader mod.
 *
 * <p>Uses {@link DeferredRegister} as required by the API modernity constraints.</p>
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, UpgraderConstants.MOD_ID);

    /** The Upgrader itself: a single, unstackable, rare tool that opens the upgrade screen. */
    public static final RegistryObject<Item> UPGRADER = ITEMS.register("upgrader",
            () -> new UpgraderItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)
                    .fireResistant()));

    private ModItems() {
        // Registry holder.
    }
}
