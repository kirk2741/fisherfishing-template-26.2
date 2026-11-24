
package com.neko.autofisher;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class GreenZoneDetector {

    private GreenZoneDetector() {}

    public record GreenZone(int startX, int endX) {}

    private static final int BASE_WIDTH = 1920;

    private static final int FISH_HALF_WIDTH = 16;
    private static final int MINIMUM_OVERLAP = 6;

    public static List<GreenZone> detect(
            BufferedImage image
    ) {
        FishingBarDetector.Bar bar =
                FishingBarDetector.detect(image);

        return detect(image, bar);
    }

    public static List<GreenZone> detect(
            BufferedImage image,
            FishingBarDetector.Bar bar
    ) {

        List<GreenZone> zones = new ArrayList<>();

        if (image == null || bar == null) {
            return zones;
        }

        int width = image.getWidth();

        // Scale thresholds according to the
        // actual detected HUD width.
        float scale = bar.width() / 1350.0f;

        int minWidth = Math.max(
                3,
                Math.round(5 * scale)
        );

        int maxGap = Math.max(
                4,
                Math.round(24 * scale)
        );

        boolean[] greenColumns = new boolean[width];

        // Scan rows relative to the fishing bar,
        // not the entire screen height.
        double[] rowPositions = {
                0.25,
                0.40,
                0.50,
                0.60,
                0.75
        };

        for (double position : rowPositions) {

            int y = bar.top()
                    + (int) Math.round(
                    (bar.height() - 1) * position
            );

            y = Math.max(
                    0,
                    Math.min(image.getHeight() - 1, y)
            );

            for (int x = bar.left();
                 x <= bar.right();
                 x++) {

                if (isGreen(image.getRGB(x, y))) {
                    greenColumns[x] = true;
                }
            }
        }

        List<GreenZone> fragments = new ArrayList<>();

        int start = -1;

        for (int x = bar.left();
             x <= bar.right() + 1;
             x++) {

            boolean green =
                    x <= bar.right()
                            && greenColumns[x];

            if (green && start == -1) {
                start = x;
            }

            if (!green && start != -1) {

                int end = x - 1;

                if (end - start + 1 >= minWidth) {
                    fragments.add(
                            new GreenZone(start, end)
                    );
                }

                start = -1;
            }
        }

        // Merge green fragments separated by
        // the moving fish sprite.
        for (GreenZone zone : fragments) {

            if (!zones.isEmpty()) {

                int lastIndex = zones.size() - 1;

                GreenZone previous =
                        zones.get(lastIndex);

                int gap =
                        zone.startX()
                                - previous.endX() - 1;

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
                Math.round(
                        imageWidth * 12f / BASE_WIDTH
                )
        );

        for (GreenZone zone : zones) {

            if (fishX >= zone.startX() - tolerance
                    && fishX <= zone.endX() + tolerance) {
                return true;
            }
        }

        return false;
    }

    // Preserve the partial-overlap behavior that
    // successfully caught narrow edge zones.
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
