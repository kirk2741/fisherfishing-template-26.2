
package com.neko.autofisher;

public class FishingController {

    public enum State {
        DISABLED,
        CASTING,
        WAITING_FOR_FISH,
        WAITING_FOR_GREEN,
        COOLDOWN
    }

    private State state = State.DISABLED;

    private long cooldownEnd = 0;

    private static final long DELAY_MS = 2000;

    public void toggle() {
        if (state == State.DISABLED) {
            state = State.CASTING;
        } else {
            disable();
        }
    }

    public void disable() {
        state = State.DISABLED;
        cooldownEnd = 0;
    }

    public boolean isEnabled() {
        return state != State.DISABLED;
    }

    public State getState() {
        return state;
    }

    public void tick(
            boolean hudVisible,
            boolean insideGreen,
            boolean hudGone,
            Runnable cast,
            Runnable catchFish
    ) {
        if (!isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();

        switch (state) {

            case CASTING:
                cast.run();
                state = State.WAITING_FOR_FISH;
                break;

            case WAITING_FOR_FISH:
                if (hudVisible) {
                    if (insideGreen) {
                        performCatch(now, catchFish);
                    } else {
                        state = State.WAITING_FOR_GREEN;
                    }
                }
                break;

            case WAITING_FOR_GREEN:
                if (hudGone) {
                    state = State.WAITING_FOR_FISH;
                    break;
                }

                if (hudVisible && insideGreen) {
                    performCatch(now, catchFish);
                }
                break;

            case COOLDOWN:
                if (now >= cooldownEnd && hudGone) {
                    state = State.CASTING;
                }
                break;

            default:
                break;
        }
    }

    private void performCatch(
            long now,
            Runnable catchFish
    ) {
        catchFish.run();

        cooldownEnd = now + DELAY_MS;

        state = State.COOLDOWN;

        FisherFishing.LOGGER.info(
                "[Auto Fisher] Catch triggered, cooldown started"
        );
    }
}
