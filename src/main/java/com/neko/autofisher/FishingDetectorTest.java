
package com.neko.autofisher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class FishingDetectorTest {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            args = new String[] {
                    "test-images/first.png",
                    "test-images/second.png"
            };
        }

        for (String path : args) {

            BufferedImage image =
                    ImageIO.read(new File(path));

            if (image == null) {
                System.out.println("Cannot read: " + path);
                continue;
            }

            List<GreenZoneDetector.GreenZone> zones =
                    GreenZoneDetector.detect(image);

            FishPositionDetector.FishPosition fish =
                    FishPositionDetector.detect(image);

            System.out.println("----------------------");
            System.out.println("Screenshot: " + path);

            System.out.println(
                    "Resolution: " + image.getWidth()
                            + "x" + image.getHeight()
            );

            System.out.println("Green zones: " + zones.size());

            for (var zone : zones) {
                System.out.println(
                        "Green X: "
                                + zone.startX() + " - "
                                + zone.endX()
                );
            }

            if (fish == null) {
                System.out.println("Fish NOT detected");
                continue;
            }

            System.out.println(
                    "Fish center X: " + fish.centerX()
            );

            System.out.println(
                    "Dark pixel score: " + fish.darkPixels()
            );

            boolean insideGreen =
                    GreenZoneDetector.isInsideAnyZone(
                            fish.centerX(), zones
                    );

            System.out.println(
                    "Inside green: " + insideGreen
            );

            System.out.println(
                    "Decision: " +
                            (insideGreen ? "RIGHT CLICK" : "WAIT")
            );
        }
    }
}
