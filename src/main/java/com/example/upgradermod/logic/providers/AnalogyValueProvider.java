package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.logic.ValueContext;
import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;


/**
 * Priority 300 &ndash; id suffix analogy from a modded item to a vanilla item.
 *
 * <p>A modded item often is a re-textured or re-balanced copy of a vanilla one
 * ({@code thermal:copper_ingot} &rarr; {@code minecraft:copper_ingot},
 * {@code create:crushed_raw_iron} &rarr; {@code minecraft:raw_iron}). Two matches are tried:</p>
 * <ol>
 *     <li>the identical registry path inside the {@code minecraft} namespace;</li>
 *     <li>the longest vanilla path that the modded path ends with
 *     ({@code modded endsWith "_" + vanilla}).</li>
 * </ol>
 *
 * <p>The analogue is priced with this provider disabled, so the lookup can never bounce back into
 * itself.</p>
 */
public class AnalogyValueProvider implements ValueProvider {

    /** Execution priority. */
    public static final int PRIORITY = 300;

    /** Namespace of the vanilla items. */
    public static final String VANILLA_NAMESPACE = "minecraft";

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getName() {
        return "analogy";
    }

    @Override
    public long getValue(ItemStack stack, ValueContext context) {
        try {
            if (stack == null || stack.isEmpty()) {
                return UNKNOWN;
            }

            ResourceLocation id = ItemRegistryCache.id(stack.getItem());
            if (id == null || VANILLA_NAMESPACE.equals(id.getNamespace())) {
                // Vanilla items have nothing to be compared against.
                return UNKNOWN;
            }

            Item analogue = findVanillaAnalogue(id.getPath());
            if (analogue == null || analogue == stack.getItem()) {
                return UNKNOWN;
            }
            if (context.isVisited(analogue)) {
                return UNKNOWN;
            }

            ItemStack analogueStack = new ItemStack(analogue);
            ValueContext analogueContext = context.descend(analogue).disabling(getName());
            return ValueCalculator.calculate(analogueStack, analogueContext);
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader analogy lookup failed", throwable);
            return UNKNOWN;
        }
    }

    /**
     * Finds the vanilla item a modded path most likely refers to.
     *
     * @param path registry path of the modded item
     * @return the vanilla item, or {@code null} when nothing matches
     */
    private Item findVanillaAnalogue(String path) {
        Item exact = ItemRegistryCache.byId(ResourceLocation.fromNamespaceAndPath(VANILLA_NAMESPACE, path));
        if (exact != null) {
            return exact;
        }

        // Longest vanilla path that is a "_" separated suffix of the modded path.
        Item best = null;
        int bestLength = -1;
        for (Item candidate : ItemRegistryCache.allItems()) {
            ResourceLocation candidateId = ItemRegistryCache.id(candidate);
            if (candidateId == null || !VANILLA_NAMESPACE.equals(candidateId.getNamespace())) {
                continue;
            }
            String candidatePath = candidateId.getPath();
            if (candidatePath.length() <= bestLength || candidatePath.length() >= path.length()) {
                continue;
            }
            if (path.endsWith("_" + candidatePath)) {
                best = candidate;
                bestLength = candidatePath.length();
            }
        }
        return best;
    }
}
