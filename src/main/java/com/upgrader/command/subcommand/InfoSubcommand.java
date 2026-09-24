package com.upgrader.command.subcommand;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.upgrader.api.UpgraderAPI;
import com.upgrader.integration.IntegrationManager;
import com.upgrader.value.ValueEngine;
import com.upgrader.value.ValueSourceRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /upgrader info} — pipeline health snapshot for admins. */
public final class InfoSubcommand {

    private InfoSubcommand() {
    }

    /** Attaches onto the root node built by {@link com.upgrader.command.UpgraderCommand}. */
    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = dispatcher.getRoot().getChild("upgrader");
        if (root == null) {
            return;
        }
        root.then(Commands.literal("info").executes(InfoSubcommand::run));
    }

    private static int run(final CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ValueEngine engine = UpgraderAPI.getValueEngine();
        src.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "api v%d | sources=%d | overrides=%d | %s",
                UpgraderAPI.API_VERSION, ValueSourceRegistry.get().size(),
                UpgraderAPI.getOverrideResolver().configuredCount(),
                engine.cache().statsLine())), false);
        src.sendSuccess(() -> Component.literal(IntegrationManager.statusLine()), false);
        return 1;
    }
}
