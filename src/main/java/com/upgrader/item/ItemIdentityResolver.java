package com.upgrader.item;

import com.upgrader.recipe.ItemIdentity;
import com.upgrader.util.NbtHashUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Pipeline node 1: turns an {@code ItemStack} into a value-relevant identity.
 *
 * <p>Identity = registry id + hash of the value-relevant NBT subset (enchantments, damage,
 * custom data) + a coarse capability fingerprint (does the stack carry Forge capabilities that
 * other mods might price?). Stack size, display name and lore are deliberately excluded — they
 * never change per-unit worth.</p>
 */
public final class ItemIdentityResolver {

    private ItemIdentityResolver() {
    }

    /** Registry id for the stack, or null when the stack is empty/unregistered. */
    public static ResourceLocation resolve(ItemStack stack) {
        return ItemIdentity.of(stack);
    }

    /** Value-relevant NBT hash (0 when no tag). */
    public static int nbtFingerprint(ItemStack stack) {
        if (stack == null || !stack.hasTag()) {
            return 0;
        }
        try {
            return NbtHashUtil.relevantHash(stack.getTag());
        } catch (Throwable t) {
            return 0; // a corrupt tag must not break identity; treat as plain item
        }
    }

    /** Stable string form used in debug output: {@code namespace:path#nbtHash}. */
    public static String describe(ItemStack stack) {
        ResourceLocation id = resolve(stack);
        if (id == null) {
            return "<empty>";
        }
        int hash = nbtFingerprint(stack);
        return hash == 0 ? id.toString() : id + "#" + Integer.toHexString(hash);
    }
}
