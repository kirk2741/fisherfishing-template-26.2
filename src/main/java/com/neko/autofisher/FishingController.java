package com.neko.autofisher;

import java.util.concurrent.ThreadLocalRandom;

public class FishingController {
    public enum State {
        DISABLED, CASTING, WAITING_FOR_FISH, WAITING_FOR_GREEN, COOLDOWN
    }

    private State state = State.DISABLED;
    private long stateStartMs;
    private long cooldownEndMs;
    private long pausedAtMs;
    private FishingConfig config;

    public void toggle() {
        if (isEnabled()) {
            disable();
            return;
        }
        config = FishingConfig.reload();
        state = State.CASTING;
        stateStartMs = System.currentTimeMillis();
        cooldownEndMs = 0;
        pausedAtMs = 0;
        FisherFishing.LOGGER.info("[Auto Fisher] ON");
    }

    public void disable() {
        state = State.DISABLED;
        stateStartMs = 0;
        cooldownEndMs = 0;
        pausedAtMs = 0;
        FisherFishing.LOGGER.info("[Auto Fisher] OFF");
    }

    public boolean isEnabled() {
        return state != State.DISABLED;
    }

    public State getState() {
        return state;
    }

    // Do not let time spent in menus expire fishing or recast timers.
    public void pause() {
        if (isEnabled() && pausedAtMs == 0) {
            pausedAtMs = System.currentTimeMillis();
        }
    }

    public void resume() {
        if (pausedAtMs != 0) {
            long pausedFor = Math.max(0, System.currentTimeMillis() - pausedAtMs);
            stateStartMs += pausedFor;
            if (cooldownEndMs != 0) {
                cooldownEndMs += pausedFor;
            }
            pausedAtMs = 0;
        }
    }

    public void tick(boolean hudVisible, boolean insideGreen,
                     boolean hookPresent, Runnable cast, Runnable catchFish) {
        if (!isEnabled() || pausedAtMs != 0) {
            return;
        }

        long now = System.currentTimeMillis();
        switch (state) {
            case CASTING -> {
                if (!hookPresent) {
                    cast.run();
                    FisherFishing.LOGGER.info("[Auto Fisher] Cast requested");
                } else {
                    FisherFishing.LOGGER.debug("[Auto Fisher] Hook already deployed. Skipping cast.");
                }
                setState(State.WAITING_FOR_FISH, now);
            }
            case WAITING_FOR_FISH, WAITING_FOR_GREEN -> {
                if (hudVisible && insideGreen) {
                    performCatch(now, catchFish);
                    break;
                }
                if (hudVisible && state == State.WAITING_FOR_FISH) {
                    setState(State.WAITING_FOR_GREEN, now);
                }

                // Never reel in because of a missing HUD or timeout. Periodically
                // check the hook and cast only once it is actually gone.
                long timeoutSeconds = state == State.WAITING_FOR_GREEN
                        ? config.minigameTimeoutSeconds : config.waitingTimeoutSeconds;
                if (now - stateStartMs >= timeoutSeconds * 1000L) {
                    if (!hookPresent) {
                        setState(State.CASTING, now);
                    } else {
                        stateStartMs = now;
                        FisherFishing.LOGGER.debug("[Auto Fisher] Hook still deployed; continuing to wait");
                    }
                }
            }
            case COOLDOWN -> {
                if (now >= cooldownEndMs && !hookPresent) {
                    // The random delay is independent of HUD disappearance.
                    // Do not react to the old HUD while the hook is still out.
                    setState(State.CASTING, now);
                }
            }
            default -> { }
        }
    }

    private void performCatch(long now, Runnable catchFish) {
        catchFish.run();
        FishingConfig settings = FishingConfig.get();
        int delayMs = ThreadLocalRandom.current().nextInt(
                settings.minCatchDelayMs, settings.maxCatchDelayMs + 1);
        cooldownEndMs = now + delayMs;
        setState(State.COOLDOWN, now);
        FisherFishing.LOGGER.info("[Auto Fisher] Catch triggered, random cooldown={}ms", delayMs);
    }

    private void setState(State newState, long now) {
        if (state != newState) {
            FisherFishing.LOGGER.debug("[Auto Fisher] State: {} -> {}", state, newState);
            state = newState;
            stateStartMs = now;
        }
    }
}
