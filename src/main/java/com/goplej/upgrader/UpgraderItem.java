package com.goplej.upgrader;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The item that opens the upgrade menu on right click.
 */
public class UpgraderItem extends Item {

    public UpgraderItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            user.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, player) -> new UpgraderScreenHandler(syncId, inventory),
                    Text.translatable("item.upgrader.upgrader")));
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.upgrader.upgrader.tooltip").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.upgrader.upgrader.tooltip2").formatted(Formatting.DARK_GRAY));
    }
}
