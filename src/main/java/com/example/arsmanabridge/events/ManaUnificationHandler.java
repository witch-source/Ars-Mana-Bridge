package com.example.arsmanabridge.events;

import com.example.arsmanabridge.ArsManabridge;
import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ManaUnificationHandler {

    private static final ResourceLocation BRIDGE_CEILING_RL =
            ResourceLocation.fromNamespaceAndPath("arsmanabridge", "ars_regen_ceiling");

    // Cache of last CONFIRMED full max mana value (post all bonuses)
    // Only updated when we're confident the value is complete
    private static final Map<UUID, Integer> confirmedMaxCache = new ConcurrentHashMap<>();

    // Cache of Iron's gear bonus (invalidated on equipment change)
    private static final Map<UUID, Double> ironsMaxCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> ironsRegenCache = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // MAX MANA
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onMaxManaCalc(MaxManaCalcEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        try {
            UUID id = player.getUUID();

            // Inject Iron's gear bonus into Ars event
            double ironsBonus;
            if (ironsMaxCache.containsKey(id)) {
                ironsBonus = ironsMaxCache.get(id);
            } else {
                AttributeInstance ironsAttr = player.getAttribute(AttributeRegistry.MAX_MANA);
                if (ironsAttr == null) return;
                ironsAttr.removeModifier(BRIDGE_CEILING_RL);
                double ironsTotal = player.getAttributeValue(AttributeRegistry.MAX_MANA);
                double ironsBase  = player.getAttributeBaseValue(AttributeRegistry.MAX_MANA);
                ironsBonus = Math.max(0, ironsTotal - ironsBase);
                ironsMaxCache.put(id, ironsBonus);
            }

            if (ironsBonus > 0) {
                double rate = BridgeConfig.IRONS_MAX_MANA_CONVERSION.get();
                int converted = (int) Math.round(ironsBonus * rate);
                event.setMax(event.getMax() + converted);
            }

            int finalMax = event.getMax();

            // KEY FIX: Only update ceiling if finalMax is at least as large as
            // what we previously confirmed. This prevents a partial recalc
            // (e.g. hotbar switch firing the event before armor bonuses are
            // included) from clamping the player's current mana down to 100.
            int prev = confirmedMaxCache.getOrDefault(id, 0);
            if (finalMax >= prev) {
                confirmedMaxCache.put(id, finalMax);
                if (player instanceof ServerPlayer sp) {
                    sp.getServer().tell(new net.minecraft.server.TickTask(
                            sp.getServer().getTickCount() + 1,
                            () -> syncCeiling(sp, finalMax, false)
                    ));
                }
            }
            // If finalMax < prev, a partial event fired — ignore it entirely

        } catch (Exception e) {
            ArsManabridge.LOGGER.error("onMaxManaCalc failed", e);
        }
    }

    /**
     * Sync the ceiling modifier on Iron's attribute.
     *
     * @param clampDown if true, also clamp current mana down to effectiveMax.
     *                  Only pass true on equipment REMOVE (armor taken off).
     *                  Never on general recalc events — those can fire mid-calculation.
     */
    private static void syncCeiling(ServerPlayer player, int arsMax, boolean clampDown) {
        if (player.level().isClientSide()) return;
        try {
            AttributeInstance attr = player.getAttribute(AttributeRegistry.MAX_MANA);
            if (attr == null) return;

            attr.removeModifier(BRIDGE_CEILING_RL);
            double ironsNative = attr.getValue();
            double bonus = arsMax - ironsNative;

            if (bonus > 0.5) {
                attr.addTransientModifier(new AttributeModifier(
                        BRIDGE_CEILING_RL,
                        bonus,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }

            // Only clamp mana down when armor is intentionally removed
            if (clampDown) {
                float effectiveMax = (float) attr.getValue();
                MagicData data = MagicData.getPlayerMagicData(player);
                if (data != null && data.getMana() > effectiveMax) {
                    data.setMana(effectiveMax);
                }
            }

        } catch (Exception e) {
            ArsManabridge.LOGGER.error("syncCeiling failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // EQUIPMENT CHANGE
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        UUID id = player.getUUID();
        ironsMaxCache.remove(id);
        ironsRegenCache.remove(id);

        // Check if a piece was removed (new stack is empty, old stack wasn't)
        boolean armorRemoved = event.getTo().isEmpty() && !event.getFrom().isEmpty();

        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 2,
                () -> {
                    try {
                        // Trigger Ars to recalculate its max mana with new gear
                        // This fires MaxManaCalcEvent which handles the ceiling update
                        var cap = CapabilityRegistry.getMana(player);
                        if (cap == null) return;
                        int newMax = cap.getMaxMana();

                        if (armorRemoved) {
                            // Clear confirmed cache so we accept the lower value
                            confirmedMaxCache.remove(id);
                            syncCeiling(player, newMax, true); // clamp current mana down
                        }
                    } catch (Exception e) {
                        ArsManabridge.LOGGER.error("equipmentChange sync failed", e);
                    }
                }
        ));
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearCaches(player.getUUID());
        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 5,
                () -> triggerArsRecalc(player)
        ));
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearCaches(player.getUUID());
        player.getServer().tell(new net.minecraft.server.TickTask(
                player.getServer().getTickCount() + 5,
                () -> triggerArsRecalc(player)
        ));
    }

    private static void clearCaches(UUID id) {
        ironsMaxCache.remove(id);
        ironsRegenCache.remove(id);
        confirmedMaxCache.remove(id);
    }

    private static void triggerArsRecalc(ServerPlayer player) {
        try {
            var cap = CapabilityRegistry.getMana(player);
            if (cap != null) cap.getMaxMana();
        } catch (Exception e) {
            ArsManabridge.LOGGER.error("triggerArsRecalc failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // REGEN
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onManaRegenCalc(ManaRegenCalcEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        try {
            UUID id = player.getUUID();
            double ironsRegenBonus;

            if (ironsRegenCache.containsKey(id)) {
                ironsRegenBonus = ironsRegenCache.get(id);
            } else {
                double total = player.getAttributeValue(AttributeRegistry.MANA_REGEN);
                double base  = player.getAttributeBaseValue(AttributeRegistry.MANA_REGEN);
                ironsRegenBonus = Math.max(0, total - base);
                ironsRegenCache.put(id, ironsRegenBonus);
            }

            if (ironsRegenBonus > 0) {
                AttributeInstance maxAttr = player.getAttribute(AttributeRegistry.MAX_MANA);
                double maxMana = maxAttr != null ? maxAttr.getValue() : 100.0;
                double rate    = BridgeConfig.IRONS_REGEN_CONVERSION.get();
                double arsFlat = ironsRegenBonus * maxMana * 0.1 * rate;
                event.setRegen(event.getRegen() + arsFlat);
            }

            // Apply Ars regen multiplier to the total (base + armor + Iron conversion)
            double arsRegenMult = BridgeConfig.ARS_REGEN_MULTIPLIER.get();
            if (arsRegenMult != 1.0) {
                event.setRegen(event.getRegen() * arsRegenMult);
            }

        } catch (Exception e) {
            ArsManabridge.LOGGER.error("onManaRegenCalc failed", e);
        }
    }
}
