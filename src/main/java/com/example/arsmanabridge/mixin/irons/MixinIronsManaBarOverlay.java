package com.example.arsmanabridge.mixin.irons;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets = "io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay",
    remap = false
)
public class MixinIronsManaBarOverlay {

    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bridge$hideIronsManaBar(
            GuiGraphics guiGraphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        ci.cancel();
    }
}