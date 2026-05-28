package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.config.BridgeConfig;
import com.example.arsmanabridge.util.RecursionGuard;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets = "com.hollingsworth.arsnouveau.common.capability.ManaCap",
    remap = false
)
public class MixinManaCapability {

    @Shadow
    LivingEntity entity;

    private static float bridge$getIronsMax(Player player) {
        try {
            return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
        } catch (Exception e) {
            return 100f;
        }
    }

    private Player bridge$getPlayer() {
        return entity instanceof Player player ? player : null;
    }

    @Inject(
        method = "getCurrentMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bridge$getCurrentMana(
            CallbackInfoReturnable<Double> cir
    ) {
        if (!BridgeConfig.isIssPrimary()) return;

        Player player = bridge$getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;

        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;
            cir.setReturnValue((double) data.getMana());
        } finally {
            RecursionGuard.exit();
        }
    }

    @Inject(
        method = "getMaxMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bridge$getMaxMana(
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!BridgeConfig.isIssPrimary()) return;

        Player player = bridge$getPlayer();
        if (player == null || player.level().isClientSide()) return;

        cir.setReturnValue((int) bridge$getIronsMax(player));
    }

    @Inject(
        method = "setMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bridge$setMana(
            double amount,
            CallbackInfoReturnable<Double> cir
    ) {
        if (!BridgeConfig.isIssPrimary()) return;

        Player player = bridge$getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;

        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;

            data.setMana(Math.min((float) amount, bridge$getIronsMax(player)));
            cir.setReturnValue((double) data.getMana());
        } finally {
            RecursionGuard.exit();
        }
    }

    @Inject(
        method = "addMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bridge$addMana(
            double amount,
            CallbackInfoReturnable<Double> cir
    ) {
        if (!BridgeConfig.isIssPrimary()) return;

        Player player = bridge$getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;

        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;

            data.setMana(Math.min(
                    data.getMana() + (float) amount,
                    bridge$getIronsMax(player)
            ));

            cir.setReturnValue((double) data.getMana());
        } finally {
            RecursionGuard.exit();
        }
    }

    @Inject(
        method = "removeMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bridge$removeMana(
            double amount,
            CallbackInfoReturnable<Double> cir
    ) {
        if (!BridgeConfig.isIssPrimary()) return;

        Player player = bridge$getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;

        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) return;

            data.setMana(Math.max(
                    0f,
                    data.getMana() - (float) amount
            ));

            cir.setReturnValue((double) data.getMana());
        } finally {
            RecursionGuard.exit();
        }
    }

    @Inject(
        method = "setMaxMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bridge$setMaxMana(
            int max,
            CallbackInfo ci
    ) {
        if (!BridgeConfig.isIssPrimary()) return;

        // Iron's MAX_MANA attribute is authoritative
        ci.cancel();
    }
}