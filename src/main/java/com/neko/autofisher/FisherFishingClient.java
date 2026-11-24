
package com.neko.autofisher;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

@Mod(value = FisherFishing.MODID, dist = Dist.CLIENT)
public class FisherFishingClient {

    public static final FishingController CONTROLLER =
            new FishingController();

    private static final FishingNativeCapture NATIVE_CAPTURE =
            new FishingNativeCapture();

    private static final FishingScreenCapture SCREEN_CAPTURE =
            new FishingScreenCapture();

    private static final KeyMapping TOGGLE_KEY =
            new KeyMapping(
                    "key.fisherfishing.toggle",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F8,
                    KeyMapping.Category.MISC
            );

    public FisherFishingClient(IEventBus modEventBus) {

        modEventBus.addListener(
                this::registerKeyMappings
        );

        NeoForge.EVENT_BUS.addListener(
                this::onClientTick
        );

        NeoForge.EVENT_BUS.addListener(
                NATIVE_CAPTURE::onRenderFrame
        );

        NeoForge.EVENT_BUS.addListener(
                SCREEN_CAPTURE::onScreenshot
        );

        FisherFishing.LOGGER.info(
                "FisherFishing client initialized!"
        );
    }

    private void registerKeyMappings(
            RegisterKeyMappingsEvent event
    ) {
        event.register(TOGGLE_KEY);
    }

    private void onClientTick(
            ClientTickEvent.Post event
    ) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            CONTROLLER.disable();
            NATIVE_CAPTURE.reset();
            return;
        }

        // Toggle Auto Fisher with F8.
        while (TOGGLE_KEY.consumeClick()) {

            if (mc.gui.screen() != null) {
                continue;
            }

            CONTROLLER.toggle();
            NATIVE_CAPTURE.reset();

            boolean enabled = CONTROLLER.isEnabled();

            mc.player.sendOverlayMessage(
                    Component.literal(
                            enabled
                                    ? "[Auto Fisher] ENABLED"
                                    : "[Auto Fisher] DISABLED"
                    )
            );

            FisherFishing.LOGGER.info(
                    "[Auto Fisher] {}",
                    enabled ? "ON" : "OFF"
            );
        }

        if (!CONTROLLER.isEnabled()) {
            return;
        }

        // Do not automate while a GUI is open.
        if (mc.gui.screen() != null) {
            return;
        }

        // Require a fishing rod in the main hand.
        if (!mc.player.getMainHandItem().is(
                Items.FISHING_ROD
        )) {
            CONTROLLER.disable();
            NATIVE_CAPTURE.reset();

            mc.player.sendOverlayMessage(
                    Component.literal(
                            "[Auto Fisher] Fishing rod required!"
                    )
            );

            return;
        }

        FishingNativeCapture.Observation observation =
                NATIVE_CAPTURE.getObservation();


        CONTROLLER.tick(
                observation.hudVisible(),

                // Enable automatic catch using actual fish overlap.
                observation.insideGreen(),

                observation.hudGone(),

                // Cast
                () -> {
                    NATIVE_CAPTURE.reset();
                    rightClick(mc);
                },

                // Catch
                () -> rightClick(mc)
        );

    }

    private void rightClick(Minecraft mc) {

        if (mc.player == null || mc.gameMode == null) {
            return;
        }

        if (mc.gui.screen() != null) {
            return;
        }

        if (!mc.player.getMainHandItem().is(
                Items.FISHING_ROD
        )) {
            return;
        }

        mc.gameMode.useItem(
                mc.player,
                InteractionHand.MAIN_HAND
        );

        FisherFishing.LOGGER.info(
                "[Auto Fisher] Right-click executed"
        );
    }
}
