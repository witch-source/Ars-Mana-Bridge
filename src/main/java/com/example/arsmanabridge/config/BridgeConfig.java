package com.example.arsmanabridge.config;
import net.neoforged.neoforge.common.ModConfigSpec;
public class BridgeConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    // Mana bridge
    public static final ModConfigSpec.DoubleValue IRONS_TO_ARS_RATE;
    public static final ModConfigSpec.DoubleValue IRONS_MAX_MANA_CONVERSION;
    public static final ModConfigSpec.DoubleValue IRONS_REGEN_CONVERSION;
    public static final ModConfigSpec.DoubleValue ARS_REGEN_MULTIPLIER;
    // Spell power bridge
    public static final ModConfigSpec.BooleanValue ENABLE_SPELL_POWER_BRIDGE;
    public static final ModConfigSpec.BooleanValue ENABLE_ELEMENTAL_SPELL_POWER;
    public static final ModConfigSpec.DoubleValue SPELL_POWER_CAP;
    // Ars → Iron spell power bridge
    public static final ModConfigSpec.BooleanValue ENABLE_ARS_TO_IRONS_POWER;
    public static final ModConfigSpec.DoubleValue ARS_TO_IRONS_POWER_SCALE;
    // Ars Elemental armor → Iron spell power
    public static final ModConfigSpec.BooleanValue ENABLE_ARS_ELEMENTAL_ARMOR_BONUS;
    public static final ModConfigSpec.DoubleValue ARS_ELEMENTAL_ARMOR_BONUS_PER_PIECE;

    static {
        BUILDER.comment(
                "ArsManaBridge",
                "Ars Nouveau is the authoritative mana system.",
                "Iron's mana, mana regen, and max mana bonuses are bridged into Ars."
        );
        BUILDER.push("mana");
        IRONS_TO_ARS_RATE = BUILDER
                .comment("Iron mana cost -> Ars mana drain.", "1.0 = 1:1.")
                .defineInRange("irons_to_ars_rate", 1.0, 0.01, 100.0);
        IRONS_MAX_MANA_CONVERSION = BUILDER
                .comment("Iron MAX_MANA gear bonus -> Ars max mana.")
                .defineInRange("irons_max_mana_conversion", 1.0, 0.0, 100.0);
        IRONS_REGEN_CONVERSION = BUILDER
                .comment("Iron % mana regen -> Ars flat regen.")
                .defineInRange("irons_regen_conversion", 1.0, 0.0, 100.0);
        ARS_REGEN_MULTIPLIER = BUILDER
                .comment(
                        "Multiplier applied to Ars Nouveau's own base mana regen.",
                        "1.0 = unchanged. 2.0 = double Ars regen. 0.5 = half.",
                        "Applies to all Ars regen sources: base regen, armor bonuses, enchants."
                )
                .defineInRange("ars_regen_multiplier", 1.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.push("spell_power");
        ENABLE_SPELL_POWER_BRIDGE = BUILDER
                .comment("Apply Iron spell power to Ars spell damage.")
                .define("enable_spell_power_bridge", true);
        ENABLE_ELEMENTAL_SPELL_POWER = BUILDER
                .comment("Apply Iron elemental spell power to matching Ars spells.")
                .define("enable_elemental_spell_power", true);
        SPELL_POWER_CAP = BUILDER
                .comment("Maximum spell power multiplier applied to Ars spells.")
                .defineInRange("spell_power_cap", 3.0, 1.0, 20.0);
        BUILDER.pop();

        BUILDER.push("ars_to_irons");
        ENABLE_ARS_TO_IRONS_POWER = BUILDER
                .comment("Allow Ars spell power attributes to buff Iron's spells.")
                .define("enable_ars_to_irons_power", true);
        ARS_TO_IRONS_POWER_SCALE = BUILDER
                .comment(
                        "Conversion from Ars flat spell damage to Iron multiplier.",
                        "0.1 = +10 Ars spell damage becomes +100% Iron spell power."
                )
                .defineInRange("ars_to_irons_power_scale", 0.1, 0.0, 10.0);
        ENABLE_ARS_ELEMENTAL_ARMOR_BONUS = BUILDER
                .comment(
                        "Apply a spell power bonus to Iron's spells per Ars Elemental armor piece worn.",
                        "Detects any item with namespace 'ars_elemental' in armor slots.",
                        "Works without Ars Elemental as a hard dependency — safely skipped if not worn."
                )
                .define("enable_ars_elemental_armor_bonus", true);
        ARS_ELEMENTAL_ARMOR_BONUS_PER_PIECE = BUILDER
                .comment(
                        "Flat spell power multiplier bonus added per Ars Elemental armor piece.",
                        "Default 0.05 = +5% per piece, +20% for a full set.",
                        "Stacks with the generic Ars spell damage conversion above.",
                        "Increase if Ars Elemental armor feels underpowered relative to Iron's gear."
                )
                .defineInRange("ars_elemental_armor_bonus_per_piece", 0.05, 0.0, 1.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean isArsPrimary() {
        return true;
    }
}
