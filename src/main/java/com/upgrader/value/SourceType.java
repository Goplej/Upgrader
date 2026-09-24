package com.upgrader.value;

/**
 * Categories of value evidence, used both for anti-double-counting bookkeeping inside
 * {@link ValueContext} and for grouping rows in the breakdown GUI.
 */
public enum SourceType {
    /** Third-party mod integration output (ProjectE EMC, Avaritia materials). */
    INTEGRATION,
    /** Full crafting/furnace recipe cost analysis. */
    RECIPE,
    /** Sum over recipe components when the recipe itself is only partially known. */
    COMPONENT,
    /** Tag membership and material-tier inference. */
    TAG,
    /** Intrinsic item properties: durability, food, armor, enchantability. */
    PROPERTY,
    /** NBT complexity modifier (enchantments, custom data). */
    NBT,
    /** Last-resort heuristics: rarity, stack size, fire resistance. */
    HEURISTIC,
    /** Manual operator override from config. */
    OVERRIDE
}
