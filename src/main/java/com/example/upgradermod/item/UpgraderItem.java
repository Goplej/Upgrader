package com.example.upgradermod.item;

import com.example.upgradermod.UpgraderConstants;
import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.stats.Stats;
import javax.annotation.Nullable;

import java.util.List;

/**
 * The Upgrader tool. Right clicking opens the {@link UpgraderMenu} container on the server, which
 * owns every piece of state (input slot, target slot, bet multiplier) used by a spin.
 */
public class UpgraderItem extends Item {

    /** Title shown in the container screen. */
    public static final Component TITLE = Component.translatable("container.upgradermod.upgrader");

    public UpgraderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, openingPlayer) -> new UpgraderMenu(containerId, inventory),
                    TITLE));
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.upgradermod.upgrader.tooltip.usage").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.upgradermod.upgrader.tooltip.author", UpgraderConstants.AUTHOR)
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
