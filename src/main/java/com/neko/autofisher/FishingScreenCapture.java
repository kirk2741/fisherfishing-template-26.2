
package com.neko.autofisher;

import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.neoforge.client.event.ScreenshotEvent;

import java.awt.image.BufferedImage;
import java.util.List;

public class FishingScreenCapture {

    public void onScreenshot(ScreenshotEvent event) {

        try {
            NativeImage nativeImage = event.getImage();

            if (nativeImage == null) {
                return;
            }

            BufferedImage frame = convertImage(nativeImage);

            // Find all green target zones.
            List<GreenZoneDetector.GreenZone> zones =
                    GreenZoneDetector.detect(frame);

            // Find moving fish position.
            FishPositionDetector.FishPosition fish =
                    FishPositionDetector.detect(frame);

            FisherFishing.LOGGER.debug(
                    "[Auto Fisher] Screenshot: {}x{}",
                    frame.getWidth(),
                    frame.getHeight()
            );

            FisherFishing.LOGGER.debug(
                    "[Auto Fisher] Green zones: {}",
                    zones.size()
            );

            for (GreenZoneDetector.GreenZone zone : zones) {
                FisherFishing.LOGGER.debug(
                        "[Auto Fisher] Green X: {} - {}",
                        zone.startX(),
                        zone.endX()
                );
            }

            if (fish == null) {
                FisherFishing.LOGGER.debug(
                        "[Auto Fisher] Fish NOT detected"
                );
                return;
            }

            boolean insideGreen =
                    GreenZoneDetector.isInsideAnyZone(
                            fish.centerX(),
                            zones
                    );

            FisherFishing.LOGGER.debug(
                    "[Auto Fisher] Fish X: {}",
                    fish.centerX()
            );

            FisherFishing.LOGGER.debug(
                    "[Auto Fisher] Inside green: {}",
                    insideGreen
            );

            FisherFishing.LOGGER.debug(
                    "[Auto Fisher] Decision: {}",
                    insideGreen ? "RIGHT CLICK" : "WAIT"
            );

        } catch (Exception ex) {
            FisherFishing.LOGGER.error(
                    "[Auto Fisher] Screenshot detection failed",
                    ex
            );
        }
    }

    private BufferedImage convertImage(
            NativeImage source
    ) {

        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage result = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result.setRGB(
                        x,
                        y,
                        source.getPixel(x, y)
                );
            }
        }

        return result;
    }
}
