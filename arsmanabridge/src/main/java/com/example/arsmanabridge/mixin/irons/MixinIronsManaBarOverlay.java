package com.example.arsmanabridge.mixin.irons;

import com.example.arsmanabridge.config.BridgeConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides Iron's mana bar when Ars Nouveau is the primary mana source.
 *
 * NOTE: targets the non-obfuscated class name via `targets` string because
 * Iron's Spellbooks is a compileOnly dep and may not be on the classpath at
 * mixin weave time in some environments.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay", remap = false)
public class MixinIronsManaBarOverlay {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void bridge$hideIronsManaBar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BridgeConfig.isArsPrimary()) {
            ci.cancel(); // Ars mana bar handles display
        }
        // ISS_PRIMARY: Iron's bar shows normally, Ars bar suppressed by MixinArsManaHud
    }
}
