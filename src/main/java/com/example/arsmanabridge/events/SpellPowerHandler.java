package com.example.arsmanabridge.events;

import com.example.arsmanabridge.ArsManabridge;
import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies Iron's spell power attributes to Ars Nouveau spell damage.
 * Uses LivingIncomingDamageEvent (renamed from LivingHurtEvent in NeoForge 1.21.1).
 */
public class SpellPowerHandler {

    private final ConcurrentHashMap<UUID, StagedMultiplier> staged = new ConcurrentHashMap<>();

    private static final Map<String, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>>
            ELEMENT_MAP = new HashMap<>();
    private static volatile boolean elementMapBuilt = false;

    private static final int STAGE_WINDOW_TICKS = 60;
    private int currentTick = 0;

    public void stageMultiplier(Player player, Spell spell) {
        if (!BridgeConfig.ENABLE_SPELL_POWER_BRIDGE.get()) return;
        float multiplier = computeMultiplier(player, spell);
        if (multiplier <= 1.0f) return;
        staged.put(player.getUUID(), new StagedMultiplier(multiplier, currentTick + STAGE_WINDOW_TICKS));
        ArsManabridge.LOGGER.debug("Staged spell power x{} for {}", multiplier, player.getName().getString());
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        currentTick++;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player)) return;
        if (!isMagicDamage(source)) return;

        StagedMultiplier staged = this.staged.remove(player.getUUID());
        if (staged == null) return;
        if (currentTick > staged.expireTick) return;

        float cap = (float) BridgeConfig.SPELL_POWER_CAP.get().doubleValue();
        float multiplier = Math.min(staged.multiplier, cap);
        event.setAmount(event.getAmount() * multiplier);

        ArsManabridge.LOGGER.debug("Applied spell power x{}", multiplier);
    }

    private boolean isMagicDamage(DamageSource source) {
        ResourceLocation typeKey = source.typeHolder().unwrapKey()
                .map(k -> k.location()).orElse(null);
        if (typeKey == null) return false;
        String path = typeKey.getNamespace() + ":" + typeKey.getPath();
        return path.contains("ars_nouveau") || path.contains("magic") || path.contains("spell");
    }

    public static float computeMultiplier(Player player, Spell spell) {
        if (!BridgeConfig.ENABLE_SPELL_POWER_BRIDGE.get()) return 1.0f;
        buildElementMapIfNeeded();

        double generic = player.getAttributeValue(AttributeRegistry.SPELL_POWER);
        double genericBonus = Math.max(0, generic - 1.0);

        double elementBonus = 0.0;
        if (BridgeConfig.ENABLE_ELEMENTAL_SPELL_POWER.get()) {
            elementBonus = getElementalBonus(player, spell);
        }

        double total = 1.0 + genericBonus + elementBonus;
        float cap = (float) BridgeConfig.SPELL_POWER_CAP.get().doubleValue();
        return (float) Math.min(total, cap);
    }

    private static double getElementalBonus(Player player, Spell spell) {
        if (spell == null || spell.unsafeList().isEmpty()) return 0.0;
        for (AbstractSpellPart part : spell.unsafeList()) {
            ResourceLocation regName = part.getRegistryName();
            if (regName == null) continue;
            String path = regName.getPath().toLowerCase(java.util.Locale.ROOT);
            for (Map.Entry<String, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>> entry
                    : ELEMENT_MAP.entrySet()) {
                if (path.contains(entry.getKey())) {
                    try {
                        double val = player.getAttributeValue(entry.getValue());
                        return Math.max(0, val - 1.0);
                    } catch (Exception ignored) {}
                }
            }
        }
        return 0.0;
    }

    private static void buildElementMapIfNeeded() {
        if (elementMapBuilt) return;
        synchronized (ELEMENT_MAP) {
            if (elementMapBuilt) return;
            putIfExists("fire",      AttributeRegistry.FIRE_SPELL_POWER);
            putIfExists("ice",       AttributeRegistry.ICE_SPELL_POWER);
            putIfExists("frost",     AttributeRegistry.ICE_SPELL_POWER);
            putIfExists("cold",      AttributeRegistry.ICE_SPELL_POWER);
            putIfExists("lightning", AttributeRegistry.LIGHTNING_SPELL_POWER);
            putIfExists("thunder",   AttributeRegistry.LIGHTNING_SPELL_POWER);
            putIfExists("shock",     AttributeRegistry.LIGHTNING_SPELL_POWER);
            putIfExists("holy",      AttributeRegistry.HOLY_SPELL_POWER);
            putIfExists("heal",      AttributeRegistry.HOLY_SPELL_POWER);
            putIfExists("light",     AttributeRegistry.HOLY_SPELL_POWER);
            putIfExists("ender",     AttributeRegistry.ENDER_SPELL_POWER);
            putIfExists("void",      AttributeRegistry.ENDER_SPELL_POWER);
            putIfExists("blood",     AttributeRegistry.BLOOD_SPELL_POWER);
            putIfExists("life",      AttributeRegistry.BLOOD_SPELL_POWER);
            putIfExists("evoc",      AttributeRegistry.EVOCATION_SPELL_POWER);
            putIfExists("summon",    AttributeRegistry.EVOCATION_SPELL_POWER);
            // Ars Elemental
            putIfExists("wind",      AttributeRegistry.LIGHTNING_SPELL_POWER);
            putIfExists("air",       AttributeRegistry.LIGHTNING_SPELL_POWER);
            putIfExists("water",     AttributeRegistry.ICE_SPELL_POWER);
            putIfExists("aqua",      AttributeRegistry.ICE_SPELL_POWER);
            putIfExists("earth",     AttributeRegistry.EVOCATION_SPELL_POWER);
            putIfExists("nature",    AttributeRegistry.EVOCATION_SPELL_POWER);
            putIfExists("plant",     AttributeRegistry.EVOCATION_SPELL_POWER);
            // Ars Technomancy
            putIfExists("arcane",    AttributeRegistry.EVOCATION_SPELL_POWER);
            putIfExists("tech",      AttributeRegistry.EVOCATION_SPELL_POWER);
            putIfExists("eldritch",  AttributeRegistry.ENDER_SPELL_POWER);
            elementMapBuilt = true;
        }
    }

    private static void putIfExists(String keyword,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        if (attr != null) ELEMENT_MAP.put(keyword, attr);
    }

    private static class StagedMultiplier {
        final float multiplier;
        final int expireTick;
        StagedMultiplier(float m, int e) { multiplier = m; expireTick = e; }
    }
}
