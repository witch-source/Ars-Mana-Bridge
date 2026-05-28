package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.config.BridgeConfig;
import com.hollingsworth.arsnouveau.client.gui.GuiManaHUD;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the Ars Nouveau mana HUD in ISS_PRIMARY mode.
 */
@Mixin(value = GuiManaHUD.class, remap = false)
public class MixinArsManaHud {

    @Inject(
        method = "renderOverlay",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void bridge$hideArsManaBar(
            GuiGraphics guiGraphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        ci.cancel();
    }
}