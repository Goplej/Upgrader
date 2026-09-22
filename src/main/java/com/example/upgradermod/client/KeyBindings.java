package com.example.upgradermod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings of the Upgrader mod.
 *
 * <p>Both bindings use {@link KeyConflictContext#GUI} so they only fire while a screen is open and
 * never clash with in-game movement keys.</p>
 */
public final class KeyBindings {

    /** Translation key of the binding category. */
    public static final String CATEGORY = "key.categories.upgradermod";

    /** Triggers a spin while the Upgrader screen is open. */
    public static final KeyMapping SPIN = new KeyMapping(
            "key.upgradermod.spin",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    /** Opens the item catalogue while the Upgrader screen is open. */
    public static final KeyMapping OPEN_CATALOG = new KeyMapping(
            "key.upgradermod.catalog",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    private KeyBindings() {
        // Static access only.
    }
}
