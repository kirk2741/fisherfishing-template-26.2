
package com.neko.autofisher;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

public class FishingNativeCapture {

    // Fast sampling while the minigame is visible.
    private static final long ACTIVE_INTERVAL_NS =
            33_333_333L;

    // Lower sampling rate while waiting for the HUD.
    private static final long IDLE_INTERVAL_NS =
            100_000_000L;

    private static final long LOG_INTERVAL_NS =
            1_000_000_000L;

    private static final long MAX_FRAME_AGE_NS =
            750_000_000L;

    // Rediscover candidates that never reveal a green zone.
    private static final long BAR_RECHECK_NS = 500_000_000L;

    // Brief detection gaps must not be mistaken for a vanished HUD.
    private static final long HUD_ABSENCE_GRACE_NS = 1_000_000_000L;

    private static final int MIN_FISH_SCORE = 60;

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

    private FishingBarDetector.Bar cachedBar = null;
    private long noGreenSinceNs = 0;
    private boolean revalidateAfterScreen = false;

    // Reuse the image rather than allocating a new
    // full-screen BufferedImage every frame.
    private BufferedImage cachedFrame = null;

    private int validStreak = 0;
    private long absentSinceNs = 0;

    private boolean seenHud = false;

    private long lastCapture = 0;
    private long lastLog = 0;

    private long fpsStart = System.nanoTime();
    private int processedFrames = 0;

    private long conversionTotalNs = 0;
    private long detectionTotalNs = 0;
    private int timingSamples = 0;

    private final FishMovementPredictor predictor =
            new FishMovementPredictor();

    private boolean previousOverlap = false;
    private boolean previousPredictedInside = false;

    public void reset() {

        generation++;

        latest = UNKNOWN;

        cachedBar = null;
        noGreenSinceNs = 0;
        revalidateAfterScreen = false;

        validStreak = 0;
        absentSinceNs = 0;

        seenHud = false;

        predictor.reset();

        previousOverlap = false;
        previousPredictedInside = false;
    }

    // Keep the last confirmed bar location, but never reuse an old observation.
    // The next frame verifies the location before it can trigger a catch.
    public void resetForScreen() {
        generation++;
        latest = UNKNOWN;
        validStreak = 0;
        absentSinceNs = 0;
        noGreenSinceNs = 0;
        revalidateAfterScreen = cachedBar != null;
        if (!revalidateAfterScreen) {
            seenHud = false;
        }
        predictor.reset();
        previousOverlap = false;
        previousPredictedInside = false;
        lastCapture = 0;
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

        long interval =
                cachedBar != null
                        ? ACTIVE_INTERVAL_NS
                        : IDLE_INTERVAL_NS;

        if (now - lastCapture < interval) {
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
                    "[Native] Capture failed",
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

            long now = System.nanoTime();

            processedFrames++;

            int width = image.getWidth();
            int height = image.getHeight();

            // A resolution change invalidates the old bar.
            if (cachedFrame != null
                    && (cachedFrame.getWidth() != width
                    || cachedFrame.getHeight() != height)) {

                cachedBar = null;
                cachedFrame = null;
                noGreenSinceNs = 0;
                revalidateAfterScreen = false;

                validStreak = 0;
                absentSinceNs = 0;
                seenHud = false;

                predictor.reset();
                previousOverlap = false;
                previousPredictedInside = false;
                latest = UNKNOWN;
            }

            // ======================================
            // 1. Find or validate the fishing bar.
            // ======================================

            FishingBarDetector.Bar bar = cachedBar;

            if (bar != null
                    && !hasRedBar(image, bar)) {

                // Existing HUD disappeared or moved.
                cachedBar = null;
                bar = null;
                if (revalidateAfterScreen) {
                    seenHud = false;
                    validStreak = 0;
                }
            }
            revalidateAfterScreen = false;

            boolean candidateVisible = false;

            if (bar == null) {
                candidateVisible = hasRedBarCandidate(image);
            } else {
                candidateVisible = true;
            }

            List<GreenZoneDetector.GreenZone> zones =
                    List.of();

            FishPositionDetector.FishPosition fish =
                    null;

            long conversionNs = 0;
            long detectionNs = 0;

            if (candidateVisible) {

                if (bar == null) {
                    // Locate directly in the native image; avoid copying the
                    // entire screen when an unconfirmed candidate is retried.
                    long detectionStart = System.nanoTime();
                    bar = FishingBarDetector.detect(
                            width, height, image::getPixel
                    );
                    detectionNs += System.nanoTime() - detectionStart;

                    if (bar != null) {
                        cachedBar = bar;
                    }
                }

                if (bar != null) {

                    long conversionStart = System.nanoTime();
                    if (cachedFrame == null) {
                        cachedFrame = new BufferedImage(
                                width, height, BufferedImage.TYPE_INT_ARGB
                        );
                    }
                    updateBarRegion(image, bar);
                    conversionNs = System.nanoTime() - conversionStart;

                    long detectionStart =
                            System.nanoTime();

                    zones = GreenZoneDetector.detect(
                            cachedFrame,
                            bar
                    );

                    fish = FishPositionDetector.detect(
                            cachedFrame,
                            bar
                    );

                    detectionNs +=
                            System.nanoTime()
                                    - detectionStart;
                }
            }

            conversionTotalNs += conversionNs;
            detectionTotalNs += detectionNs;
            timingSamples++;

            boolean hasGreen = !zones.isEmpty();

            // A red object or an incomplete bar can pass the red-only cache
            // check. Periodically search the full frame until green is found.
            if (bar == null || hasGreen) {
                noGreenSinceNs = 0;
            } else if (noGreenSinceNs == 0) {
                noGreenSinceNs = now;
            } else if (now - noGreenSinceNs >= BAR_RECHECK_NS) {
                cachedBar = null;
                noGreenSinceNs = 0;
            }

            boolean fishValid =
                    fish != null
                            && fish.darkPixels() >= Math.max(15,
                                    Math.round(MIN_FISH_SCORE * Math.min(1.0f,
                                            bar.height() / 136.0f)
                                            * Math.min(1.0f, bar.height() / 136.0f)));

            boolean validHud =
                    bar != null
                            && hasGreen
                            && fishValid;

            // ======================================
            // 2. Confirm HUD presence.
            // ======================================

            if (validHud) {

                validStreak++;
            } else {
                validStreak = 0;
            }

            // A temporarily hidden or very narrow green zone does not mean
            // the red fishing bar has disappeared.
            if (bar != null) {
                absentSinceNs = 0;
            } else if (absentSinceNs == 0) {
                absentSinceNs = now;
            }

            if (validStreak >= 2) {
                seenHud = true;
            }

            boolean hudVisible =
                    seenHud && validHud;

            boolean hudGone =
                    seenHud && absentSinceNs != 0
                            && now - absentSinceNs >= HUD_ABSENCE_GRACE_NS;

            // ======================================
            // 3. Working partial-overlap detection.
            // ======================================

            boolean insideGreen = false;

            if (hudVisible && fishValid) {

                insideGreen =
                        GreenZoneDetector
                                .isFishOverlappingGreen(
                                        fish.centerX(),
                                        zones,
                                        bar.width()
                                );
            }

            // ======================================
            // 4. Fish movement predictor.
            // Diagnostic-only.
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
                                width
                        );
            }

            if (insideGreen && !previousOverlap) {

                FisherFishing.LOGGER.debug(
                        "[OVERLAP HIT] Fish={}, Green={}, Score={}, Velocity={}",
                        fish.centerX(),
                        zones,
                        fish.darkPixels(),
                        Math.round(predictor.getVelocity())
                );
            }

            if (predictedInside
                    && !previousPredictedInside) {

                FisherFishing.LOGGER.debug(
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

            // A screen may have opened or closed while this frame was processed.
            if (requestGeneration != generation) {
                return;
            }

            latest = new Observation(
                    hudVisible,
                    insideGreen,
                    hudGone,
                    now
            );

            // ======================================
            // 5. Performance logging.
            // ======================================

            if (now - lastLog >= LOG_INTERVAL_NS) {

                lastLog = now;

                double seconds =
                        (now - fpsStart)
                                / 1_000_000_000.0;

                if (seconds > 0 && FisherFishing.LOGGER.isDebugEnabled()) {

                    FisherFishing.LOGGER.debug(
                            "[Native PERF] Processed FPS={}, BarCached={}, Conversion={}ms, Detection={}ms",
                            String.format(
                                    Locale.ROOT,
                                    "%.1f",
                                    processedFrames / seconds
                            ),
                            cachedBar != null,
                            formatMs(
                                    conversionTotalNs,
                                    timingSamples
                            ),
                            formatMs(
                                    detectionTotalNs,
                                    timingSamples
                            )
                    );
                }

                FisherFishing.LOGGER.debug(
                        "[Native] Frame={}x{}, Bar={}, HUD={}, Green={}, Fish={}, Score={}, Velocity={}, Inside={}, PredictedX={}, PredictedInside={}, Gone={}",
                        width,
                        height,
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

                fpsStart = now;
                processedFrames = 0;

                conversionTotalNs = 0;
                detectionTotalNs = 0;
                timingSamples = 0;
            }

        } catch (Exception e) {

            latest = UNKNOWN;
            cachedBar = null;

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

    // ======================================
    // Fast preliminary red-bar search.
    // Avoid allocating and copying an entire
    // BufferedImage when the HUD is absent.
    // ======================================

    private boolean hasRedBarCandidate(
            NativeImage image
    ) {

        int width = image.getWidth();
        int height = image.getHeight();

        int minX = (int) (width * 0.08);
        int maxX = (int) (width * 0.92);

        int minY = (int) (height * 0.18);
        int maxY = (int) (height * 0.80);

        int stepX = Math.max(4, width / 180);
        int stepY = Math.max(4, height / 140);

        int samplesPerRow =
                (maxX - minX) / stepX + 1;

        int threshold = Math.max(
                12,
                (int) (samplesPerRow * 0.08)
        );

        for (int y = minY; y <= maxY; y += stepY) {

            int red = 0;

            for (int x = minX; x <= maxX; x += stepX) {

                if (isBarRed(image.getPixel(x, y))) {
                    red++;
                }
            }

            if (red >= threshold) {
                return true;
            }
        }

        return false;
    }

    // ======================================
    // Fast validation of cached bar location.
    // ======================================

    private boolean hasRedBar(
            NativeImage image,
            FishingBarDetector.Bar bar
    ) {

        if (bar.left() < 0
                || bar.top() < 0
                || bar.right() >= image.getWidth()
                || bar.bottom() >= image.getHeight()) {
            return false;
        }

        int checked = 0;
        int red = 0;

        // Test several rows inside the known bar.
        for (int row = 1; row <= 3; row++) {

            int y = bar.top()
                    + bar.height() * row / 4;

            for (int col = 1; col <= 24; col++) {

                int x = bar.left()
                        + bar.width() * col / 25;

                checked++;

                if (isBarRed(image.getPixel(x, y))) {
                    red++;
                }
            }
        }

        return red >= Math.max(8, checked / 5);
    }

    private boolean isBarRed(int rgb) {

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        return r >= 150
                && r > g * 1.35
                && r > b * 1.40
                && g < 185
                && b < 150;
    }

    // ======================================
    // Subsequent captures: copy HUD only.
    // Coordinates remain full-screen values.
    // ======================================

    private void updateBarRegion(
            NativeImage source,
            FishingBarDetector.Bar bar
    ) {

        if (cachedFrame == null) {
            return;
        }

        int padX = Math.max(
                8,
                source.getWidth() / 100
        );

        int padY = Math.max(
                4,
                source.getHeight() / 100
        );

        int minX = Math.max(
                0,
                bar.left() - padX
        );

        int maxX = Math.min(
                source.getWidth() - 1,
                bar.right() + padX
        );

        int minY = Math.max(
                0,
                bar.top() - padY
        );

        int maxY = Math.min(
                source.getHeight() - 1,
                bar.bottom() + padY
        );

        for (int y = minY; y <= maxY; y++) {

            for (int x = minX; x <= maxX; x++) {

                cachedFrame.setRGB(
                        x,
                        y,
                        source.getPixel(x, y)
                );
            }
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
                        BASE_SAFE_MARGIN
                                * frameWidth / 1920.0f
                )
        );

        for (GreenZoneDetector.GreenZone zone : zones) {

            int zoneWidth =
                    zone.endX() - zone.startX() + 1;

            int safeMargin =
                    Math.min(margin, zoneWidth / 4);

            if (predictedX >= zone.startX() + safeMargin
                    && predictedX <= zone.endX() - safeMargin) {
                return true;
            }
        }

        return false;
    }

    private String formatMs(
            long nanoseconds,
            int samples
    ) {

        if (samples <= 0) {
            return "0.00";
        }

        return String.format(
                Locale.ROOT,
                "%.2f",
                nanoseconds / 1_000_000.0 / samples
        );
    }
}


