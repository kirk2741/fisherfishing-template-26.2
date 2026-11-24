
package com.neko.autofisher;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.List;

public class FishingLiveCapture {

    // Capture at approximately 10 FPS
    private static final long CAPTURE_INTERVAL_NS =
            100_000_000L;

    // Log detection results every 500 ms
    private static final long LOG_INTERVAL_NS =
            500_000_000L;

    // Retry Robot initialization every 5 seconds
    private static final long ROBOT_RETRY_NS =
            5_000_000_000L;

    private Robot robot;
    private GraphicsDevice robotDevice;

    private long lastCapture = 0;
    private long lastLog = 0;
    private long lastDebugLog = 0;
    private long lastRobotAttempt = 0;

    private boolean errorLogged = false;

    public FishingLiveCapture() {
        // Initialize Robot only after Minecraft is running.
    }

    public void onRenderFrame(RenderFrameEvent.Post event) {

        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        long now = System.nanoTime();

        // Debug output once per second
        if (now - lastDebugLog >= 1_000_000_000L) {

            lastDebugLog = now;

            FisherFishing.LOGGER.info(
                    "[Live DEBUG] Frame event received"
            );

            FisherFishing.LOGGER.info(
                    "[Live DEBUG] Robot: {}, Player: {}, Level: {}, Enabled: {}, Screen open: {}",
                    robot != null,
                    mc.player != null,
                    mc.level != null,
                    FisherFishingClient.CONTROLLER.isEnabled(),
                    mc.gui.screen() != null
            );

            FisherFishing.LOGGER.info(
                    "[Live DEBUG] Window X={}, Y={}, Width={}, Height={}",
                    mc.getWindow().getX(),
                    mc.getWindow().getY(),
                    mc.getWindow().getWidth(),
                    mc.getWindow().getHeight()
            );
        }

        // Check whether Minecraft is ready
        if (mc.player == null
                || mc.level == null
                || mc.gui.screen() != null
                || !FisherFishingClient.CONTROLLER.isEnabled()) {
            return;
        }

        int windowX = mc.getWindow().getX();
        int windowY = mc.getWindow().getY();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        Rectangle captureArea = new Rectangle(
                windowX,
                windowY,
                width,
                height
        );

        // Find the monitor containing Minecraft
        GraphicsDevice targetDevice =
                findMonitor(captureArea);

        if (targetDevice == null) {

            if (!errorLogged) {
                FisherFishing.LOGGER.warn(
                        "[Live DEBUG] No monitor contains Minecraft window: {}",
                        captureArea
                );

                errorLogged = true;
            }

            return;
        }

        // Initialize Robot lazily, with retry protection
        if (robot == null || robotDevice != targetDevice) {

            if (now - lastRobotAttempt < ROBOT_RETRY_NS) {
                return;
            }

            lastRobotAttempt = now;

            try {
                FisherFishing.LOGGER.info(
                        "[Live DEBUG] Initializing Robot..."
                );

                FisherFishing.LOGGER.info(
                        "[Live DEBUG] Java headless: {}",
                        GraphicsEnvironment.isHeadless()
                );

                robot = new Robot(targetDevice);
                robot.setAutoDelay(0);

                robotDevice = targetDevice;

                errorLogged = false;

                FisherFishing.LOGGER.info(
                        "[Live DEBUG] Robot initialized successfully!"
                );

                FisherFishing.LOGGER.info(
                        "[Live DEBUG] Monitor: {}",
                        targetDevice.getIDstring()
                );

            } catch (AWTException | SecurityException e) {

                robot = null;
                robotDevice = null;

                FisherFishing.LOGGER.error(
                        "[Live DEBUG] Robot initialization failed",
                        e
                );

                return;
            }
        }

        // Limit capture rate
        if (now - lastCapture < CAPTURE_INTERVAL_NS) {
            return;
        }

        lastCapture = now;

        try {

            BufferedImage frame =
                    robot.createScreenCapture(captureArea);

            if (frame == null) {
                return;
            }

            // Detect green target areas
            List<GreenZoneDetector.GreenZone> zones =
                    GreenZoneDetector.detect(frame);

            // Detect moving fish
            FishPositionDetector.FishPosition fish =
                    FishPositionDetector.detect(frame);

            if (now - lastLog >= LOG_INTERVAL_NS) {

                lastLog = now;

                FisherFishing.LOGGER.info(
                        "[Live DEBUG] Capture successful: {}x{}",
                        frame.getWidth(),
                        frame.getHeight()
                );

                if (!zones.isEmpty() && fish != null) {

                    boolean insideGreen =
                            GreenZoneDetector.isInsideAnyZone(
                                    fish.centerX(),
                                    zones
                            );

                    FisherFishing.LOGGER.info(
                            "[Live] Fish X: {}, Green zones: {}, Inside: {}",
                            fish.centerX(),
                            zones,
                            insideGreen
                    );

                    FisherFishing.LOGGER.info(
                            "[Live] Decision: {}",
                            insideGreen
                                    ? "RIGHT CLICK"
                                    : "WAIT"
                    );

                } else {

                    FisherFishing.LOGGER.info(
                            "[Live] Fishing HUD not confirmed"
                    );
                }
            }

            errorLogged = false;

        } catch (Exception e) {

            if (!errorLogged) {

                FisherFishing.LOGGER.error(
                        "[Live DEBUG] Screen capture failed",
                        e
                );

                errorLogged = true;
            }
        }
    }

    private GraphicsDevice findMonitor(Rectangle windowArea) {

        GraphicsDevice[] devices =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getScreenDevices();

        GraphicsDevice bestDevice = null;
        long bestArea = 0;

        for (GraphicsDevice device : devices) {

            Rectangle bounds =
                    device.getDefaultConfiguration()
                            .getBounds();

            Rectangle intersection =
                    bounds.intersection(windowArea);

            if (intersection.isEmpty()) {
                continue;
            }

            long intersectionArea =
                    (long) intersection.width
                            * intersection.height;

            if (intersectionArea > bestArea) {
                bestArea = intersectionArea;
                bestDevice = device;
            }
        }

        return bestDevice;
    }
}
