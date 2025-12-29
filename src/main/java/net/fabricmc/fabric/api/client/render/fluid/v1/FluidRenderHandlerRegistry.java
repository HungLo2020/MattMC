/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.api.client.render.fluid.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Registry for custom fluid render handlers.
 */
public final class FluidRenderHandlerRegistry {
    public static final FluidRenderHandlerRegistry INSTANCE = new FluidRenderHandlerRegistry();
    private final Map<Fluid, FluidRenderHandler> handlers = new IdentityHashMap<>();
    private final Map<Fluid, FluidRenderHandler> modHandlers = new IdentityHashMap<>();
    private final Map<Block, Boolean> transparencyForOverlay = new IdentityHashMap<>();
    private volatile boolean initialized = false;
    
    private FluidRenderHandlerRegistry() { }
    
    /**
     * Ensures default handlers are registered. Called lazily to avoid class loading issues.
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    // Register default handlers for vanilla fluids
                    handlers.put(Fluids.WATER, WaterRenderHandler.INSTANCE);
                    handlers.put(Fluids.FLOWING_WATER, WaterRenderHandler.INSTANCE);
                    handlers.put(Fluids.LAVA, LavaRenderHandler.INSTANCE);
                    handlers.put(Fluids.FLOWING_LAVA, LavaRenderHandler.INSTANCE);
                    initialized = true;
                }
            }
        }
    }
    
    /**
     * Gets the singleton instance.
     */
    public static FluidRenderHandlerRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Registers a render handler for a fluid.
     */
    public void register(Fluid fluid, FluidRenderHandler handler) {
        ensureInitialized();
        handlers.put(fluid, handler);
        modHandlers.put(fluid, handler);
    }
    
    /**
     * Gets the render handler for a fluid.
     */
    @Nullable
    public FluidRenderHandler get(Fluid fluid) {
        ensureInitialized();
        return handlers.get(fluid);
    }
    
    /**
     * Gets the override handler for a fluid if one exists.
     */
    @Nullable
    public FluidRenderHandler getOverride(Fluid fluid) {
        ensureInitialized();
        return modHandlers.get(fluid);
    }
    
    /**
     * Sets the transparency of a block for fluid overlay rendering.
     */
    public void setBlockTransparency(Block block, boolean transparent) {
        ensureInitialized();
        transparencyForOverlay.put(block, transparent);
    }
    
    /**
     * Checks if a block is transparent for fluid rendering purposes.
     */
    public boolean isBlockTransparent(Block block) {
        ensureInitialized();
        Boolean override = transparencyForOverlay.get(block);
        if (override != null) {
            return override;
        }
        return block instanceof HalfTransparentBlock || block instanceof LeavesBlock;
    }
    
    /**
     * Called when the fluid renderer reloads textures.
     */
    public void onFluidRendererReload(LiquidBlockRenderer renderer, TextureAtlasSprite[] waterSprites, TextureAtlasSprite[] lavaSprites, TextureAtlasSprite waterOverlay) {
        ensureInitialized();
        WaterRenderHandler.INSTANCE.updateSprites(waterSprites, waterOverlay);
        LavaRenderHandler.INSTANCE.updateSprites(lavaSprites);
        
        TextureAtlas texture = Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
        
        for (FluidRenderHandler handler : handlers.values()) {
            handler.reloadTextures(texture);
        }
    }
    
    /**
     * Handler for water rendering.
     */
    private static class WaterRenderHandler implements FluidRenderHandler {
        public static final WaterRenderHandler INSTANCE = new WaterRenderHandler();
        
        /**
         * The water color of the Ocean biome.
         */
        private static final int DEFAULT_WATER_COLOR = 0x3f76e4;
        
        private final TextureAtlasSprite[] sprites = new TextureAtlasSprite[3];
        
        @Override
        public TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            return sprites;
        }
        
        @Override
        public int getFluidColor(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            if (view != null && pos != null) {
                return BiomeColors.getAverageWaterColor(view, pos);
            } else {
                return DEFAULT_WATER_COLOR;
            }
        }
        
        public void updateSprites(TextureAtlasSprite[] waterSprites, TextureAtlasSprite waterOverlay) {
            sprites[0] = waterSprites[0];
            sprites[1] = waterSprites[1];
            sprites[2] = waterOverlay;
        }
    }
    
    /**
     * Handler for lava rendering.
     */
    private static class LavaRenderHandler implements FluidRenderHandler {
        public static final LavaRenderHandler INSTANCE = new LavaRenderHandler();
        
        private TextureAtlasSprite[] sprites;
        
        @Override
        public TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            return sprites;
        }
        
        public void updateSprites(TextureAtlasSprite[] lavaSprites) {
            sprites = lavaSprites;
        }
    }
}
