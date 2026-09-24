package com.upgrader.util;

import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

/**
 * Lenient {@link ResourceLocation} parsing that never throws on malformed input.
 */
public final class ResourceLocationUtil {

    /** Namespace used when a bare path (no colon) is supplied. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    private ResourceLocationUtil() {
    }

    /**
     * Parses {@code id} leniently: trims whitespace, defaults the namespace to
     * {@code minecraft}, and returns {@code null} for structurally invalid strings instead of
     * throwing (configs and datapacks authored by humans must never crash the game).
     */
    public static ResourceLocation parseOrNull(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        String trimmed = id.trim();
        if (!trimmed.contains(":")) {
            trimmed = DEFAULT_NAMESPACE + ":" + trimmed;
        }
        return ResourceLocation.tryParse(trimmed);
    }

    /** Safe string form of any object; {@code null}-tolerant. */
    public static String safeId(ResourceLocation id) {
        return id == null ? "<unknown>" : id.toString();
    }
}
