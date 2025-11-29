package net.matt.quantize.compat;

import net.matt.quantize.Quantize; // <-- has public static final String MOD_ID = "quantize";
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MissingMappingsHandler {
    @SubscribeEvent
    public static void onMissingMappings(final MissingMappingsEvent e) {
        // Handle ALL missing BLOCKS in our namespace → map to AIR
        for (MissingMappingsEvent.Mapping<Block> m : e.getMappings(ForgeRegistries.Keys.BLOCKS, Quantize.MOD_ID)) {
            m.remap(Blocks.AIR);
        }

        // Handle ALL missing ITEMS in our namespace → ignore (drop them)
        for (MissingMappingsEvent.Mapping<Item> m : e.getMappings(ForgeRegistries.Keys.ITEMS, Quantize.MOD_ID)) {
            m.ignore(); // there's no Items.AIR; ignoring is the correct behavior
        }
    }
}
