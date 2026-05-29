package com.example.arsmanabridge.mixin.irons;

import com.example.arsmanabridge.config.BridgeConfig;
import com.example.arsmanabridge.util.RecursionGuard;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In ARS_PRIMARY mode, redirects Iron's MagicData mana reads/writes to Ars cap.
 *
 * MagicData is an instance that belongs to a player — it has no Player param
 * on its methods. We need to get the player from the static getPlayerMagicData
 * lookup, but since we're injecting into the instance we need to find the owner.
 *
 * Iron's MagicData has a serverPlayer field we access via the static lookup
 * in reverse: we use getPlayerMagicData to find which player owns this instance
 * by checking the serverPlayer field via the known static accessor.
 *
 * Simpler approach: Shadow the serverPlayer field directly.
 */
@Mixin(
    targets = "io.redspace.ironsspellbooks.api.magic.MagicData",
    remap = false
)
public class MixinIronsMagicDataMana {

    // MagicData stores a reference to the owning ServerPlayer
    // Field name confirmed from Iron's source structure
    @org.spongepowered.asm.mixin.Shadow
    private ServerPlayer serverPlayer;

    private Player bridge$getOwner() {
        return serverPlayer;
    }

    /**
     * Redirect getMana() so Iron's "enough mana?" check reads the Ars pool.
     * MagicData.getMana() takes no parameters — it's an instance method.
     */
    @Inject(
        method = "getMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void bridge$getMana(CallbackInfoReturnable<Float> cir) {
        if (!BridgeConfig.isArsPrimary()) return;
        Player player = bridge$getOwner();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;
        try {
            IManaCap cap = CapabilityRegistry.getMana(player);
            if (cap == null) return;
            cir.setReturnValue((float) cap.getCurrentMana());
        } catch (Exception ignored) {
        } finally {
            RecursionGuard.exit();
        }
    }

    /**
     * Redirect setMana() so Iron's spell expenditure drains the Ars pool.
     * Iron's pattern: setMana(currentMana - cost).
     * We derive cost = currentArs - newAmount and removeMana from Ars.
     */
    @Inject(
        method = "setMana",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void bridge$setMana(float amount, CallbackInfo ci) {
        if (!BridgeConfig.isArsPrimary()) return;
        Player player = bridge$getOwner();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;
        try {
            IManaCap cap = CapabilityRegistry.getMana(player);
            if (cap == null) return;
            float currentArs = (float) cap.getCurrentMana();
            float ironsSpent = currentArs - amount;
            if (ironsSpent > 0) {
                double rate = BridgeConfig.IRONS_TO_ARS_RATE.get();
                cap.removeMana(ironsSpent * rate);
            }
            ci.cancel();
        } catch (Exception ignored) {
        } finally {
            RecursionGuard.exit();
        }
    }
}
