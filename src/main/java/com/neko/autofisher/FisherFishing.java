
package com.neko.autofisher;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.fml.common.Mod;

@Mod(FisherFishing.MODID)
public class FisherFishing {

    public static final String MODID = "fisherfishing";

    public static final Logger LOGGER = LogUtils.getLogger();

    public FisherFishing() {
        LOGGER.info("FisherFishing mod loaded!");
    }
}
