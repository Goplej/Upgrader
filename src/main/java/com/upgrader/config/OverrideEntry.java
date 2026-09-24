package com.upgrader.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import com.upgrader.util.ResourceLocationUtil;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One parsed line of the {@code overrides} config sections.
 *
 * <p>Grammar: {@code namespace:path=NUMBER} for exact items, {@code tag:namespace:path=NUMBER}
 * for tag-wide overrides. Malformed lines are dropped with a WARN rather than crashing.</p>
 *
 * @param target what the override applies to
 * @param value  the forced numeric value (always &ge; 0)
 */
public record OverrideEntry(Target target, long value) {

    /** Loose validation pattern shared by both config lists. */
    public static final Pattern STATIC_PATTERN =
            Pattern.compile("(?:tag:)?[a-z0-9_.-]+:[a-z0-9/._-]+=\\d+");

    /** Discriminated union of the two override kinds. */
    public sealed interface Target {
        /** An exact item id. */
        record Item(ResourceLocation id) implements Target {
        }

        /** A whole item tag. */
        record Tag(ResourceLocation id) implements Target {
        }
    }

    /** Parses one config string; empty when malformed. */
    public static Optional<OverrideEntry> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        int eq = raw.lastIndexOf('=');
        if (eq <= 0 || eq == raw.length() - 1) {
            return Optional.empty();
        }
        String idPart = raw.substring(0, eq).trim();
        long v;
        try {
            v = Long.parseLong(raw.substring(eq + 1).trim());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (v < 0) {
            return Optional.empty();
        }
        if (idPart.startsWith("tag:")) {
            ResourceLocation rl = ResourceLocationUtil.parseOrNull(idPart.substring(4));
            return rl == null ? Optional.empty() : Optional.of(new OverrideEntry(new Target.Tag(rl), v));
        }
        ResourceLocation rl = ResourceLocationUtil.parseOrNull(idPart);
        return rl == null ? Optional.empty() : Optional.of(new OverrideEntry(new Target.Item(rl), v));
    }

    /** Config validator predicate used by {@link ForgeConfigSpec} list definitions. */
    public static boolean isValid(Object o) {
        return o instanceof String s && parse(s).isPresent();
    }
}
