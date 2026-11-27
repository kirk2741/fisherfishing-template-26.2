
package com.neko.autofisher;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FishingConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue AUTO_RECOVERY;
    private static final ModConfigSpec.BooleanValue STOP_WHEN_INVENTORY_FULL;

    private static final ModConfigSpec.IntValue WAITING_TIMEOUT;
    private static final ModConfigSpec.IntValue MINIGAME_TIMEOUT;
    private static final ModConfigSpec.IntValue RECOVERY_DELAY;

    private static final ModConfigSpec.IntValue MIN_CATCH_DELAY;
    private static final ModConfigSpec.IntValue MAX_CATCH_DELAY;

    static {
        ModConfigSpec.Builder builder =
                new ModConfigSpec.Builder();

        builder.push("recovery");

        AUTO_RECOVERY = builder
                .comment("Recover automatically if fishing gets stuck.")
                .translation("fisherfishing.config.autoRecovery")
                .define("autoRecovery", true);

        WAITING_TIMEOUT = builder
                .comment("Seconds to wait for the fishing HUD.")
                .translation("fisherfishing.config.waitingTimeout")
                .defineInRange(
                        "waitingTimeoutSeconds",
                        90, 15, 3600
                );

        MINIGAME_TIMEOUT = builder
                .comment("Seconds before a fishing minigame times out.")
                .translation("fisherfishing.config.minigameTimeout")
                .defineInRange(
                        "minigameTimeoutSeconds",
                        30, 5, 600
                );

        RECOVERY_DELAY = builder
                .comment("Milliseconds to wait before recasting after recovery.")
                .translation("fisherfishing.config.recoveryDelay")
                .defineInRange(
                        "recoveryDelayMs",
                        2000, 500, 30000
                );

        builder.pop();

        builder.push("catching");

        STOP_WHEN_INVENTORY_FULL = builder
                .comment("Stop before casting when all 36 inventory and hotbar slots are occupied.")
                .translation("fisherfishing.config.stopWhenInventoryFull")
                .define("stopWhenInventoryFull", false);

        MIN_CATCH_DELAY = builder
                .comment("Minimum milliseconds after catching before recasting.")
                .translation("fisherfishing.config.minCatchDelay")
                .defineInRange(
                        "minCatchDelayMs",
                        1000, 500, 30000
                );

        MAX_CATCH_DELAY = builder
                .comment("Maximum milliseconds after catching before recasting.")
                .translation("fisherfishing.config.maxCatchDelay")
                .defineInRange(
                        "maxCatchDelayMs",
                        4000, 500, 30000
                );

        builder.pop();

        SPEC = builder.build();
    }

    public final boolean autoRecovery;
    public final int waitingTimeoutSeconds;
    public final int minigameTimeoutSeconds;
    public final int recoveryDelayMs;

    public final int minCatchDelayMs;
    public final int maxCatchDelayMs;

    // Retained for compatibility with any existing
    // code that still references catchCooldownMs.
    public final int catchCooldownMs;

    private FishingConfig() {
        autoRecovery = AUTO_RECOVERY.get();
        waitingTimeoutSeconds = WAITING_TIMEOUT.get();
        minigameTimeoutSeconds = MINIGAME_TIMEOUT.get();
        recoveryDelayMs = RECOVERY_DELAY.get();

        int minimum = MIN_CATCH_DELAY.get();
        int maximum = MAX_CATCH_DELAY.get();

        minCatchDelayMs = Math.min(minimum, maximum);
        maxCatchDelayMs = Math.max(minimum, maximum);

        catchCooldownMs = minCatchDelayMs;
    }

    public static FishingConfig get() {
        return new FishingConfig();
    }

    public static boolean stopWhenInventoryFull() {
        return STOP_WHEN_INVENTORY_FULL.get();
    }

    public static FishingConfig reload() {
        // NeoForge already manages and saves its
        // registered configuration.
        return new FishingConfig();
    }
}
