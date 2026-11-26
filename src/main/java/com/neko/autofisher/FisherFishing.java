
package com.neko.autofisher;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.common.Mod;

@Mod(FisherFishing.MODID)
public class FisherFishing {

    public static final String MODID = "fisherfishing";

    public static final Logger LOGGER = LogUtils.getLogger();

    public FisherFishing(ModContainer container) {
        // Step 2: Register configuration
        container.registerConfig(
                ModConfig.Type.CLIENT,
                FishingConfig.SPEC
        );

        // Step 3: Enable the Mods -> Config button
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                ConfigurationScreen::new
        );
        LOGGER.info("FisherFishing mod loaded!");
    }
}
