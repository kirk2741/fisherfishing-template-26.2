
package com.neko.autofisher;

import java.awt.image.BufferedImage;

public final class FishPositionDetector {

    private FishPositionDetector() {}

    // Keep this record unchanged for compatibility
    // with FishingNativeCapture.java.
    public record FishPosition(
            int centerX,
            int darkPixels
    ) {}

    private static final int BASE_BAR_HEIGHT = 136;

    private static final int WINDOW_RADIUS = 20;
    private static final int MIN_SCORE = 35;

    private static final int COLOR_TOLERANCE = 8;

    // NEW: Exclude decorative bar borders.
    private static final double EDGE_EXCLUSION = 0.025;

    private static final int[][] FISH_COLORS = {
            {135, 103, 105},
            {212, 161, 105},
            {77, 46, 32},
            {174, 136, 76},
            {157, 138, 72},
            {206, 155, 88},
            {139, 82, 40}
    };

    public static FishPosition detect(
            BufferedImage image
    ) {

        FishingBarDetector.Bar bar =
                FishingBarDetector.detect(image);

        return detect(image, bar);
    }

    public static FishPosition detect(
            BufferedImage image,
            FishingBarDetector.Bar bar
    ) {

        if (image == null || bar == null) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        float scale = Math.max(
                0.5f,
                bar.height() / (float) BASE_BAR_HEIGHT
        );

        int radius = Math.max(
                8,
                Math.round(WINDOW_RADIUS * scale)
        );

        int minScore = Math.max(
                15,
                Math.round(MIN_SCORE * scale * scale)
        );

        // NEW: Exclude the outer area where border
        // textures can resemble fish palette colors.
        int edgeExclusion = Math.max(
                12,
                Math.round(
                        bar.width() * (float) EDGE_EXCLUSION
                )
        );

        int minX = Math.max(
                0,
                bar.left() + edgeExclusion
        );

        int maxX = Math.min(
                width - 1,
                bar.right() - edgeExclusion
        );

        int minY = Math.max(
                0,
                bar.top()
        );

        int maxY = Math.min(
                height - 1,
                bar.bottom()
        );

        if (maxX - minX < radius * 2) {
            return null;
        }

        int[] colorColumns = new int[width];

        // Scan only the usable fishing-bar region.
        for (int x = minX; x <= maxX; x++) {

            int matches = 0;

            for (int y = minY; y <= maxY; y++) {

                if (isFishColor(image.getRGB(x, y))) {
                    matches++;
                }
            }

            colorColumns[x] = matches;
        }

        // Sliding-window fish detection.
        int bestX = -1;
        int bestScore = 0;

        int score = 0;

        for (int x = minX;
             x <= minX + radius * 2;
             x++) {

            score += colorColumns[x];
        }

        for (int center = minX + radius;
             center <= maxX - radius;
             center++) {

            if (score > bestScore) {
                bestScore = score;
                bestX = center;
            }

            int leaving = center - radius;
            int entering = center + radius + 1;

            if (entering <= maxX) {

                score -= colorColumns[leaving];
                score += colorColumns[entering];
            }
        }

        if (bestX < 0 || bestScore < minScore) {
            return null;
        }

        // Find the weighted center of matching pixels.
        long weightedX = 0;
        int totalMatches = 0;

        for (int x = bestX - radius;
             x <= bestX + radius;
             x++) {

            int count = colorColumns[x];

            weightedX += (long) x * count;
            totalMatches += count;
        }

        if (totalMatches == 0) {
            return null;
        }

        int centerX = (int) Math.round(
                (double) weightedX / totalMatches
        );

        return new FishPosition(
                centerX,
                bestScore
        );
    }

    private static boolean isFishColor(int rgb) {

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        for (int[] color : FISH_COLORS) {

            if (Math.abs(r - color[0])
                    <= COLOR_TOLERANCE
                    && Math.abs(g - color[1])
                    <= COLOR_TOLERANCE
                    && Math.abs(b - color[2])
                    <= COLOR_TOLERANCE) {

                return true;
            }
        }

        return false;
    }
}
