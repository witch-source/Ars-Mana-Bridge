package com.example.arsmanabridge.mixin.ars;

import com.example.arsmanabridge.util.CasterContextHolder;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(value = SpellResolver.class, remap = false)
public class MixinSpellResolverContext {

    @Shadow public SpellContext spellContext;

    // Cache the reflected field so we don't pay getDeclaredField() on every cast
    private static volatile Field SPELL_FIELD = null;

    private static Field getSpellField() {
        if (SPELL_FIELD == null) {
            synchronized (MixinSpellResolverContext.class) {
                if (SPELL_FIELD == null) {
                    try {
                        Field f = SpellContext.class.getDeclaredField("spell");
                        f.setAccessible(true);
                        SPELL_FIELD = f;
                    } catch (Exception ignored) {}
                }
            }
        }
        return SPELL_FIELD;
    }

    @Inject(method = "canCast", at = @At("HEAD"), remap = false, require = 0)
    private void bridge$captureContext(LivingEntity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) return;
        try {
            Field f = getSpellField();
            if (f == null) return;
            Spell spell = (Spell) f.get(spellContext);
            CasterContextHolder.set(player, spell);
        } catch (Exception ignored) {}
    }

    @Inject(method = "canCast", at = @At("RETURN"), remap = false, require = 0)
    private void bridge$clearContext(LivingEntity entity,
            CallbackInfoReturnable<Boolean> cir) {
        CasterContextHolder.clear();
    }
}
