package com.example.upgradermod.registry;

import com.example.upgradermod.UpgraderConstants;
import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Menu (container) registry of the Upgrader mod.
 *
 * <p>The menu is created through {@link IForgeMenuType} so that the extra network buffer handed out
 * by Forge is available on the constructor.</p>
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, UpgraderConstants.MOD_ID);

    public static final RegistryObject<MenuType<UpgraderMenu>> UPGRADER_MENU =
            MENUS.register("upgrader_menu", () -> IForgeMenuType.create(UpgraderMenu::new));

    private ModMenus() {
        // Registry holder.
    }
}
