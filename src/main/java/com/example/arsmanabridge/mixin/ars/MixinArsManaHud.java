package com.example.arsmanabridge.mixin.ars;

import com.hollingsworth.arsnouveau.client.gui.GuiManaHUD;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Ars HUD enabled.
 */
@Mixin(value = GuiManaHUD.class, remap = false)
public class MixinArsManaHud {
}