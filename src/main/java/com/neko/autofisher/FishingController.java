
package com.neko.autofisher;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadLocalRandom;

public class FishingController {

    public enum State {
        DISABLED,
        CASTING,
        WAITING_FOR_FISH,
        WAITING_FOR_GREEN,
        COOLDOWN,
        RECOVERY_COOLDOWN
    }

    private State state = State.DISABLED;

    private long stateStartMs = 0;
    private long cooldownEndMs = 0;

    private FishingConfig config = FishingConfig.get();

    // Prevent repeated recovery clicks when the
    // hook is already missing.
    private boolean recoveryReeledIn = false;

    public void toggle() {

        if (state == State.DISABLED) {

            config = FishingConfig.reload();

            state = State.CASTING;
            stateStartMs = System.currentTimeMillis();
            cooldownEndMs = 0;

            recoveryReeledIn = false;

            FisherFishing.LOGGER.info(
                    "[Auto Fisher] ON"
            );

        } else {
            disable();
        }
    }

    public void disable() {

        state = State.DISABLED;

        stateStartMs = 0;
        cooldownEndMs = 0;

        recoveryReeledIn = false;

        FisherFishing.LOGGER.info(
                "[Auto Fisher] OFF"
        );
    }

    public boolean isEnabled() {
        return state != State.DISABLED;
    }

    public State getState() {
        return state;
    }

    /**
     * Called once per client tick.
     *
     * @param hudVisible Fishing minigame visible
     * @param insideGreen Fish overlaps a green zone
     * @param hudGone Previously seen minigame disappeared
     * @param hookPresent Player currently has a fishing hook
     * @param cast Action used to cast the rod
     * @param catchFish Normal minigame catch action
     * @param recover Action used to retrieve the rod
     */
    public void tick(
            boolean hudVisible,
            boolean insideGreen,
            boolean hudGone,
            boolean hookPresent,
            Runnable cast,
            Runnable catchFish,
            Runnable recover
    ) {

        if (!isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();

        switch (state) {

            case CASTING: {

                // Important:
                // Do not click if the rod is already cast.
                // Otherwise the click would retrieve it.
                if (!hookPresent) {

                    cast.run();

                    FisherFishing.LOGGER.info(
                            "[Auto Fisher] Cast requested"
                    );

                } else {

                    FisherFishing.LOGGER.info(
                            "[Auto Fisher] Hook already deployed. Skipping cast."
                    );
                }

                setState(
                        State.WAITING_FOR_FISH,
                        now
                );

                break;
            }

            case WAITING_FOR_FISH: {

                if (hudVisible) {

                    if (insideGreen) {

                        performCatch(
                                now,
                                catchFish
                        );

                    } else {

                        setState(
                                State.WAITING_FOR_GREEN,
                                now
                        );
                    }

                    break;
                }

                // If no HUD appears for too long,
                // recover based on hook state.
                if (config.autoRecovery
                        && elapsedMs(now)
                        >= config.waitingTimeoutSeconds
                        * 1000L) {

                    startRecovery(
                            now,
                            hookPresent,
                            recover,
                            "No fishing HUD detected"
                    );
                }

                break;
            }

            case WAITING_FOR_GREEN: {

                if (hudVisible && insideGreen) {

                    performCatch(
                            now,
                            catchFish
                    );

                    break;
                }

                // The HUD disappeared unexpectedly.
                if (hudGone) {

                    FisherFishing.LOGGER.warn(
                            "[Auto Fisher] Fishing HUD disappeared before catch"
                    );

                    if (config.autoRecovery) {

                        startRecovery(
                                now,
                                hookPresent,
                                recover,
                                "Fishing HUD disappeared"
                        );

                    } else {

                        setState(
                                State.WAITING_FOR_FISH,
                                now
                        );
                    }

                    break;
                }

                // Fishing minigame remains active
                // longer than expected.
                if (config.autoRecovery
                        && elapsedMs(now)
                        >= config.minigameTimeoutSeconds
                        * 1000L) {

                    startRecovery(
                            now,
                            hookPresent,
                            recover,
                            "Fishing minigame timeout"
                    );
                }

                break;
            }

            case COOLDOWN: {

                if (now < cooldownEndMs) {
                    break;
                }

                // Normal catch finished and HUD gone.
                if (hudGone) {

                    setState(
                            State.CASTING,
                            now
                    );

                    break;
                }

                // If the HUD never disappears, do not
                // remain in cooldown forever.
                long maximumWait =
                        config.minigameTimeoutSeconds
                                * 1000L;

                if (config.autoRecovery
                        && elapsedMs(now) >= maximumWait) {

                    startRecovery(
                            now,
                            hookPresent,
                            recover,
                            "HUD did not disappear after catch"
                    );
                }

                break;
            }

            case RECOVERY_COOLDOWN: {

                if (now < cooldownEndMs) {
                    break;
                }

                if (hookPresent) {

                    // Hook may not have disappeared yet.
                    // Avoid accidentally casting/reeling
                    // back and forth.
                    FisherFishing.LOGGER.warn(
                            "[Auto Fisher] Recovery delayed: hook still deployed"
                    );

                    // Retry the recovery click only after
                    // another recovery delay.
                    // This is bounded by the configured
                    // delay, not repeated every tick.
                    if (config.autoRecovery
                            && recoveryReeledIn) {

                        recover.run();

                        cooldownEndMs =
                                now + config.recoveryDelayMs;

                        FisherFishing.LOGGER.info(
                                "[Auto Fisher] Recovery reel-in retry"
                        );
                    }

                    break;
                }

                recoveryReeledIn = false;

                FisherFishing.LOGGER.info(
                        "[Auto Fisher] Recovery complete. Preparing to cast."
                );

                setState(
                        State.CASTING,
                        now
                );

                break;
            }

            default:
                break;
        }
    }


    private void performCatch(
            long now,
            Runnable catchFish
    ) {
        // Catch immediately.
        catchFish.run();

        // Read the latest values from the in-game config.
        FishingConfig settings = FishingConfig.get();

        int min = settings.minCatchDelayMs;
        int max = settings.maxCatchDelayMs;

        int delayMs = ThreadLocalRandom.current()
                .nextInt(min, max + 1);

        cooldownEndMs = now + delayMs;

        setState(State.COOLDOWN, now);

        FisherFishing.LOGGER.info(
                "[Auto Fisher] Catch triggered, random cooldown={}ms",
                delayMs
        );
    }



    private void startRecovery(
            long now,
            boolean hookPresent,
            Runnable recover,
            String reason
    ) {

        FisherFishing.LOGGER.warn(
                "[Auto Fisher] Recovery triggered: {}",
                reason
        );

        recoveryReeledIn = false;

        if (hookPresent) {

            // Hook exists, so retrieve it first.
            recover.run();
            recoveryReeledIn = true;

            FisherFishing.LOGGER.info(
                    "[Auto Fisher] Recovery: retrieving fishing hook"
            );

        } else {

            // No hook exists.
            // Do not right-click here, since
            // that would cast instead of reel in.
            FisherFishing.LOGGER.info(
                    "[Auto Fisher] Recovery: hook already missing, skipping reel-in"
            );
        }

        cooldownEndMs =
                now + config.recoveryDelayMs;

        setState(
                State.RECOVERY_COOLDOWN,
                now
        );
    }

    private long elapsedMs(long now) {

        return Math.max(
                0,
                now - stateStartMs
        );
    }

    private void setState(
            State newState,
            long now
    ) {

        if (state == newState) {
            return;
        }

        FisherFishing.LOGGER.debug(
                "[Auto Fisher] State: {} -> {}",
                state,
                newState
        );

        state = newState;
        stateStartMs = now;
    }
}
