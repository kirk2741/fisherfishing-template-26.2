
package com.neko.autofisher;

import java.awt.image.BufferedImage;
import java.util.function.IntBinaryOperator;

public final class FishingBarDetector {

    private FishingBarDetector() {}

    public record Bar(
            int left,
            int top,
            int right,
            int bottom
    ) {
        public int width() {
            return right - left + 1;
        }

        public int height() {
            return bottom - top + 1;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }
    }

    public static Bar detect(BufferedImage image) {

        if (image == null) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        return detect(width, height, image::getRGB);
    }

    public static Bar detect(int width, int height, IntBinaryOperator pixelAt) {

        // Broad search area supports different HUD
        // positions and common aspect ratios.
        int minX = (int) (width * 0.08);
        int maxX = (int) (width * 0.92);

        int minY = (int) (height * 0.18);
        int maxY = (int) (height * 0.80);

        int rowThreshold = Math.max(
                30,
                (int) (width * 0.08)
        );

        int[] rowScores = new int[height];

        // Count predominantly red bar pixels.
        for (int y = minY; y <= maxY; y++) {

            int count = 0;

            for (int x = minX; x <= maxX; x += 2) {

                if (isBarRed(pixelAt.applyAsInt(x, y))) {
                    count += 2;
                }
            }

            rowScores[y] = count;
        }

        // Find a substantial consecutive band of rows.
        int bestTop = -1;
        int bestBottom = -1;
        long bestScore = 0;

        int runTop = -1;
        long runScore = 0;

        for (int y = minY; y <= maxY + 1; y++) {

            boolean qualifies =
                    y <= maxY
                            && rowScores[y] >= rowThreshold;

            if (qualifies) {

                if (runTop == -1) {
                    runTop = y;
                    runScore = 0;
                }

                runScore += rowScores[y];

            } else if (runTop != -1) {

                int runBottom = y - 1;

                int runHeight =
                        runBottom - runTop + 1;

                if (runHeight >= Math.max(
                        8,
                        (int) (height * 0.018)
                ) && runScore > bestScore) {

                    bestScore = runScore;
                    bestTop = runTop;
                    bestBottom = runBottom;
                }

                runTop = -1;
            }
        }

        if (bestTop < 0) {
            return null;
        }

        // Find the strongest red row within that band.
        int bestY = bestTop;

        for (int y = bestTop; y <= bestBottom; y++) {

            if (rowScores[y] > rowScores[bestY]) {
                bestY = y;
            }
        }

        // Determine horizontal bounds using red pixels
        // from several nearby rows.
        int sampleRadius = Math.max(
                2,
                (bestBottom - bestTop) / 10
        );

        int[] columnVotes = new int[width];

        for (int y = Math.max(bestTop, bestY - sampleRadius);
             y <= Math.min(bestBottom, bestY + sampleRadius);
             y += 2) {

            for (int x = minX; x <= maxX; x++) {

                if (isBarRed(pixelAt.applyAsInt(x, y))) {
                    columnVotes[x]++;
                }
            }
        }


        int left = -1;
        int right = -1;

// Ignore small isolated groups of red pixels,
// including elements outside the fishing HUD.
        int maxGroupGap = Math.max(
                8,
                Math.round(width * 0.025f)
        );

        int minGroupPixels = Math.max(
                20,
                Math.round(width * 0.05f)
        );

        int groupStart = -1;
        int groupEnd = -1;
        int groupPixels = 0;

        for (int x = minX; x <= maxX + 1; x++) {

            boolean red =
                    x <= maxX && columnVotes[x] >= 2;

            if (red) {

                if (groupStart == -1) {
                    groupStart = x;
                }

                if (groupEnd >= 0
                        && x - groupEnd > maxGroupGap) {

                    if (groupPixels >= minGroupPixels) {
                        if (left == -1) {
                            left = groupStart;
                        }
                        right = groupEnd;
                    }

                    groupStart = x;
                    groupPixels = 0;
                }

                groupEnd = x;
                groupPixels++;
            }

            if ((!red && groupEnd >= 0
                    && x - groupEnd > maxGroupGap)
                    || x == maxX + 1) {

                if (groupPixels >= minGroupPixels) {
                    if (left == -1) {
                        left = groupStart;
                    }
                    right = groupEnd;
                }

                groupStart = -1;
                groupEnd = -1;
                groupPixels = 0;
            }
        }


        if (left < 0 || right <= left) {
            return null;
        }

        // Reject unusually small detected regions.
        if (right - left < width * 0.12) {
            return null;
        }

        // Padding includes green targets near the edges
        // and fish pixels extending outside the red fill.
        int padX = Math.max(
                12,
                Math.round(width * 0.06f)
        );

        int padY = Math.max(
                4,
                Math.round(height * 0.008f)
        );

        return new Bar(
                Math.max(0, left - padX),
                Math.max(0, bestTop - padY),
                Math.min(width - 1, right + padX),
                Math.min(height - 1, bestBottom + padY)
        );
    }

    private static boolean isBarRed(int rgb) {

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        return r >= 150
                && r > g * 1.35
                && r > b * 1.40
                && g < 185
                && b < 150;
    }
}
