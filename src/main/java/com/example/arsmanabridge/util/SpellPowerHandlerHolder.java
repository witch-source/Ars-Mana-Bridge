package com.example.arsmanabridge.util;

import com.example.arsmanabridge.events.SpellPowerHandler;

/**
 * Static holder so MixinSpellResolverMana can reference the SpellPowerHandler
 * without needing a non-private static method on the mixin class itself.
 * (Mixin rules: non-private statics are forbidden on mixin classes.)
 */
public final class SpellPowerHandlerHolder {

    private static SpellPowerHandler instance;

    private SpellPowerHandlerHolder() {}

    public static void set(SpellPowerHandler handler) {
        instance = handler;
    }

    public static SpellPowerHandler get() {
        return instance;
    }
}
