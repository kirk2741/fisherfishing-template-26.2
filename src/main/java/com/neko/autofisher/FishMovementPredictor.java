
package com.neko.autofisher;

public class FishMovementPredictor {

    private int previousX = -1;
    private long previousTimeNs = 0;

    private double velocity = 0;
    private boolean hasVelocity = false;

    private static final double MAX_SPEED = 1400.0;
    private static final double SMOOTHING = 0.6;

    public void update(int fishX, long timestampNs) {

        if (previousTimeNs > 0
                && timestampNs > previousTimeNs) {

            double seconds =
                    (timestampNs - previousTimeNs)
                            / 1_000_000_000.0;

            if (seconds >= 0.005 && seconds <= 0.20) {

                double measured =
                        (fishX - previousX) / seconds;

                if (Math.abs(measured) <= MAX_SPEED) {

                    // Ignore no-motion measurements caused
                    // by the fish being rendered at the same X.
                    if (fishX != previousX) {

                        if (!hasVelocity
                                || Math.signum(measured)
                                != Math.signum(velocity)) {

                            velocity = measured;

                        } else {

                            velocity =
                                    SMOOTHING * measured
                                            + (1.0 - SMOOTHING)
                                            * velocity;
                        }

                        hasVelocity = true;
                    }

                } else {
                    hasVelocity = false;
                    velocity = 0;
                }

            } else {
                hasVelocity = false;
                velocity = 0;
            }
        }

        previousX = fishX;
        previousTimeNs = timestampNs;
    }

    public int predictX(long millisecondsAhead) {

        if (previousX < 0 || !hasVelocity) {
            return -1;
        }

        double seconds = millisecondsAhead / 1000.0;

        return (int) Math.round(
                previousX + velocity * seconds
        );
    }

    public boolean hasVelocity() {
        return hasVelocity;
    }

    public double getVelocity() {
        return velocity;
    }

    public void reset() {
        previousX = -1;
        previousTimeNs = 0;
        velocity = 0;
        hasVelocity = false;
    }
}
