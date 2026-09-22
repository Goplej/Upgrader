package com.example.upgradermod.logic;

import com.example.upgradermod.UpgraderConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Audit trail for condition <b>C6</b> of the pre-spin check (Section 2).
 *
 * <p>Whenever the value of the consumed item exceeds
 * {@link UpgraderConstants#LOG_THRESHOLD}, one line is appended to
 * {@code logs/upgradermod_suspicious.log} inside the game directory. The same event is mirrored to
 * the normal mod log at {@code WARN} level.</p>
 */
public final class SuspiciousLogger {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Appends one audit line.
     *
     * @param player      the player that tried to spin, may be {@code null}
     * @param input       the consumed stack
     * @param target      the requested stack
     * @param inputValue  value of the input stack
     * @param targetValue value of the target stack
     */
    public static void log(@Nullable Player player, ItemStack input, ItemStack target, long inputValue, long targetValue) {
        String playerName = "<unknown>";
        try {
            if (player != null) {
                playerName = player.getGameProfile().getName();
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader could not resolve the player name for the suspicious log", throwable);
        }

        String line = String.format(Locale.ROOT,
                "%s | player=%s | input=%s x%d | target=%s x%d | inputValue=%d | targetValue=%d | threshold=%d%n",
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                playerName,
                describe(input), input == null ? 0 : input.getCount(),
                describe(target), target == null ? 0 : target.getCount(),
                inputValue, targetValue, UpgraderConstants.LOG_THRESHOLD);

        try {
            Path file = FMLPaths.GAMEDIR.get().resolve(UpgraderConstants.SUSPICIOUS_LOG_FILE);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not write to {}, the suspicious spin is only logged to the console",
                    UpgraderConstants.SUSPICIOUS_LOG_FILE, throwable);
        }

        LOGGER.warn("Suspicious Upgrader spin: {} tried to convert {} (value {}) into {}",
                playerName, describe(input), inputValue, describe(target));
    }

    private static String describe(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "<empty>";
        }
        ResourceLocation id = ItemRegistryCache.id(stack.getItem());
        return id == null ? "<unregistered>" : id.toString();
    }

    private SuspiciousLogger() {
        // Static access only.
    }
}
