/*
 * Copyright (C) 2024 MattMC
 *
 * This file is part of MattMC.
 *
 * MattMC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.minecraft.client.renderer;

import net.minecraft.client.Minecraft;
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
 * Native Minecraft registry for custom fluid render handlers.
 * This replaces the Fabric API FluidRenderHandlerRegistry with a first-class Minecraft API.
 * 
 * <p>Part of Step 3 of the deep integration plan - consolidating Fabric API stubs
 * into native Minecraft systems.</p>
 */
public final class FluidRendererRegistry {
    private static final FluidRendererRegistry INSTANCE = new FluidRendererRegistry();
    
    private final Map<Fluid, FluidRenderHandler> handlers = new IdentityHashMap<>();
    private final Map<Fluid, FluidRenderHandler> modHandlers = new IdentityHashMap<>();
    private final Map<Block, Boolean> transparencyForOverlay = new IdentityHashMap<>();
    private volatile boolean initialized = false;
    
    private FluidRendererRegistry() { }
    
    /**
     * Gets the singleton instance.
     */
    public static FluidRendererRegistry getInstance() {
        return INSTANCE;
    }
    
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
     * Registers a render handler for a fluid.
     * 
     * @param fluid The fluid to register a handler for
     * @param handler The render handler
     */
    public void register(Fluid fluid, FluidRenderHandler handler) {
        if (fluid == null || handler == null) {
            throw new IllegalArgumentException("Fluid and handler cannot be null");
        }
        ensureInitialized();
        handlers.put(fluid, handler);
        modHandlers.put(fluid, handler);
    }
    
    /**
     * Gets the render handler for a fluid.
     * 
     * @param fluid The fluid
     * @return The render handler, or null if none is registered
     */
    @Nullable
    public FluidRenderHandler get(Fluid fluid) {
        ensureInitialized();
        return handlers.get(fluid);
    }
    
    /**
     * Gets the override handler for a fluid if one exists.
     * 
     * @param fluid The fluid
     * @return The override handler, or null if none exists
     */
    @Nullable
    public FluidRenderHandler getOverride(Fluid fluid) {
        ensureInitialized();
        return modHandlers.get(fluid);
    }
    
    /**
     * Sets the transparency of a block for fluid overlay rendering.
     * 
     * @param block The block
     * @param transparent Whether the block is transparent
     */
    public void setBlockTransparency(Block block, boolean transparent) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        ensureInitialized();
        transparencyForOverlay.put(block, transparent);
    }
    
    /**
     * Checks if a block is transparent for fluid rendering purposes.
     * 
     * @param block The block to check
     * @return True if the block is transparent for fluid rendering
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
     * This updates all registered handlers with new texture sprites.
     * 
     * @param renderer The liquid block renderer
     * @param waterSprites The water texture sprites
     * @param lavaSprites The lava texture sprites
     * @param waterOverlay The water overlay sprite
     */
    public void onFluidRendererReload(LiquidBlockRenderer renderer, TextureAtlasSprite[] waterSprites, 
            TextureAtlasSprite[] lavaSprites, TextureAtlasSprite waterOverlay) {
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
     * Interface for fluid render handlers.
     */
    public interface FluidRenderHandler {
        /**
         * Gets the texture sprites for a fluid at a given position.
         * 
         * @param view The block view
         * @param pos The block position
         * @param state The fluid state
         * @return Array of texture sprites
         */
        TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state);
        
        /**
         * Gets the color tint for a fluid at a given position.
         * 
         * @param view The block view
         * @param pos The block position
         * @param state The fluid state
         * @return The color tint as an ARGB integer
         */
        default int getFluidColor(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            return -1; // White
        }
        
        /**
         * Called when textures are reloaded.
         * 
         * @param textureAtlas The texture atlas
         */
        default void reloadTextures(TextureAtlas textureAtlas) {
            // No-op by default
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
        
        void updateSprites(TextureAtlasSprite[] waterSprites, TextureAtlasSprite waterOverlay) {
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
        
        void updateSprites(TextureAtlasSprite[] lavaSprites) {
            sprites = lavaSprites;
        }
    }
}
