package com.upgrader.value;

import com.upgrader.Upgrader;
import com.upgrader.config.OverrideEntry;
import com.upgrader.config.UpgraderConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies operator-defined value overrides from the {@code [overrides]} config sections.
 *
 * <p>Priority: exact item override &gt; tag override &gt; computed value. Both tables are parsed
 * lazily and re-parsed on {@link #reload()} (wired to {@code /upgrader reload} and config
 * reloads), so a typo in one line can never crash the game — it is dropped with a WARN.</p>
 */
public final class OverrideResolver {

    private volatile Map<ResourceLocation, Long> exact = Map.of();
    private volatile Map<ResourceLocation, Long> tags = Map.of();

    /** Re-parses both config lists into immutable lookup maps. */
    public void reload() {
        Map<ResourceLocation, Long> newExact = new HashMap<>();
        Map<ResourceLocation, Long> newTags = new HashMap<>();
        for (String line : UpgraderConfig.overridesExact()) {
            OverrideEntry.parse(line).ifPresentOrElse(
                    e -> {
                        if (e.target() instanceof OverrideEntry.Target.Item item) {
                            newExact.put(item.id(), e.value());
                        }
                    },
                    () -> Upgrader.LOGGER.warn("Ignoring malformed override entry '{}'", line));
        }
        for (String line : UpgraderConfig.overridesTags()) {
            OverrideEntry.parse(line).ifPresentOrElse(
                    e -> {
                        if (e.target() instanceof OverrideEntry.Target.Tag tag) {
                            newTags.put(tag.id(), e.value());
                        }
                    },
                    () -> Upgrader.LOGGER.warn("Ignoring malformed tag override entry '{}'", line));
        }
        this.exact = Map.copyOf(newExact);
        this.tags = Map.copyOf(newTags);
        if (!this.exact.isEmpty() || !this.tags.isEmpty()) {
            Upgrader.LOGGER.warn("{} active value overrides configured - the engine normally computes values itself",
                    this.exact.size() + this.tags.size());
        }
    }

    /** Wraps {@code computed} with an override when one matches; otherwise returns it unchanged. */
    public ItemValue resolve(ItemStack stack, ResourceLocation id, ItemValue computed) {
        Long forced = exact.get(id);
        String via = "override:exact:" + id;
        if (forced == null) {
            for (Map.Entry<ResourceLocation, Long> e : tags.entrySet()) {
                try {
                    if (stack.is(net.minecraft.tags.ItemTags.create(e.getKey()))) {
                        forced = e.getValue();
                        via = "override:tag:" + e.getKey();
                        break;
                    }
                } catch (Throwable t) {
                    Upgrader.LOGGER.debug("Tag lookup failed for {}: {}", e.getKey(), t.toString());
                }
            }
        }
        if (forced == null) {
            return computed;
        }
        List<ValueContribution> breakdown = new ArrayList<>(computed.breakdown());
        breakdown.add(new ValueContribution(via, SourceType.OVERRIDE, forced,
                "manual config override (wins over computed value)", 1.0));
        return new ItemValue(id, forced, ValueTier.fromValue(forced), Confidence.HIGH,
                List.copyOf(breakdown), via, Instant.now(),
                computed.cycleDetected(), computed.depthReached(), computed.nodesProcessed());
    }

    public int configuredCount() {
        return exact.size() + tags.size();
    }
}
