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
            ResourceLocation.fromNamespaceAndPath(
                    "arsmanabridge",
                    "ars_regen_ceiling"
            );

    private static final Map<UUID, Double> ironsMaxCache =
            new ConcurrentHashMap<>();

    private static final Map<UUID, Double> ironsRegenCache =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // MAX MANA
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onMaxManaCalc(MaxManaCalcEvent event) {

        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        try {
            UUID id = player.getUUID();
            double ironsBonus;

            if (ironsMaxCache.containsKey(id)) {
                ironsBonus = ironsMaxCache.get(id);
            } else {

                AttributeInstance ironsAttr =
                        player.getAttribute(AttributeRegistry.MAX_MANA);

                if (ironsAttr == null) return;

                // Remove ceiling so measurement is clean
                ironsAttr.removeModifier(BRIDGE_CEILING_RL);

                double ironsTotal =
                        player.getAttributeValue(AttributeRegistry.MAX_MANA);

                double ironsBase =
                        player.getAttributeBaseValue(AttributeRegistry.MAX_MANA);

                ironsBonus = Math.max(0, ironsTotal - ironsBase);

                ironsMaxCache.put(id, ironsBonus);

                ArsManabridge.LOGGER.debug(
                        "MaxManaCalc FRESH: total={}, base={}, bonus={}",
                        ironsTotal,
                        ironsBase,
                        ironsBonus
                );
            }

            // Convert Iron bonus -> Ars bonus
            if (ironsBonus > 0) {

                double rate =
                        BridgeConfig.IRONS_MAX_MANA_CONVERSION.get();

                int converted =
                        (int) Math.round(ironsBonus * rate);

                event.setMax(event.getMax() + converted);

                ArsManabridge.LOGGER.debug(
                        "MaxManaCalc: +{} -> arsMax={}",
                        converted,
                        event.getMax()
                );
            }

            int finalMax = event.getMax();

            if (player instanceof ServerPlayer sp) {
                sp.getServer().tell(
                        new net.minecraft.server.TickTask(
                                sp.getServer().getTickCount() + 1,
                                () -> syncCeiling(sp, finalMax)
                        )
                );
            }

        } catch (Exception e) {
            ArsManabridge.LOGGER.error(
                    "onMaxManaCalc failed",
                    e
            );
        }
    }

    private static void syncCeiling(ServerPlayer player, int arsMax) {

        if (player.level().isClientSide()) return;

        try {
            AttributeInstance attr =
                    player.getAttribute(AttributeRegistry.MAX_MANA);

            if (attr == null) return;

            attr.removeModifier(BRIDGE_CEILING_RL);

            double ironsNative = attr.getValue();

            double bonus = arsMax - ironsNative;

            if (bonus > 0.5) {
                attr.addTransientModifier(
                        new AttributeModifier(
                                BRIDGE_CEILING_RL,
                                bonus,
                                AttributeModifier.Operation.ADD_VALUE
                        )
                );
            }

            // Clamp Iron mana if Ars max shrank
            float effectiveMax = (float) attr.getValue();

            MagicData data =
                    MagicData.getPlayerMagicData(player);

            if (data != null && data.getMana() > effectiveMax) {
                data.setMana(effectiveMax);
            }

            ArsManabridge.LOGGER.debug(
                    "syncCeiling: arsMax={}, ironsNative={}, bonus={}, final={}",
                    arsMax,
                    ironsNative,
                    Math.max(0, bonus),
                    effectiveMax
            );

        } catch (Exception e) {
            ArsManabridge.LOGGER.error(
                    "syncCeiling failed",
                    e
            );
        }
    }

    // -------------------------------------------------------------------------
    // CACHE INVALIDATION
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        ironsMaxCache.remove(player.getUUID());
        ironsRegenCache.remove(player.getUUID());

        player.getServer().tell(
                new net.minecraft.server.TickTask(
                        player.getServer().getTickCount() + 1,
                        () -> {
                            try {
                                AttributeInstance attr =
                                        player.getAttribute(
                                                AttributeRegistry.MAX_MANA
                                        );

                                if (attr != null) {
                                    attr.removeModifier(
                                            BRIDGE_CEILING_RL
                                    );
                                }

                            } catch (Exception ignored) {
                            }
                        }
                )
        );
    }

    @SubscribeEvent
    public void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event
    ) {

        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        ironsMaxCache.remove(player.getUUID());
        ironsRegenCache.remove(player.getUUID());

        player.getServer().tell(
                new net.minecraft.server.TickTask(
                        player.getServer().getTickCount() + 5,
                        () -> triggerArsRecalc(player)
                )
        );
    }

    @SubscribeEvent
    public void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event
    ) {

        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        ironsMaxCache.remove(player.getUUID());
        ironsRegenCache.remove(player.getUUID());

        player.getServer().tell(
                new net.minecraft.server.TickTask(
                        player.getServer().getTickCount() + 5,
                        () -> triggerArsRecalc(player)
                )
        );
    }

    private static void triggerArsRecalc(
            ServerPlayer player
    ) {

        try {
            var cap = CapabilityRegistry.getMana(player);

            if (cap != null) {
                cap.getMaxMana();
            }

        } catch (Exception e) {
            ArsManabridge.LOGGER.error(
                    "triggerArsRecalc failed",
                    e
            );
        }
    }

    // -------------------------------------------------------------------------
    // REGEN
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onManaRegenCalc(ManaRegenCalcEvent event) {

        if (!(event.getEntity() instanceof Player player))
            return;

        if (player.level().isClientSide())
            return;

        try {

            UUID id = player.getUUID();
            double ironsRegenBonus;

            if (ironsRegenCache.containsKey(id)) {
                ironsRegenBonus =
                        ironsRegenCache.get(id);
            } else {

                double total =
                        player.getAttributeValue(
                                AttributeRegistry.MANA_REGEN
                        );

                double base =
                        player.getAttributeBaseValue(
                                AttributeRegistry.MANA_REGEN
                        );

                ironsRegenBonus =
                        Math.max(0, total - base);

                ironsRegenCache.put(
                        id,
                        ironsRegenBonus
                );
            }

            if (ironsRegenBonus > 0) {

                AttributeInstance maxAttr =
                        player.getAttribute(
                                AttributeRegistry.MAX_MANA
                        );

                double maxMana =
                        maxAttr != null
                                ? maxAttr.getValue()
                                : 100.0;

                double rate =
                        BridgeConfig.IRONS_REGEN_CONVERSION.get();

                double arsFlat =
                        ironsRegenBonus
                                * maxMana
                                * 0.1
                                * rate;

                event.setRegen(
                        event.getRegen() + arsFlat
                );

                ArsManabridge.LOGGER.debug(
                        "ManaRegenCalc: bonus={}%, maxMana={}, arsFlat={}, regen={}",
                        ironsRegenBonus * 100,
                        maxMana,
                        arsFlat,
                        event.getRegen()
                );
            }

        } catch (Exception e) {
            ArsManabridge.LOGGER.error(
                    "onManaRegenCalc failed",
                    e
            );
        }
    }
}