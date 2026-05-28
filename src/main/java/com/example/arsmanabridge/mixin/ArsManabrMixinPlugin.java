package com.example.arsmanabridge.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin plugin - intentionally minimal.
 *
 * Previously used Class.forName() in onLoad() to detect Iron's Spellbooks,
 * but that triggered Player and other classes to load during bootstrap,
 * causing MixinTargetAlreadyLoadedException for any other mod that also
 * mixins into Player (PlayerAnimator, VariantsAndVentures, etc.).
 *
 * The Iron's overlay mixin uses `targets = "..."` string form which is
 * already safe at prepare time - no runtime class check needed.
 */
public class ArsManabrMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Intentionally empty - no Class.forName() here.
        // Class.forName() at bootstrap time forces class loading before
        // Minecraft's classloader is ready, breaking other mods' mixins.
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
