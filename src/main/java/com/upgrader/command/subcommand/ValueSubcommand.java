package com.upgrader.command.subcommand;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.upgrader.api.UpgraderAPI;
import com.upgrader.util.TextFormatUtil;
import com.upgrader.value.ItemValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** {@code /upgrader value <item>} — engine verdict + breakdown for any registered item. */
public final class ValueSubcommand {

    private ValueSubcommand() {
    }

    /** Attaches onto the root node built by {@link com.upgrader.command.UpgraderCommand}. */
    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = dispatcher.getRoot().getChild("upgrader");
        if (root == null) {
            return; // root not registered yet — UpgraderCommand owns registration order
        }
        root.then(Commands.literal("value")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .executes(ValueSubcommand::run)));
    }

    private static int run(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "item");
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            src.sendFailure(Component.literal("Unknown item: " + id));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        ItemValue value = UpgraderAPI.getItemValue(stack);
        src.sendSuccess(() -> Component.literal(TextFormatUtil.formatValue(value.numericValue())
                + "  [" + value.tier() + ", conf="
                + String.format(java.util.Locale.ROOT, "%.2f", value.confidence().weight()) + "]"), false);
        value.breakdown().forEach(row ->
                src.sendSuccess(() -> Component.literal("   · " + row.explanation()), false));
        if (!value.isKnown()) {
            src.sendSuccess(() -> Component.literal("   (unknown: no trustworthy evidence)"), false);
        }
        return 1;
    }
}
