package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import com.example.arsmanabridge.util.CasterContextHolder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Injects into Ars Nouveau's SpellStats.getPotency() at RETURN.
 *
 * This is the canonical hook for scaling Ars spell damage — it fires during
 * the spell's damage calculation, not as a post-hoc damage event modifier.
 *
 * Approach from ars_n_spells:
 *   - getPotency() returns an int (amplify stack count effectively)
 *   - We multiply by Iron's SPELL_POWER (generic) and the best matching
 *     elemental attribute based on the first effect glyph's path name
 *   - Return Math.round(original * multiplier)
 *
 * We get the player from SpellContext which is captured in MixinSpellResolverContext.
 */
@Mixin(
    targets = "com.hollingsworth.arsnouveau.api.spell.SpellStats",
    remap = false
)
public class MixinSpellStatsPotency {

    private static final Map<String, Holder<Attribute>> ELEMENT_MAP = new HashMap<>();
    private static volatile boolean elementMapBuilt = false;

    @Inject(
        method = "getPotency",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void bridge$applyIronsSpellPower(CallbackInfoReturnable<Integer> cir) {
        if (!BridgeConfig.ENABLE_SPELL_POWER_BRIDGE.get()) return;

        Player player = CasterContextHolder.getPlayer();
        if (player == null) return;

        Spell spell = CasterContextHolder.getSpell();
        float multiplier = computeMultiplier(player, spell);
        if (multiplier <= 1.0f) return;

        int original = cir.getReturnValue();
        cir.setReturnValue(Math.round(original * multiplier));
    }

    private static float computeMultiplier(Player player, Spell spell) {
        buildElementMapIfNeeded();

        // Generic spell power: Iron's base is 1.0, bonus = value - 1.0
        double generic = 0;
        try {
            generic = Math.max(0, player.getAttributeValue(AttributeRegistry.SPELL_POWER) - 1.0);
        } catch (Exception ignored) {}

        // Elemental bonus from first matching glyph
        double elemental = 0;
        if (BridgeConfig.ENABLE_ELEMENTAL_SPELL_POWER.get() && spell != null
                && !spell.unsafeList().isEmpty()) {
            elemental = getElementalBonus(player, spell);
        }

        double total = 1.0 + generic + elemental;
        float cap = (float) BridgeConfig.SPELL_POWER_CAP.get().doubleValue();
        return (float) Math.min(total, cap);
    }

    private static double getElementalBonus(Player player, Spell spell) {
        for (AbstractSpellPart part : spell.unsafeList()) {
            ResourceLocation reg = part.getRegistryName();
            if (reg == null) continue;
            String path = reg.getPath().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Holder<Attribute>> entry : ELEMENT_MAP.entrySet()) {
                if (path.contains(entry.getKey())) {
                    try {
                        return Math.max(0, player.getAttributeValue(entry.getValue()) - 1.0);
                    } catch (Exception ignored) {}
                }
            }
        }
        return 0;
    }

    private static void buildElementMapIfNeeded() {
        if (elementMapBuilt) return;
        synchronized (ELEMENT_MAP) {
            if (elementMapBuilt) return;
            put("fire",       AttributeRegistry.FIRE_SPELL_POWER);
            put("ice",        AttributeRegistry.ICE_SPELL_POWER);
            put("frost",      AttributeRegistry.ICE_SPELL_POWER);
            put("cold",       AttributeRegistry.ICE_SPELL_POWER);
            put("lightning",  AttributeRegistry.LIGHTNING_SPELL_POWER);
            put("thunder",    AttributeRegistry.LIGHTNING_SPELL_POWER);
            put("shock",      AttributeRegistry.LIGHTNING_SPELL_POWER);
            put("holy",       AttributeRegistry.HOLY_SPELL_POWER);
            put("heal",       AttributeRegistry.HOLY_SPELL_POWER);
            put("light",      AttributeRegistry.HOLY_SPELL_POWER);
            put("ender",      AttributeRegistry.ENDER_SPELL_POWER);
            put("void",       AttributeRegistry.ENDER_SPELL_POWER);
            put("blood",      AttributeRegistry.BLOOD_SPELL_POWER);
            put("life",       AttributeRegistry.BLOOD_SPELL_POWER);
            put("evoc",       AttributeRegistry.EVOCATION_SPELL_POWER);
            put("summon",     AttributeRegistry.EVOCATION_SPELL_POWER);
            // Ars Elemental
            put("wind",       AttributeRegistry.LIGHTNING_SPELL_POWER);
            put("air",        AttributeRegistry.LIGHTNING_SPELL_POWER);
            put("water",      AttributeRegistry.ICE_SPELL_POWER);
            put("aqua",       AttributeRegistry.ICE_SPELL_POWER);
            put("earth",      AttributeRegistry.EVOCATION_SPELL_POWER);
            put("nature",     AttributeRegistry.EVOCATION_SPELL_POWER);
            put("plant",      AttributeRegistry.EVOCATION_SPELL_POWER);
            // Ars Technomancy
            put("arcane",     AttributeRegistry.EVOCATION_SPELL_POWER);
            put("tech",       AttributeRegistry.EVOCATION_SPELL_POWER);
            put("eldritch",   AttributeRegistry.ENDER_SPELL_POWER);
            elementMapBuilt = true;
        }
    }

    private static void put(String key, Holder<Attribute> attr) {
        if (attr != null) ELEMENT_MAP.put(key, attr);
    }
}
