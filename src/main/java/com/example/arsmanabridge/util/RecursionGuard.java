package com.example.arsmanabridge.util;

/**
 * Thread-local flag that prevents re-entrant mixin calls.
 *
 * When MixinManaCapability intercepts getCurrentMana() and calls
 * IronsBridge.getMana(), Iron's getMana() may itself call back through
 * some Ars path. Without this guard that creates an infinite loop.
 *
 * Usage:
 *   if (RecursionGuard.enter()) {
 *       try { ... } finally { RecursionGuard.exit(); }
 *   }
 */
public final class RecursionGuard {

    private static final ThreadLocal<Boolean> ACTIVE =
            ThreadLocal.withInitial(() -> false);

    private RecursionGuard() {}

    /** Returns true if we successfully entered (not already active). */
    public static boolean enter() {
        if (ACTIVE.get()) return false;
        ACTIVE.set(true);
        return true;
    }

    public static void exit() {
        ACTIVE.set(false);
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }
}
