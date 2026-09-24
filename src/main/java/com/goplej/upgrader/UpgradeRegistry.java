package com.goplej.upgrader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;

/**
 * Data-driven-ish registry of upgrade lines.
 *
 * <p>A line is a ladder of items (e.g. wooden sword &rarr; stone &rarr; iron &rarr; diamond &rarr; netherite).
 * Every step of the ladder has a material cost, taken from the player's inventory.
 * A player may jump any number of tiers at once: the cost is accumulated for every skipped step.
 */
public final class UpgradeRegistry {
    private UpgradeRegistry() {
    }

    /**
     * @param count         how many of the step material one step costs
     * @param items         the ladder from lowest to highest tier
     * @param stepMaterials material for the step into the tier at index i + 1
     */
    public record Line(int count, List<Item> items, List<Item> stepMaterials) {
    }

    /** NBT keys copied from the old item to the upgraded one. */
    private static final String[] PRESERVED_KEYS = {
            "Enchantments", "display", "Unbreakable", "CustomModelData", "AttributeModifiers", "Trim"
    };

    private static final Map<Item, Line> BY_ITEM = new HashMap<>();

    static {
        List<Item> toolMaterials = List.of(Items.COBBLESTONE, Items.IRON_INGOT, Items.DIAMOND, Items.NETHERITE_INGOT);
        List<Item> armorMaterials = List.of(Items.IRON_INGOT, Items.IRON_INGOT, Items.DIAMOND, Items.NETHERITE_INGOT);

        // Tools — material counts match the vanilla crafting recipes.
        line(2, toolMaterials, Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
        line(3, toolMaterials, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
        line(3, toolMaterials, Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
        line(1, toolMaterials, Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL);
        line(2, toolMaterials, Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);

        // Armor — leather -> chainmail -> iron -> diamond -> netherite.
        line(5, armorMaterials, Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET);
        line(8, armorMaterials, Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE);
        line(7, armorMaterials, Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS);
        line(4, armorMaterials, Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS);
    }

    private static void line(int count, List<Item> stepMaterials, Item... items) {
        Line line = new Line(count, List.of(items), List.copyOf(stepMaterials));
        for (Item item : items) {
            BY_ITEM.put(item, line);
        }
    }

    /** Whether the item can be put into the upgrade slot at all. */
    public static boolean isUpgradable(Item item) {
        Line line = BY_ITEM.get(item);
        return line != null && line.items().indexOf(item) < line.items().size() - 1;
    }

    /** All upgrade options for the given item, from the next tier up to the top one. */
    public static List<UpgradeOption> getOptions(ItemStack stack) {
        return stack.isEmpty() ? List.of() : getOptions(stack.getItem());
    }

    public static List<UpgradeOption> getOptions(Item item) {
        Line line = BY_ITEM.get(item);
        if (line == null) {
            return List.of();
        }
        int from = line.items().indexOf(item);
        List<UpgradeOption> options = new ArrayList<>();
        for (int to = from + 1; to < line.items().size(); to++) {
            Map<Item, Integer> costs = new LinkedHashMap<>();
            for (int step = from; step < to; step++) {
                costs.merge(line.stepMaterials().get(step), line.count(), Integer::sum);
            }
            List<UpgradeOption.Cost> list = new ArrayList<>();
            costs.forEach((mat, amount) -> list.add(new UpgradeOption.Cost(mat, amount)));
            options.add(new UpgradeOption(line.items().get(to), List.copyOf(list)));
        }
        return options;
    }

    /**
     * Builds the upgraded item, keeping enchantments, custom name, trim etc.
     * The result is fully repaired.
     */
    public static ItemStack upgrade(ItemStack input, Item target) {
        ItemStack result = new ItemStack(target);
        NbtCompound sourceNbt = input.getNbt();
        if (sourceNbt != null) {
            NbtCompound resultNbt = result.getOrCreateNbt();
            for (String key : PRESERVED_KEYS) {
                if (sourceNbt.contains(key)) {
                    resultNbt.put(key, sourceNbt.get(key).copy());
                }
            }
        }
        return result;
    }
}
