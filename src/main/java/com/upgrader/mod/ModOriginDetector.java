package com.upgrader.mod;

import com.upgrader.Upgrader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Pipeline node 2: figures out which mod owns an item id and what we know about that mod.
 *
 * <p>Used by breakdown explanations ("from Mekanism") and by integration dispatch. Every lookup
 * is guarded: an absent ModList (unit tests) degrades to "minecraft"/"unknown" rather than
 * throwing.</p>
 */
public final class ModOriginDetector {

    public static final String VANILLA_NAMESPACE = "minecraft";

    private ModOriginDetector() {
    }

    /** Namespace (= mod id) owning the given item id. */
    public static String namespaceOf(ResourceLocation id) {
        return id == null ? VANILLA_NAMESPACE : id.getNamespace();
    }

    /** True when the item belongs to vanilla Minecraft. */
    public static boolean isVanilla(ResourceLocation id) {
        return VANILLA_NAMESPACE.equals(namespaceOf(id));
    }

    /** Display name of the owning mod, falling back to the namespace itself. */
    public static String displayNameOf(ResourceLocation id) {
        String ns = namespaceOf(id);
        try {
            return ModList.get().getModContainerById(ns)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(ns);
        } catch (Throwable t) {
            return ns; // ModList unavailable (bootstrap/tests)
        }
    }

    /** Version string of the owning mod, or "?" when unknown. */
    public static String versionOf(ResourceLocation id) {
        String ns = namespaceOf(id);
        try {
            return ModList.get().getModContainerById(ns)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("?");
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Mod origin lookup failed for {}", ns, t);
            return "?";
        }
    }

    /** Whether any mod with this id is present at runtime. */
    public static boolean isModLoaded(String modId) {
        try {
            return ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }
}
