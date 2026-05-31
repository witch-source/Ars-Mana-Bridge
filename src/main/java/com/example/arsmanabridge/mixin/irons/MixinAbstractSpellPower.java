package com.example.arsmanabridge.mixin.irons;

import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
public class MixinAbstractSpellPower {

    // Iron's school type IDs (from irons_spellbooks registry)
    private static final String NS = "irons_spellbooks";
    private static final ResourceLocation SCHOOL_FIRE       = ResourceLocation.fromNamespaceAndPath(NS, "fire");
    private static final ResourceLocation SCHOOL_ICE        = ResourceLocation.fromNamespaceAndPath(NS, "ice");
    private static final ResourceLocation SCHOOL_LIGHTNING  = ResourceLocation.fromNamespaceAndPath(NS, "lightning");
    private static final ResourceLocation SCHOOL_HOLY       = ResourceLocation.fromNamespaceAndPath(NS, "holy");
    private static final ResourceLocation SCHOOL_ENDER      = ResourceLocation.fromNamespaceAndPath(NS, "ender");
    private static final ResourceLocation SCHOOL_BLOOD      = ResourceLocation.fromNamespaceAndPath(NS, "blood");
    private static final ResourceLocation SCHOOL_EVOCATION  = ResourceLocation.fromNamespaceAndPath(NS, "evocation");
    private static final ResourceLocation SCHOOL_NATURE     = ResourceLocation.fromNamespaceAndPath(NS, "nature");
    private static final ResourceLocation SCHOOL_ELDRITCH   = ResourceLocation.fromNamespaceAndPath(NS, "eldritch");

    @Inject(
        method = "getSpellPower",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void bridge$applyArsSpellPower(
            int spellLevel,
            Entity sourceEntity,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!(sourceEntity instanceof LivingEntity living)) return;

        float original = cir.getReturnValue();
        float multiplier = 1.0f;

        // --- Generic Ars thread spell damage -> all Iron's spells ---
        if (BridgeConfig.ENABLE_ARS_TO_IRONS_POWER.get()) {
            try {
                double arsSpellPower = living.getAttributeValue(PerkAttributes.SPELL_DAMAGE_BONUS);
                if (arsSpellPower > 0) {
                    multiplier += (float)(arsSpellPower * BridgeConfig.ARS_TO_IRONS_POWER_SCALE.get());
                }
            } catch (Exception ignored) {}
        }

        // --- Ars Elemental armor -> Iron's spells, element-matched only ---
        if (BridgeConfig.ENABLE_ARS_ELEMENTAL_ARMOR_BONUS.get()) {
            double bonusPerPiece = BridgeConfig.ARS_ELEMENTAL_ARMOR_BONUS_PER_PIECE.get();
            if (bonusPerPiece > 0) {
                SchoolType spellSchool = ((AbstractSpell)(Object)this).getSchoolType();
                ResourceLocation spellSchoolId = spellSchool != null ? spellSchool.getId() : null;

                if (spellSchoolId != null) {
                    int matchingPieces = 0;
                    for (ItemStack stack : living.getArmorSlots()) {
                        if (stack.isEmpty()) continue;
                        ResourceLocation itemId = stack.getItemHolder().unwrapKey()
                                .map(k -> k.location()).orElse(null);
                        if (itemId == null || !"ars_elemental".equals(itemId.getNamespace())) continue;

                        ResourceLocation armorSchoolId = getArmorSchoolId(itemId.getPath());
                        if (armorSchoolId != null && armorSchoolId.equals(spellSchoolId)) {
                            matchingPieces++;
                        }
                    }
                    if (matchingPieces > 0) {
                        multiplier += (float)(matchingPieces * bonusPerPiece);
                    }
                }
            }
        }

        float cap = (float) BridgeConfig.SPELL_POWER_CAP.get().doubleValue();
        multiplier = Math.min(multiplier, cap);
        if (multiplier > 1.0f) {
            cir.setReturnValue(original * multiplier);
        }
    }

    /**
     * Map Ars Elemental armor item path keywords to Iron's school ResourceLocation IDs.
     *   pyromancer  -> irons_spellbooks:fire
     *   aquamancer  -> irons_spellbooks:ice   (water closest to ice/cold)
     *   aethermancer-> irons_spellbooks:lightning (air closest to lightning)
     *   geomancer   -> irons_spellbooks:evocation (earth closest to summoning/evocation)
     */
    private static ResourceLocation getArmorSchoolId(String itemPath) {
        if (itemPath.contains("pyro")   || itemPath.contains("fire"))  return SCHOOL_FIRE;
        if (itemPath.contains("aqua")   || itemPath.contains("water")) return SCHOOL_ICE;
        if (itemPath.contains("aether") || itemPath.contains("air")
                                        || itemPath.contains("wind"))  return SCHOOL_LIGHTNING;
        if (itemPath.contains("geo")    || itemPath.contains("earth")
                                        || itemPath.contains("nature"))return SCHOOL_EVOCATION;
        return null;
    }
}
