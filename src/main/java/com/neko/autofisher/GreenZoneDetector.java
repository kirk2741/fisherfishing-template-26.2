
package com.neko.autofisher;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class GreenZoneDetector {

    public record GreenZone(int startX, int endX) {}

    private static final int BASE_WIDTH = 1920;

    private static final double MIN_X = 0.12;
    private static final double MAX_X = 0.88;

    private static final double[] SAMPLE_Y = {
            0.40,
            0.43,
            0.47
    };

    private static final int MIN_WIDTH = 5;
    private static final int MAX_GAP = 24;

    // Fish-overlap settings for narrow green zones.
    private static final int FISH_HALF_WIDTH = 16;
    private static final int MINIMUM_OVERLAP = 6;

    public static List<GreenZone> detect(
            BufferedImage image
    ) {

        List<GreenZone> zones = new ArrayList<>();

        if (image == null) {
            return zones;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        int minX = Math.max(
                0,
                (int) (width * MIN_X)
        );

        int maxX = Math.min(
                width - 1,
                (int) (width * MAX_X)
        );

        int minWidth = Math.max(
                2,
                Math.round(
                        MIN_WIDTH * width / (float) BASE_WIDTH
                )
        );

        int maxGap = Math.max(
                1,
                Math.round(
                        MAX_GAP * width / (float) BASE_WIDTH
                )
        );

        // Identify columns with green pixels.
        boolean[] greenColumns = new boolean[width];

        for (double sampleY : SAMPLE_Y) {

            int y = Math.min(
                    height - 1,
                    Math.max(
                            0,
                            (int) (height * sampleY)
                    )
            );

            for (int x = minX; x <= maxX; x++) {

                if (isGreen(image.getRGB(x, y))) {
                    greenColumns[x] = true;
                }
            }
        }

        // Find contiguous green regions.
        List<GreenZone> rawZones = new ArrayList<>();

        int start = -1;

        for (int x = minX; x <= maxX + 1; x++) {

            boolean green =
                    x <= maxX && greenColumns[x];

            if (green && start == -1) {

                start = x;

            } else if (!green && start != -1) {

                int end = x - 1;

                if (end - start + 1 >= minWidth) {

                    rawZones.add(
                            new GreenZone(start, end)
                    );
                }

                start = -1;
            }
        }

        // Merge nearby fragments of the same zone.
        for (GreenZone zone : rawZones) {

            if (!zones.isEmpty()) {

                int lastIndex = zones.size() - 1;
                GreenZone previous = zones.get(lastIndex);

                int gap =
                        zone.startX() - previous.endX() - 1;

                if (gap <= maxGap) {

                    zones.set(
                            lastIndex,
                            new GreenZone(
                                    previous.startX(),
                                    zone.endX()
                            )
                    );

                    continue;
                }
            }

            zones.add(zone);
        }

        return zones;
    }

    private static boolean isGreen(int rgb) {

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        return g >= 150
                && r <= 120
                && b <= 140
                && g > r * 1.5
                && g > b * 1.3;
    }

    // Original center-based detection.
    public static boolean isInsideAnyZone(
            int fishX,
            List<GreenZone> zones
    ) {

        if (zones == null || fishX < 0) {
            return false;
        }

        for (GreenZone zone : zones) {

            if (fishX >= zone.startX()
                    && fishX <= zone.endX()) {

                return true;
            }
        }

        return false;
    }

    // Previous tolerance-based detection.
    public static boolean isInsideAnyZoneWithTolerance(
            int fishX,
            List<GreenZone> zones,
            int imageWidth
    ) {

        if (zones == null || fishX < 0) {
            return false;
        }

        int tolerance = Math.max(
                1,
                Math.round(imageWidth * 12f / BASE_WIDTH)
        );

        for (GreenZone zone : zones) {

            if (fishX >= zone.startX() - tolerance
                    && fishX <= zone.endX() + tolerance) {

                return true;
            }
        }

        return false;
    }

    // NEW: Detect partial fish overlap with green.
    public static boolean isFishOverlappingGreen(
            int fishCenterX,
            List<GreenZone> zones,
            int imageWidth
    ) {

        if (zones == null || fishCenterX < 0) {
            return false;
        }

        int fishHalfWidth = Math.max(
                1,
                Math.round(
                        imageWidth * FISH_HALF_WIDTH
                                / (float) BASE_WIDTH
                )
        );

        int minimumOverlap = Math.max(
                1,
                Math.round(
                        imageWidth * MINIMUM_OVERLAP
                                / (float) BASE_WIDTH
                )
        );

        int fishLeft = fishCenterX - fishHalfWidth;
        int fishRight = fishCenterX + fishHalfWidth;

        for (GreenZone zone : zones) {

            int overlapLeft = Math.max(
                    fishLeft,
                    zone.startX()
            );

            int overlapRight = Math.min(
                    fishRight,
                    zone.endX()
            );

            int overlapWidth = Math.max(
                    0,
                    overlapRight - overlapLeft + 1
            );

            if (overlapWidth >= minimumOverlap) {
                return true;
            }
        }

        return false;
    }
}
