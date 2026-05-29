package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.util.RecursionGuard;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets = "com.hollingsworth.arsnouveau.common.capability.ManaCap",
    remap = false
)
public class MixinManaCapability {

    @Shadow
    LivingEntity entity;

    private Player bridge$getPlayer() {
        return entity instanceof Player player ? player : null;
    }

    /**
     * Ars PRIMARY:
     * Ars owns mana.
     * Mirror Ars mana into Iron MagicData.
     */

    @Inject(
        method = "setMana",
        at = @At("TAIL"),
        remap = false
    )
    private void bridge$mirrorSetMana(
            double amount,
            CallbackInfoReturnable<Double> cir
    ) {
        bridge$syncToIrons();
    }

    @Inject(
        method = "addMana",
        at = @At("TAIL"),
        remap = false
    )
    private void bridge$mirrorAddMana(
            double amount,
            CallbackInfoReturnable<Double> cir
    ) {
        bridge$syncToIrons();
    }

    @Inject(
        method = "removeMana",
        at = @At("TAIL"),
        remap = false
    )
    private void bridge$mirrorRemoveMana(
            double amount,
            CallbackInfoReturnable<Double> cir
    ) {
        bridge$syncToIrons();
    }

    private void bridge$syncToIrons() {
        Player player = bridge$getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;

        try {
            var self =
                    (com.hollingsworth.arsnouveau.api.mana.IManaCap)(Object)this;

            MagicData data =
                    MagicData.getPlayerMagicData(player);

            if (data == null) return;

            float mana =
                    (float) self.getCurrentMana();

            float max =
                    self.getMaxMana();

            // Mirror Ars -> Iron
            data.setMana(
                    Math.min(mana, max)
            );

        } finally {
            RecursionGuard.exit();
        }
    }
}