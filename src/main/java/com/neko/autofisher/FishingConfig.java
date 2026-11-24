
package com.neko.autofisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class FishingConfig {

    private static final Path CONFIG_PATH =
            Path.of("config", "fisherfishing.properties");

    private static FishingConfig current;

    public final boolean autoRecovery;
    public final int waitingTimeoutSeconds;
    public final int minigameTimeoutSeconds;
    public final int recoveryDelayMs;
    public final int catchCooldownMs;

    private FishingConfig(
            boolean autoRecovery,
            int waitingTimeoutSeconds,
            int minigameTimeoutSeconds,
            int recoveryDelayMs,
            int catchCooldownMs
    ) {
        this.autoRecovery = autoRecovery;
        this.waitingTimeoutSeconds = waitingTimeoutSeconds;
        this.minigameTimeoutSeconds = minigameTimeoutSeconds;
        this.recoveryDelayMs = recoveryDelayMs;
        this.catchCooldownMs = catchCooldownMs;
    }

    public static synchronized FishingConfig get() {
        if (current == null) {
            current = load();
        }
        return current;
    }

    public static synchronized FishingConfig reload() {
        current = load();
        return current;
    }

    private static FishingConfig load() {

        Properties properties = new Properties();

        properties.setProperty(
                "autoRecovery", "true"
        );
        properties.setProperty(
                "waitingTimeoutSeconds", "90"
        );
        properties.setProperty(
                "minigameTimeoutSeconds", "30"
        );
        properties.setProperty(
                "recoveryDelayMs", "2000"
        );
        properties.setProperty(
                "catchCooldownMs", "2000"
        );

        try {

            Files.createDirectories(
                    CONFIG_PATH.getParent()
            );

            if (Files.exists(CONFIG_PATH)) {

                try (InputStream in =
                             Files.newInputStream(CONFIG_PATH)) {
                    properties.load(in);
                }

            } else {

                try (OutputStream out =
                             Files.newOutputStream(CONFIG_PATH)) {

                    properties.store(
                            out,
                            "Neko Auto Fisher Configuration"
                    );
                }

                FisherFishing.LOGGER.info(
                        "[Auto Fisher] Created config: {}",
                        CONFIG_PATH.toAbsolutePath()
                );
            }

        } catch (IOException e) {

            FisherFishing.LOGGER.error(
                    "[Auto Fisher] Could not load configuration. Using defaults.",
                    e
            );
        }

        FishingConfig config = new FishingConfig(

                Boolean.parseBoolean(
                        properties.getProperty(
                                "autoRecovery", "true"
                        )
                ),

                readInt(
                        properties,
                        "waitingTimeoutSeconds",
                        90, 15, 3600
                ),

                readInt(
                        properties,
                        "minigameTimeoutSeconds",
                        30, 5, 600
                ),

                readInt(
                        properties,
                        "recoveryDelayMs",
                        2000, 500, 30000
                ),

                readInt(
                        properties,
                        "catchCooldownMs",
                        2000, 500, 30000
                )
        );

        FisherFishing.LOGGER.info(
                "[Auto Fisher] Config loaded: autoRecovery={}, waitingTimeout={}s, minigameTimeout={}s, recoveryDelay={}ms, catchCooldown={}ms",
                config.autoRecovery,
                config.waitingTimeoutSeconds,
                config.minigameTimeoutSeconds,
                config.recoveryDelayMs,
                config.catchCooldownMs
        );

        return config;
    }

    private static int readInt(
            Properties properties,
            String key,
            int defaultValue,
            int minimum,
            int maximum
    ) {

        try {

            int value = Integer.parseInt(
                    properties.getProperty(
                            key,
                            Integer.toString(defaultValue)
                    ).trim()
            );

            return Math.max(
                    minimum,
                    Math.min(maximum, value)
            );

        } catch (NumberFormatException e) {

            FisherFishing.LOGGER.warn(
                    "[Auto Fisher] Invalid config value for {}. Using {}.",
                    key,
                    defaultValue
            );

            return defaultValue;
        }
    }
}
