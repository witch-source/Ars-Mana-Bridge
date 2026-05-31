package com.example.arsmanabridge.mixin.irons;

import com.example.arsmanabridge.config.BridgeConfig;
import com.example.arsmanabridge.util.RecursionGuard;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "io.redspace.ironsspellbooks.api.magic.MagicData", remap = false)
public class MixinIronsMagicDataMana {

    @Shadow private ServerPlayer serverPlayer;

    private Player bridge$getOwner() { return serverPlayer; }

    /** Redirect getMana() so Iron's spell checks read Ars mana. */
    @Inject(method = "getMana", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
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
        } finally { RecursionGuard.exit(); }
    }

    /**
     * Redirect setMana() so Iron's spell expenditure drains Ars mana.
     * Iron's pattern: setMana(currentMana - cost).
     * We derive cost = currentArs - newAmount and removeMana from Ars.
     */
    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bridge$setMana(float amount, CallbackInfo ci) {
        if (!BridgeConfig.isArsPrimary()) return;
        Player player = bridge$getOwner();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;
        try {
            IManaCap cap = CapabilityRegistry.getMana(player);
            if (cap == null) return;
            float currentArs = (float) cap.getCurrentMana();
            float delta = currentArs - amount;
            if (delta > 0.01f) {
                // Spell cost: drain from Ars
                double rate = BridgeConfig.IRONS_TO_ARS_RATE.get();
                cap.removeMana(delta * rate);
            } else if (delta < -0.01f) {
                // Iron's is setting mana HIGHER than current (e.g. a potion restoring
                // mana by doing setMana(current + amount)). Add the difference to Ars.
                cap.addMana(-delta);
            }
            ci.cancel();
        } catch (Exception ignored) {
        } finally { RecursionGuard.exit(); }
    }

    /**
     * Redirect addMana() — used by mana potions and restoration items.
     * Iron's potions call addMana(amount) directly rather than setMana(current + amount).
     */
    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bridge$addMana(float amount, CallbackInfo ci) {
        if (!BridgeConfig.isArsPrimary()) return;
        Player player = bridge$getOwner();
        if (player == null || player.level().isClientSide()) return;
        if (!RecursionGuard.enter()) return;
        try {
            IManaCap cap = CapabilityRegistry.getMana(player);
            if (cap == null) return;
            if (amount > 0) {
                cap.addMana(amount);
            }
            ci.cancel();
        } catch (Exception ignored) {
        } finally { RecursionGuard.exit(); }
    }
}
