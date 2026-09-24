package com.upgrader.command;

import com.mojang.brigadier.CommandDispatcher;
import com.upgrader.api.UpgraderAPI;
import com.upgrader.command.subcommand.InfoSubcommand;
import com.upgrader.command.subcommand.ValueSubcommand;
import com.upgrader.network.ValueSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Root {@code /upgrader} command (op level 2). Subcommands live in their own classes under
 * {@code command.subcommand} so each stays single-responsibility and independently testable.
 */
@Mod.EventBusSubscriber(modid = "upgrader", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpgraderCommand {

    private UpgraderCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Builds the whole tree; also called directly by ValueEngineTest to avoid a real server. */
    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("upgrader")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            UpgraderAPI.reloadRuntime();
                            CompoundTag sync = new CompoundTag();
                            sync.putLong("reloaded_at", System.currentTimeMillis());
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("command.upgrader.reloaded"), false);
                            net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                                    .getPlayerList().getPlayers().forEach(p ->
                                            com.upgrader.network.UpgraderPayloadHandler.INSTANCE.send(
                                                    PacketDistributor.PLAYER.with(() -> p),
                                                    new ValueSyncPacket(sync)));
                            return 1;
                        }))
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "engine=" + UpgraderAPI.getValueEngine()
                                    + " overrides=" + UpgraderAPI.getOverrideResolver().configuredCount()), false);
                            return 1;
                        })));
        // Subcommand classes attach themselves onto the already-registered root node.
        InfoSubcommand.register(dispatcher);
        ValueSubcommand.register(dispatcher);
    }
}
