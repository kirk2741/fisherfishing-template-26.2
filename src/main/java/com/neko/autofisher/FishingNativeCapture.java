
package com.neko.autofisher;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

public class FishingNativeCapture {

    private static final long CAPTURE_INTERVAL_NS =
            33_333_333L;

    private static final long LOG_INTERVAL_NS =
            500_000_000L;

    private static final long MAX_FRAME_AGE_NS =
            750_000_000L;

    private static final int MIN_FISH_SCORE = 60;

    // Prediction remains diagnostic-only.
    private static final long PREDICTION_MS = 30;

    private static final int BASE_SAFE_MARGIN = 4;

    public record Observation(
            boolean hudVisible,
            boolean insideGreen,
            boolean hudGone,
            long timestampNs
    ) {}

    private static final Observation UNKNOWN =
            new Observation(false, false, false, 0);

    private volatile Observation latest = UNKNOWN;

    private volatile boolean capturePending = false;

    private volatile int generation = 0;

    private int validStreak = 0;
    private int absentStreak = 0;

    private boolean seenHud = false;

    private long lastCapture = 0;
    private long lastLog = 0;

    private long fpsStart = System.nanoTime();
    private int processedFrames = 0;

    private final FishMovementPredictor predictor =
            new FishMovementPredictor();

    private boolean previousOverlap = false;
    private boolean previousPredictedInside = false;

    public void reset() {

        generation++;

        latest = UNKNOWN;

        validStreak = 0;
        absentStreak = 0;

        seenHud = false;

        predictor.reset();

        previousOverlap = false;
        previousPredictedInside = false;
    }

    public Observation getObservation() {

        Observation observation = latest;

        if (observation.timestampNs() == 0) {
            return UNKNOWN;
        }

        long age =
                System.nanoTime() - observation.timestampNs();

        if (age < 0 || age > MAX_FRAME_AGE_NS) {
            return UNKNOWN;
        }

        return observation;
    }

    public void onRenderFrame(
            RenderFrameEvent.Post event
    ) {

        Minecraft mc = Minecraft.getInstance();

        if (!FisherFishingClient.CONTROLLER.isEnabled()
                || mc.player == null
                || mc.level == null
                || mc.gui.screen() != null
                || capturePending) {
            return;
        }

        long now = System.nanoTime();

        if (now - lastCapture < CAPTURE_INTERVAL_NS) {
            return;
        }

        lastCapture = now;
        capturePending = true;

        int requestGeneration = generation;

        try {

            Screenshot.takeScreenshot(
                    mc.gameRenderer.mainRenderTarget(),
                    image -> processScreenshot(
                            image,
                            requestGeneration
                    )
            );

        } catch (Exception e) {

            capturePending = false;

            FisherFishing.LOGGER.error(
                    "[Native] Capture request failed",
                    e
            );
        }
    }

    private void processScreenshot(
            NativeImage image,
            int requestGeneration
    ) {

        try {

            if (image == null
                    || requestGeneration != generation
                    || !FisherFishingClient.CONTROLLER.isEnabled()) {
                return;
            }

            // Full-frame conversion for compatibility
            // testing across resolutions.
            BufferedImage frame = convertImage(image);

            long now = System.nanoTime();

            updatePerformance(now);

            // ======================================
            // NEW: Detect the fishing bar first.
            // ======================================

            FishingBarDetector.Bar bar =
                    FishingBarDetector.detect(frame);

            List<GreenZoneDetector.GreenZone> zones =
                    GreenZoneDetector.detect(frame, bar);

            FishPositionDetector.FishPosition fish =
                    FishPositionDetector.detect(frame, bar);

            boolean hasGreen = !zones.isEmpty();

            boolean fishValid =
                    fish != null
                            && fish.darkPixels() >= MIN_FISH_SCORE;

            boolean validHud =
                    bar != null && hasGreen && fishValid;

            // ======================================
            // HUD confirmation.
            // ======================================

            if (validHud) {

                validStreak++;
                absentStreak = 0;

            } else if (bar == null || !hasGreen) {

                absentStreak++;
                validStreak = 0;

            } else {

                validStreak = 0;
                absentStreak = 0;
            }

            if (validStreak >= 2) {
                seenHud = true;
            }

            boolean hudVisible =
                    seenHud && validHud;

            boolean hudGone =
                    seenHud && absentStreak >= 2;

            // ======================================
            // Actual partial-overlap detection.
            // ======================================

            boolean insideGreen = false;

            if (hudVisible && fishValid) {

                insideGreen =
                        GreenZoneDetector
                                .isFishOverlappingGreen(
                                        fish.centerX(),
                                        zones,
                                        frame.getWidth()
                                );
            }

            // ======================================
            // Movement prediction: diagnostics only.
            // ======================================

            if (hudVisible && fishValid) {

                predictor.update(
                        fish.centerX(),
                        now
                );

            } else {

                predictor.reset();
                previousPredictedInside = false;
            }

            int predictedX = -1;
            boolean predictedInside = false;

            if (hudVisible
                    && fishValid
                    && predictor.hasVelocity()) {

                predictedX =
                        predictor.predictX(PREDICTION_MS);

                predictedInside =
                        isPredictedInsideGreen(
                                predictedX,
                                zones,
                                frame.getWidth()
                        );
            }

            if (insideGreen && !previousOverlap) {

                FisherFishing.LOGGER.info(
                        "[OVERLAP HIT] Fish={}, Green={}, Score={}, Velocity={}",
                        fish.centerX(),
                        zones,
                        fish.darkPixels(),
                        Math.round(predictor.getVelocity())
                );
            }

            if (predictedInside
                    && !previousPredictedInside) {

                FisherFishing.LOGGER.info(
                        "[PREDICT HIT] Fish={}, FutureX={}, Velocity={}, Green={}, ActualOverlap={}",
                        fish.centerX(),
                        predictedX,
                        Math.round(predictor.getVelocity()),
                        zones,
                        insideGreen
                );
            }

            previousOverlap = insideGreen;
            previousPredictedInside = predictedInside;

            // Only actual overlap goes to the
            // automatic fishing controller.
            latest = new Observation(
                    hudVisible,
                    insideGreen,
                    hudGone,
                    now
            );

            // ======================================
            // Periodic diagnostics.
            // ======================================

            if (now - lastLog >= LOG_INTERVAL_NS) {

                lastLog = now;

                FisherFishing.LOGGER.info(
                        "[Native] Frame={}x{}, Bar={}, HUD={}, Green={}, Fish={}, Score={}, Velocity={}, Inside={}, PredictedX={}, PredictedInside={}, Gone={}",
                        frame.getWidth(),
                        frame.getHeight(),
                        bar,
                        hudVisible,
                        zones,
                        fish != null ? fish.centerX() : -1,
                        fish != null ? fish.darkPixels() : 0,
                        Math.round(predictor.getVelocity()),
                        insideGreen,
                        predictedX,
                        predictedInside,
                        hudGone
                );
            }

        } catch (Exception e) {

            latest = UNKNOWN;

            predictor.reset();
            previousOverlap = false;
            previousPredictedInside = false;

            FisherFishing.LOGGER.error(
                    "[Native] Detection failed",
                    e
            );

        } finally {

            if (image != null) {
                image.close();
            }

            capturePending = false;
        }
    }

    private boolean isPredictedInsideGreen(
            int predictedX,
            List<GreenZoneDetector.GreenZone> zones,
            int frameWidth
    ) {

        if (predictedX < 0) {
            return false;
        }

        int margin = Math.max(
                1,
                Math.round(
                        BASE_SAFE_MARGIN * frameWidth
                                / 1920.0f
                )
        );

        for (GreenZoneDetector.GreenZone zone : zones) {

            int zoneWidth =
                    zone.endX() - zone.startX() + 1;

            int safeMargin =
                    Math.min(margin, zoneWidth / 4);

            int safeStart =
                    zone.startX() + safeMargin;

            int safeEnd =
                    zone.endX() - safeMargin;

            if (predictedX >= safeStart
                    && predictedX <= safeEnd) {
                return true;
            }
        }

        return false;
    }

    private void updatePerformance(long now) {

        processedFrames++;

        long elapsed = now - fpsStart;

        if (elapsed < 1_000_000_000L) {
            return;
        }

        double seconds =
                elapsed / 1_000_000_000.0;

        double fps =
                processedFrames / seconds;

        FisherFishing.LOGGER.info(
                "[Native PERF] Processed FPS: {}",
                String.format(
                        Locale.ROOT,
                        "%.1f",
                        fps
                )
        );

        fpsStart = now;
        processedFrames = 0;
    }

    private BufferedImage convertImage(
            NativeImage source
    ) {

        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage frame = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                frame.setRGB(
                        x,
                        y,
                        source.getPixel(x, y)
                );
            }
        }

        return frame;
    }
}
