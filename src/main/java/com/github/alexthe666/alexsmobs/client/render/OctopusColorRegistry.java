package com.github.alexthe666.alexsmobs.client.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class OctopusColorRegistry {

    public static final BlockState FALLBACK_BLOCK = Blocks.SAND.defaultBlockState();
    public static Object2IntMap<String> TEXTURES_TO_COLOR = new Object2IntOpenHashMap<>();;

    public static int getBlockColor(BlockState stack) {
        String blockName = stack.toString();
        if (TEXTURES_TO_COLOR.containsKey(blockName)) {
            return TEXTURES_TO_COLOR.getInt(blockName);
        } else {
            int colorizer = -1;
            try{
                colorizer = Minecraft.getInstance().getBlockColors().getColor(stack, null, null, 0);
            }catch (Exception e){
                System.err.println("Another mod did not use block colorizers correctly.");
            }
            int color;
            if(colorizer == -1){
                // Fallback to white if no colorizer exists
                // In 1.21, texture pixel access API changed significantly
                // For mimic octopus camouflage, we'll use the block colorizer as primary method
                // If that's not available, we default to white which still looks reasonable
                color = 0XFFFFFF;
            }else{
                color = colorizer;
            }
            TEXTURES_TO_COLOR.put(blockName, color);
            return color;
        }
    }
}
