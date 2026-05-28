package com.example.arsmanabridge.events;

import com.example.arsmanabridge.ArsManabridge;
import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ManaUnificationHandler {

    private static final ResourceLocation BRIDGE_MAX_MANA_RL =
            ResourceLocation.fromNamespaceAndPath("arsmanabridge", "ars_max_mana_sync");

    /**
     * PRIMARY HOOK: fires after Ars has fully recalculated max mana.
     * event.getMax() is the final Ars value including all gear/perks/glyphs.
     * Read it directly — the cap hasn't been updated yet at this point.
     */
    @SubscribeEvent
    public void onArsMaxManaCalc(MaxManaCalcEvent event) {
        if (!BridgeConfig.isIssPrimary()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        pushArsMaxToIrons(player, event.getMax());
    }

    /**
     * Fallback for Iron's own gear changes (which don't trigger MaxManaCalcEvent).
     * LivingEquipmentChangeEvent only fires on actual slot changes, not every tick.
     */
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!BridgeConfig.isIssPrimary()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 2,
                () -> syncFromCap(player)
        ));
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 5,
                () -> syncFromCap(player)
        ));
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 5,
                () -> syncFromCap(player)
        ));
    }

    private static void syncFromCap(ServerPlayer player) {
        if (!BridgeConfig.isIssPrimary()) return;
        if (player.level().isClientSide()) return;
        try {
            IManaCap cap = CapabilityRegistry.getMana(player);
            if (cap == null) return;
            pushArsMaxToIrons(player, cap.getMaxMana());
        } catch (Exception e) {
            ArsManabridge.LOGGER.error("syncFromCap failed: {}", e.getMessage());
        }
    }

    /**
     * Sets Iron's MAX_MANA so that its effective total is at least arsMax.
     *
     * Strategy: remove our bridge modifier, then compute Iron's native total
     * (base + Iron's own gear modifiers). If arsMax exceeds that, add a bridge
     * modifier for the difference. If Iron's gear already exceeds arsMax, leave
     * it alone — Iron's gear wins.
     *
     * This avoids:
     * - Stacking: we always remove before adding, and only add once
     * - Overwriting Iron's gear bonuses: we measure native total first
     * - Ars armor not working: arsMax comes from MaxManaCalcEvent which includes Ars gear
     */
    public static void pushArsMaxToIrons(ServerPlayer player, int arsMax) {
        if (player.level().isClientSide()) return;
        try {
            AttributeInstance attr = player.getAttribute(AttributeRegistry.MAX_MANA);
            if (attr == null) return;

            // Step 1: remove our modifier so Iron's native value is clean
            attr.removeModifier(BRIDGE_MAX_MANA_RL);

            // Step 2: measure Iron's total without our modifier (base + Iron's gear)
            double ironsNative = attr.getValue();

            // Step 3: only add a bonus if Ars wants more than Iron's native total
            double bonus = arsMax - ironsNative;
            if (bonus > 0.5) { // 0.5 tolerance to avoid float noise
                attr.addTransientModifier(new AttributeModifier(
                        BRIDGE_MAX_MANA_RL,
                        bonus,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }

            // Step 4: clamp current mana to new effective max
            float effectiveMax = (float) attr.getValue();
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data != null && data.getMana() > effectiveMax) {
                data.setMana(effectiveMax);
            }

            ArsManabridge.LOGGER.debug(
                    "pushArsMaxToIrons: arsMax={}, ironsNative={}, bonus={}, final={}",
                    arsMax, ironsNative, Math.max(0, bonus), effectiveMax);

        } catch (Exception e) {
            ArsManabridge.LOGGER.error("pushArsMaxToIrons failed: {}", e.getMessage());
        }
    }
}
