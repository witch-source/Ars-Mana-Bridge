package com.example.arsmanabridge.util;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.world.entity.player.Player;

/**
 * Thread-local holder for the currently casting player and spell.
 * Set in MixinSpellResolverContext.canCast() HEAD,
 * read in MixinSpellStatsPotency.getPotency() RETURN,
 * cleared in MixinSpellResolverContext.canCast() RETURN.
 */
public final class CasterContextHolder {

    private static final ThreadLocal<Player> CASTER = new ThreadLocal<>();
    private static final ThreadLocal<Spell> ACTIVE_SPELL = new ThreadLocal<>();

    private CasterContextHolder() {}

    public static void set(Player player, Spell spell) {
        CASTER.set(player);
        ACTIVE_SPELL.set(spell);
    }

    public static Player getPlayer() { return CASTER.get(); }
    public static Spell getSpell()   { return ACTIVE_SPELL.get(); }

    public static void clear() {
        CASTER.remove();
        ACTIVE_SPELL.remove();
    }
}
