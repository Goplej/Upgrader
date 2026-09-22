package com.example.upgradermod.logic;

import com.example.upgradermod.UpgraderConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * PRE_SPIN_CHECK (Section 2).
 *
 * <p>The spin is cancelled when <b>any</b> of the following is true:</p>
 * <table border="1">
 *     <caption>Pre-spin conditions</caption>
 *     <tr><th>Code</th><th>Condition</th></tr>
 *     <tr><td>C1</td><td>{@code input.isEmpty() || target.isEmpty()}</td></tr>
 *     <tr><td>C2</td><td>{@code inputValue <= 0 || targetValue <= 0}</td></tr>
 *     <tr><td>C3</td><td>{@code ItemStack.isSameItemSameTags(input, target)}</td></tr>
 *     <tr><td>C4</td><td>{@code player.isCreative() && targetValue >= 1_000_000}</td></tr>
 *     <tr><td>C5</td><td>{@code inputValue > targetValue * 100}</td></tr>
 *     <tr><td>C6</td><td>{@code inputValue > LOG_THRESHOLD} &ndash; additionally written to
 *     {@code logs/upgradermod_suspicious.log}</td></tr>
 * </table>
 */
public final class SpinValidator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Target value from which on creative players are refused (C4). */
    public static final long CREATIVE_TARGET_LIMIT = 1_000_000L;

    /** Input/target value ratio from which on a spin is refused (C5). */
    public static final long MAX_VALUE_RATIO = 100L;

    /**
     * Outcome of a pre-spin check.
     *
     * @param allowed    {@code true} when the spin may proceed
     * @param code       {@code 0} when allowed, otherwise {@code 1..6} for C1..C6
     * @param messageKey translation key of the refusal message, {@code null} when allowed
     */
    public record Validation(boolean allowed, int code, @Nullable String messageKey) {

        /** Shared instance for the "spin may proceed" case. */
        public static final Validation ALLOWED = new Validation(true, 0, null);

        /**
         * Builds a refusal.
         *
         * @param code       condition number, {@code 1..6}
         * @param messageKey translation key shown to the player
         * @return a refusing validation
         */
        public static Validation cancel(int code, String messageKey) {
            return new Validation(false, code, messageKey);
        }
    }

    /**
     * Runs every condition in order C1 &rarr; C6.
     *
     * @param input       the stack that would be consumed
     * @param target      the stack that would be won
     * @param inputValue  value of the input stack
     * @param targetValue value of the target stack
     * @param player      the spinning player
     * @return the first matching condition, or {@link Validation#ALLOWED}
     */
    public static Validation validate(ItemStack input, ItemStack target, long inputValue, long targetValue, Player player) {
        try {
            boolean inputEmpty = input == null || input.isEmpty();
            boolean targetEmpty = target == null || target.isEmpty();

            // C1 - both slots have to hold something.
            if (inputEmpty || targetEmpty) {
                return Validation.cancel(1, "upgradermod.spin.cancel.empty");
            }

            // C2 - both values have to be positive.
            if (inputValue <= 0L || targetValue <= 0L) {
                return Validation.cancel(2, "upgradermod.spin.cancel.value");
            }

            // C3 - upgrading an item into itself is not an upgrade.
            if (ItemStack.isSameItemSameTags(input, target)) {
                return Validation.cancel(3, "upgradermod.spin.cancel.same");
            }

            // C4 - creative players may not farm expensive targets.
            if (player != null && player.isCreative() && targetValue >= CREATIVE_TARGET_LIMIT) {
                return Validation.cancel(4, "upgradermod.spin.cancel.creative");
            }

            // C6 is audited before C5 so an input above the suspicious threshold is always written
            // to disk, even when it also trips the value-ratio guard.
            boolean suspicious = inputValue > UpgraderConstants.LOG_THRESHOLD;
            if (suspicious) {
                SuspiciousLogger.log(player, input, target, inputValue, targetValue);
            }

            // C5 - the input must not be worth more than 100x the target. Compared in double to stay
            // overflow safe for the full value range.
            if ((double) inputValue > (double) targetValue * (double) MAX_VALUE_RATIO) {
                return Validation.cancel(5, "upgradermod.spin.cancel.ratio");
            }

            // C6 - absurd input values are audited and refused.
            if (suspicious) {
                return Validation.cancel(6, "upgradermod.spin.cancel.suspicious");
            }

            return Validation.ALLOWED;
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader pre-spin check failed, the spin is cancelled", throwable);
            return Validation.cancel(0, "upgradermod.spin.cancel.error");
        }
    }

    private SpinValidator() {
        // Static access only.
    }
}
