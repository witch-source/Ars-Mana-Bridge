package com.example.arsmanabridge.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "arsmanabridge", bus = EventBusSubscriber.Bus.GAME)
public class ManaUnificationHandler {

    // Unique ResourceLocation for our bridge's modifier
    private static final ResourceLocation ARS_BONUS_MODIFIER_ID = 
        ResourceLocation.fromNamespaceAndPath("arsmanabridge", "ars_mana_bonus");

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        // Only run synchronization on the logical server side
        if (player.level().isClientSide()) return;

        // 1. Fetch Ars Nouveau's Mana Attachment
        com.hollingsworth.arsnouveau.api.mana.IMana arsMana = 
            com.hollingsworth.arsnouveau.api.CapabilityRegistry.getMana(player);

        if (arsMana == null) return;

        // 2. Fetch Iron's Spells Max Mana Attribute Instance
        var ironMaxManaAttribute = player.getAttribute(
            net.iron431.irons_spellbooks.registry.AttributeRegistry.MAX_MANA.get()
        );

        if (ironMaxManaAttribute != null) {
            // Calculate how much extra mana Ars Nouveau expects the player to have 
            // from learned Glyphs, Perks, and worn Ars Armor pieces.
            int arsMax = arsMana.getMaxMana();
            int arsBase = 100; // Ars Nouveau default starting base mana
            int arsBonusDelta = arsMax - arsBase;

            // Safely clear the old modifier before applying the updated one
            ironMaxManaAttribute.removeModifier(ARS_BONUS_MODIFIER_ID);
            
            if (arsBonusDelta > 0) {
                AttributeModifier modifier = new AttributeModifier(
                    ARS_BONUS_MODIFIER_ID, 
                    arsBonusDelta, 
                    AttributeModifier.Operation.ADD_VALUE
                );
                ironMaxManaAttribute.addTransientModifier(modifier);
            }
        }

        // 3. Keep current mana values perfectly linked between systems
        float masterMaxMana = (float) player.getAttributeValue(
            net.iron431.irons_spellbooks.registry.AttributeRegistry.MAX_MANA.get()
        );
        
        float masterCurrentMana = net.iron431.irons_spellbooks.capabilities.magic.MagicData.getPlayerMagicData(player).getMana();

        // Push values back down to Ars Nouveau so its spells consume from the shared pool
        arsMana.setMaxMana((int) masterMaxMana);
        arsMana.setMana((int) masterCurrentMana);
    }
}
