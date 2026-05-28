package com.example.arsmanabridge;

import com.example.arsmanabridge.config.BridgeConfig;
import com.example.arsmanabridge.events.ManaUnificationHandler;
import com.example.arsmanabridge.events.SpellPowerHandler;
import com.example.arsmanabridge.util.SpellPowerHandlerHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ArsManabridge.MOD_ID)
public class ArsManabridge {

    public static final String MOD_ID = "arsmanabridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ArsManabridge(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        SpellPowerHandler spellPowerHandler = new SpellPowerHandler();
        SpellPowerHandlerHolder.set(spellPowerHandler);

        NeoForge.EVENT_BUS.register(new ManaUnificationHandler());
        NeoForge.EVENT_BUS.register(spellPowerHandler);

        LOGGER.info("ArsManabridge initialized. Mode: {}", BridgeConfig.MANA_MODE.get());
    }
}
