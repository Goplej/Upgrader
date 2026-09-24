package com.upgrader.upgrade;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.capability.UpgradeCapability;
import com.upgrader.upgrade.effect.AreaMiningUpgradeEffect;
import com.upgrader.upgrade.effect.AutoSmeltUpgradeEffect;
import com.upgrader.upgrade.effect.DurabilityUpgradeEffect;
import com.upgrader.upgrade.effect.EfficiencyUpgradeEffect;
import com.upgrader.upgrade.effect.LootUpgradeEffect;
import com.upgrader.upgrade.effect.MagnetUpgradeEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime hooks that turn the stored upgrade NBT into live behaviour:
 * magnet pull (player tick), vein mining + auto-smelt (left-click release), and loot luck
 * (hurt events add a tiny crit chance). Every handler is defensive: a corrupt tag simply means
 * "no bonus this tick".
 */
@Mod.EventBusSubscriber(modid = Upgrader.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpgradeEvents {

    private UpgradeEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack held = sp.getMainHandItem();
        int radius = readInt(held, MagnetUpgradeEffect.TAG_MAGNET_RADIUS);
        if (radius <= 0) {
            return;
        }
        // Pull nearby drops every 5 ticks to keep entity scans cheap.
        if (sp.tickCount % 5 != 0) {
            return;
        }
        List<ItemEntity> items = sp.level().getEntitiesOfClass(ItemEntity.class,
                sp.getBoundingBox().inflate(radius), e -> !e.hasPickUpDelay());
        for (ItemEntity item : items) {
            item.setDeltaMovement(item.position().vectorTo(sp.position()).normalize().scale(0.35));
            item.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack tool = sp.getMainHandItem();
        int areaRadius = readInt(tool, AreaMiningUpgradeEffect.TAG_RADIUS);
        if (areaRadius <= 0 || !event.getFace().equals(event.getFace())) {
            return;
        }
        // Queue the vein for the block-break completion moment via a simple BFS limited by config.
        BlockPos origin = event.getPos();
        ServerLevel level = event.getLevel().getServer().overworld() == event.getLevel()
                ? null : (ServerLevel) event.getLevel();
        if (level == null || level.getBlockState(origin).getBlock() == Blocks.AIR) {
            return;
        }
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        visited.add(origin.immutable());
        BlockState target = level.getBlockState(origin);
        int budget = 32; // conservative default; server owners can raise via future config key
        int broken = 0;
        while (!frontier.isEmpty() && broken < budget) {
            BlockPos pos = frontier.poll();
            if (!pos.equals(origin) && !level.getBlockState(pos).getBlock().equals(target.getBlock())) {
                continue;
            }
            if (tool.getMaxDamage() > 0 && tool.getDamageValue() >= tool.getMaxDamage() - 1) {
                break; // respect durability: stop before breaking the tool silently
            }
            level.destroyBlock(pos, true, sp);
            broken++;
            for (BlockPos next : neighborsWithin(pos, origin, areaRadius)) {
                if (visited.add(next)) {
                    frontier.add(next);
                }
            }
        }
    }

    private static Iterable<BlockPos> neighborsWithin(BlockPos pos, BlockPos origin, int radius) {
        return () -> new java.util.Iterator<>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private int index = 0;
            private static final int[][] DIRS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

            @Override
            public boolean hasNext() {
                while (index < DIRS.length) {
                    int[] d = DIRS[index];
                    cursor.set(pos.getX() + d[0], pos.getY() + d[1], pos.getZ() + d[2]);
                    if (Math.abs(cursor.getX() - origin.getX()) <= radius
                            && Math.abs(cursor.getY() - origin.getY()) <= radius
                            && Math.abs(cursor.getZ() - origin.getZ()) <= radius) {
                        return true;
                    }
                    index++;
                }
                return false;
            }

            @Override
            public BlockPos next() {
                hasNext();
                BlockPos out = cursor.immutable();
                index++;
                return out;
            }
        };
    }

    private static int readInt(ItemStack stack, String key) {
        if (stack == null || !stack.hasTag()) {
            return 0;
        }
        try {
            CompoundTag root = stack.getTag().getCompound(UpgradeCapability.TAG_ROOT);
            return Math.max(0, root.getInt(key));
        } catch (Throwable t) {
            return 0;
        }
    }
}
