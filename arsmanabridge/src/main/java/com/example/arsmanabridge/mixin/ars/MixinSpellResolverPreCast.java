package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In ISS_PRIMARY mode, replaces Ars's canCast check with an Iron's mana check.
 *
 * Without this, Ars would check its own (now-mirrored) mana values and may
 * return true even when Iron's is empty, because the values haven't synced yet
 * in the same tick.
 *
 * We directly check Iron's MagicData against the converted cost.
 */
@Mixin(value = SpellResolver.class, remap = false)
public class MixinSpellResolverPreCast {

    @Inject(method = "canCast", at = @At("HEAD"), cancellable = true, remap = false)
    private void bridge$canCast(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!BridgeConfig.isIssPrimary()) return;
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (player.isCreative()) {
            cir.setReturnValue(true);
            return;
        }

        SpellResolver self = (SpellResolver)(Object)this;
        int arsCost = self.getResolveCost();
        float rate = (float) BridgeConfig.ARS_TO_IRONS_RATE.get().doubleValue();
        int ironsCost = (int) Math.max(1, Math.round(arsCost * rate));

        MagicData data = MagicData.getPlayerMagicData(player);
        if (data == null) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(data.getMana() >= ironsCost);
    }
}
