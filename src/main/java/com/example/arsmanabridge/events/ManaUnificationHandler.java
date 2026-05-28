package com.example.arsmanabridge.events;

import com.example.arsmanabridge.ArsManabridge;
import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ManaUnificationHandler {

    // ResourceLocation key for our bridge modifier (1.21.1 uses ResourceLocation, not UUID)
    private static final ResourceLocation BRIDGE_MAX_MANA_RL =
            ResourceLocation.fromNamespaceAndPath("arsmanabridge", "ars_max_mana_sync");

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncMaxMana(player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncMaxMana(player);
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 1,
                () -> syncMaxMana(player)
        ));
    }

    public static void syncMaxMana(ServerPlayer player) {
        if (!BridgeConfig.isIssPrimary() && !BridgeConfig.isArsPrimary()) return;
        Level level = player.level();
        if (level.isClientSide()) return;

        if (BridgeConfig.isIssPrimary()) {
            syncArsMaxToIrons(player);
        }
    }

    private static void syncArsMaxToIrons(ServerPlayer player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap == null) return;

            int arsMax = manaCap.getMaxMana();

            AttributeInstance ironsMaxMana = player.getAttribute(AttributeRegistry.MAX_MANA);
            if (ironsMaxMana == null) return;

            // Remove our old modifier (1.21.1 API: removeModifier takes ResourceLocation)
            ironsMaxMana.removeModifier(BRIDGE_MAX_MANA_RL);

            double ironsBase = ironsMaxMana.getBaseValue();
            double bonus = Math.max(0, arsMax - ironsBase);
            if (bonus > 0) {
                // 1.21.1 AttributeModifier: (ResourceLocation, double, Operation)
                ironsMaxMana.addTransientModifier(new AttributeModifier(
                        BRIDGE_MAX_MANA_RL,
                        bonus,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }

            ArsManabridge.LOGGER.debug(
                    "Synced Ars max mana {} -> Iron's MAX_MANA (base={}, bonus={})",
                    arsMax, ironsBase, bonus);

        } catch (Exception e) {
            ArsManabridge.LOGGER.error("Failed to sync max mana for {}: {}",
                    player.getName().getString(), e.getMessage());
        }
    }
}
