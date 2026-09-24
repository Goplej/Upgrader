package com.goplej.upgrader;

import java.util.List;

import net.minecraft.item.Item;

/**
 * One selectable upgrade: a target item and the list of materials required for it.
 */
public record UpgradeOption(Item target, List<Cost> costs) {

    public record Cost(Item item, int count) {
    }
}
