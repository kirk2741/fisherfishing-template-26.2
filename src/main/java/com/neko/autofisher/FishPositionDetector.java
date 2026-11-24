
package com.neko.autofisher;

import java.awt.image.BufferedImage;

public class FishPositionDetector {

    public record FishPosition(
            int centerX,
            int darkPixels
    ) {}

    private static final int BASE_WIDTH = 1920;

    private static final double MIN_X = 0.12;
    private static final double MAX_X = 0.88;

    private static final double MIN_Y = 0.37;
    private static final double MAX_Y = 0.46;

    private static final int WINDOW_RADIUS = 20;
    private static final int COLOR_TOLERANCE = 8;
    private static final int MIN_SCORE = 35;

    // Distinctive colors sampled from the fish sprite.
    // Values are RGB.
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
        if (image == null) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        int minX = Math.max(
                0, (int) (width * MIN_X)
        );

        int maxX = Math.min(
                width - 1, (int) (width * MAX_X)
        );

        int minY = Math.max(
                0, (int) (height * MIN_Y)
        );

        int maxY = Math.min(
                height - 1, (int) (height * MAX_Y)
        );

        int radius = Math.max(
                5,
                Math.round(
                        WINDOW_RADIUS *
                                width / (float) BASE_WIDTH
                )
        );

        int[] colorColumns = new int[width];

        for (int x = minX; x <= maxX; x++) {

            int matches = 0;

            for (int y = minY; y <= maxY; y++) {
                if (isFishColor(image.getRGB(x, y))) {
                    matches++;
                }
            }

            colorColumns[x] = matches;
        }

        int bestX = -1;
        int bestScore = 0;

        // Sliding-window matching.
        for (int x = minX + radius;
             x <= maxX - radius;
             x++) {

            int score = 0;

            for (int dx = -radius;
                 dx <= radius;
                 dx++) {

                score += colorColumns[x + dx];
            }

            if (score > bestScore) {
                bestScore = score;
                bestX = x;
            }
        }

        int minScore = Math.max(
                10,
                Math.round(
                        MIN_SCORE *
                                width / (float) BASE_WIDTH
                )
        );

        if (bestX == -1 || bestScore < minScore) {
            return null;
        }

        // Weighted center of matching fish colors.
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

        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        for (int[] color : FISH_COLORS) {

            if (Math.abs(red - color[0]) <= COLOR_TOLERANCE
                    && Math.abs(green - color[1]) <= COLOR_TOLERANCE
                    && Math.abs(blue - color[2]) <= COLOR_TOLERANCE) {

                return true;
            }
        }

        return false;
    }
}
