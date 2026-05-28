package com.example.arsmanabridge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BridgeConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    /**
     * iss_primary = Iron's mana bar shown, Iron's pool is the single source of truth.
     *               Ars spells drain Iron's mana. Ars mana bar hidden.
     * ars_primary = Ars mana bar shown, Ars pool is the single source of truth.
     *               Iron's spells drain Ars mana. Iron's mana bar hidden.
     */
    public static final ModConfigSpec.ConfigValue<String> MANA_MODE;

    /**
     * Multiplier applied when converting Ars mana costs to Iron's pool.
     * Default 1.0 = direct 1:1. Increase to make Ars spells cost more from Iron's pool.
     */
    public static final ModConfigSpec.DoubleValue ARS_TO_IRONS_RATE;

    /**
     * Multiplier applied when converting Iron's mana costs to Ars pool.
     * Default 1.0 = direct 1:1.
     */
    public static final ModConfigSpec.DoubleValue IRONS_TO_ARS_RATE;

    /** Apply Iron's generic SPELL_POWER attribute to Ars spell damage. */
    public static final ModConfigSpec.BooleanValue ENABLE_SPELL_POWER_BRIDGE;

    /**
     * Apply Iron's elemental spell power attributes (FIRE_SPELL_POWER, ICE_SPELL_POWER, etc.)
     * to matching Ars spells. Requires ENABLE_SPELL_POWER_BRIDGE.
     */
    public static final ModConfigSpec.BooleanValue ENABLE_ELEMENTAL_SPELL_POWER;

    /**
     * Max total spell power multiplier from all Iron's attributes combined.
     * Prevents runaway stacking. Default 3.0 = spells can deal up to 3x base damage.
     */
    public static final ModConfigSpec.DoubleValue SPELL_POWER_CAP;

    static {
        BUILDER.comment("ArsManabridge - Ars Nouveau / Iron's Spells 'n Spellbooks integration");

        BUILDER.push("mana");
        MANA_MODE = BUILDER
                .comment(
                    "Which mana system is the authoritative pool.",
                    "  iss_primary - Iron's mana is the single source. Ars spells drain Iron's pool.",
                    "                Iron's mana bar is shown; Ars mana bar is hidden.",
                    "  ars_primary - Ars mana is the single source. Iron's spells drain Ars pool.",
                    "                Ars mana bar is shown; Iron's mana bar is hidden."
                )
                .define("mana_mode", "iss_primary");

        ARS_TO_IRONS_RATE = BUILDER
                .comment("Conversion rate: Ars mana cost -> Iron's pool drain. Default 1.0 (1:1).")
                .defineInRange("ars_to_irons_rate", 1.0, 0.01, 100.0);

        IRONS_TO_ARS_RATE = BUILDER
                .comment("Conversion rate: Iron's mana cost -> Ars pool drain. Default 1.0 (1:1).")
                .defineInRange("irons_to_ars_rate", 1.0, 0.01, 100.0);
        BUILDER.pop();

        BUILDER.push("spell_power");
        ENABLE_SPELL_POWER_BRIDGE = BUILDER
                .comment("Apply Iron's generic SPELL_POWER attribute to Ars spell damage.")
                .define("enable_spell_power_bridge", true);

        ENABLE_ELEMENTAL_SPELL_POWER = BUILDER
                .comment(
                    "Apply Iron's elemental spell power attributes to matching Ars spells.",
                    "Requires enable_spell_power_bridge = true.",
                    "Detects element from first Ars glyph path name.",
                    "Ars Elemental and Ars Technomancy spells are included automatically."
                )
                .define("enable_elemental_spell_power", true);

        SPELL_POWER_CAP = BUILDER
                .comment("Maximum spell power multiplier applied to Ars spells from Iron's attributes.",
                         "1.0 = no bonus, 3.0 = up to triple damage.")
                .defineInRange("spell_power_cap", 3.0, 1.0, 20.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean isIssPrimary() {
        return "iss_primary".equalsIgnoreCase(MANA_MODE.get());
    }

    public static boolean isArsPrimary() {
        return "ars_primary".equalsIgnoreCase(MANA_MODE.get());
    }
}
