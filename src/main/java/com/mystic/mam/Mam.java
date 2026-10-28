package com.mystic.mam;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Mam.MODID)
public class Mam {
    public static final String MODID = "mam";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mam(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(SaveRequestPayload::register);
    }
}
