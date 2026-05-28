package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.ArsManabridge;
import com.example.arsmanabridge.config.BridgeConfig;
import com.example.arsmanabridge.util.SpellPowerHandlerHolder;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts Ars spell mana expenditure to drain Iron's pool in ISS_PRIMARY mode.
 *
 * Note: Mixin classes cannot have non-private static methods, so the
 * SpellPowerHandler reference lives in SpellPowerHandlerHolder instead.
 */
@Mixin(value = SpellResolver.class, remap = false)
public class MixinSpellResolverMana {

    @Inject(method = "expendMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void bridge$expendMana(CallbackInfo ci) {
        if (!BridgeConfig.isIssPrimary()) return;

        SpellResolver self = (SpellResolver)(Object)this;
        SpellContext context = self.spellContext;
        if (context == null) return;

        LivingEntity caster = context.getUnwrappedCaster();
        if (!(caster instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (player.isCreative()) { ci.cancel(); return; }

        int arsCost = self.getResolveCost();
        if (arsCost <= 0) { ci.cancel(); return; }

        float rate = (float) BridgeConfig.ARS_TO_IRONS_RATE.get().doubleValue();
        int ironsCost = (int) Math.max(1, Math.round(arsCost * rate));

        MagicData data = MagicData.getPlayerMagicData(player);
        if (data != null) {
            data.setMana(Math.max(0, data.getMana() - ironsCost));
            ArsManabridge.LOGGER.debug("Ars cost {} -> Iron's cost {}. Remaining: {}",
                    arsCost, ironsCost, data.getMana());
        }

        // Stage spell power multiplier via the static holder (not a static method on this mixin)
        var handler = SpellPowerHandlerHolder.get();
        if (BridgeConfig.ENABLE_SPELL_POWER_BRIDGE.get() && handler != null) {
            try {
                java.lang.reflect.Field spellField = SpellContext.class.getDeclaredField("spell");
                spellField.setAccessible(true);
                com.hollingsworth.arsnouveau.api.spell.Spell spell =
                        (com.hollingsworth.arsnouveau.api.spell.Spell) spellField.get(context);
                handler.stageMultiplier(player, spell);
            } catch (Exception e) {
                ArsManabridge.LOGGER.debug("Could not stage spell power: {}", e.getMessage());
            }
        }

        ci.cancel();
    }
}
