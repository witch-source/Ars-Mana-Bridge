package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.util.CasterContextHolder;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the casting player and spell into a ThreadLocal before canCast()
 * runs, so MixinSpellStatsPotency can read them during getPotency().
 * Cleared after canCast() returns.
 */
@Mixin(value = SpellResolver.class, remap = false)
public class MixinSpellResolverContext {

    @Shadow
    public SpellContext spellContext;

    @Inject(method = "canCast", at = @At("HEAD"), remap = false, require = 0)
    private void bridge$captureContext(LivingEntity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) return;
        try {
            java.lang.reflect.Field spellField =
                    SpellContext.class.getDeclaredField("spell");
            spellField.setAccessible(true);
            com.hollingsworth.arsnouveau.api.spell.Spell spell =
                    (com.hollingsworth.arsnouveau.api.spell.Spell)
                    spellField.get(spellContext);
            CasterContextHolder.set(player, spell);
        } catch (Exception ignored) {}
    }

    @Inject(method = "canCast", at = @At("RETURN"), remap = false, require = 0)
    private void bridge$clearContext(LivingEntity entity,
            CallbackInfoReturnable<Boolean> cir) {
        CasterContextHolder.clear();
    }
}
