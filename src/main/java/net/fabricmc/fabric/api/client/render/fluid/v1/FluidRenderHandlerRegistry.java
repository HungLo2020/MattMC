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

import net.minecraft.client.renderer.FluidRendererRegistry;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Registry for custom fluid render handlers.
 * 
 * @deprecated Use {@link FluidRendererRegistry} instead. This is a compatibility layer that
 * delegates to the native Minecraft API. Part of Step 3 of the deep integration plan.
 */
@Deprecated
public final class FluidRenderHandlerRegistry {
    public static final FluidRenderHandlerRegistry INSTANCE = new FluidRenderHandlerRegistry();
    
    private FluidRenderHandlerRegistry() { }
    
    /**
     * Gets the singleton instance.
     * 
     * @deprecated Use {@link FluidRendererRegistry#getInstance()} instead.
     */
    @Deprecated
    public static FluidRenderHandlerRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Registers a render handler for a fluid.
     * 
     * @deprecated Use {@link FluidRendererRegistry#register(Fluid, FluidRendererRegistry.FluidRenderHandler)} instead.
     */
    @Deprecated
    public void register(Fluid fluid, FluidRenderHandler handler) {
        // Wrap the Fabric API handler to work with the native API
        FluidRendererRegistry.getInstance().register(fluid, new FluidRenderHandlerAdapter(handler));
    }
    
    /**
     * Gets the render handler for a fluid.
     * 
     * @deprecated Use {@link FluidRendererRegistry#get(Fluid)} instead.
     */
    @Deprecated
    @Nullable
    public FluidRenderHandler get(Fluid fluid) {
        FluidRendererRegistry.FluidRenderHandler nativeHandler = FluidRendererRegistry.getInstance().get(fluid);
        if (nativeHandler instanceof FluidRenderHandlerAdapter) {
            return ((FluidRenderHandlerAdapter) nativeHandler).fabricHandler;
        }
        // Wrap native handler for compatibility
        return nativeHandler != null ? new NativeHandlerWrapper(nativeHandler) : null;
    }
    
    /**
     * Gets the override handler for a fluid if one exists.
     * 
     * @deprecated Use {@link FluidRendererRegistry#getOverride(Fluid)} instead.
     */
    @Deprecated
    @Nullable
    public FluidRenderHandler getOverride(Fluid fluid) {
        FluidRendererRegistry.FluidRenderHandler nativeHandler = FluidRendererRegistry.getInstance().getOverride(fluid);
        if (nativeHandler instanceof FluidRenderHandlerAdapter) {
            return ((FluidRenderHandlerAdapter) nativeHandler).fabricHandler;
        }
        return nativeHandler != null ? new NativeHandlerWrapper(nativeHandler) : null;
    }
    
    /**
     * Sets the transparency of a block for fluid overlay rendering.
     * 
     * @deprecated Use {@link FluidRendererRegistry#setBlockTransparency(Block, boolean)} instead.
     */
    @Deprecated
    public void setBlockTransparency(Block block, boolean transparent) {
        FluidRendererRegistry.getInstance().setBlockTransparency(block, transparent);
    }
    
    /**
     * Checks if a block is transparent for fluid rendering purposes.
     * 
     * @deprecated Use {@link FluidRendererRegistry#isBlockTransparent(Block)} instead.
     */
    @Deprecated
    public boolean isBlockTransparent(Block block) {
        return FluidRendererRegistry.getInstance().isBlockTransparent(block);
    }
    
    /**
     * Called when the fluid renderer reloads textures.
     * 
     * @deprecated Use {@link FluidRendererRegistry#onFluidRendererReload(LiquidBlockRenderer, TextureAtlasSprite[], TextureAtlasSprite[], TextureAtlasSprite)} instead.
     */
    @Deprecated
    public void onFluidRendererReload(LiquidBlockRenderer renderer, TextureAtlasSprite[] waterSprites, TextureAtlasSprite[] lavaSprites, TextureAtlasSprite waterOverlay) {
        FluidRendererRegistry.getInstance().onFluidRendererReload(renderer, waterSprites, lavaSprites, waterOverlay);
    }
    
    /**
     * Adapter to wrap Fabric API handler for use with native API.
     */
    private static class FluidRenderHandlerAdapter implements FluidRendererRegistry.FluidRenderHandler {
        private final FluidRenderHandler fabricHandler;
        
        FluidRenderHandlerAdapter(FluidRenderHandler fabricHandler) {
            this.fabricHandler = fabricHandler;
        }
        
        @Override
        public TextureAtlasSprite[] getFluidSprites(net.minecraft.world.level.BlockAndTintGetter view, net.minecraft.core.BlockPos pos, net.minecraft.world.level.material.FluidState state) {
            return fabricHandler.getFluidSprites(view, pos, state);
        }
        
        @Override
        public int getFluidColor(net.minecraft.world.level.BlockAndTintGetter view, net.minecraft.core.BlockPos pos, net.minecraft.world.level.material.FluidState state) {
            return fabricHandler.getFluidColor(view, pos, state);
        }
        
        @Override
        public void reloadTextures(net.minecraft.client.renderer.texture.TextureAtlas textureAtlas) {
            fabricHandler.reloadTextures(textureAtlas);
        }
    }
    
    /**
     * Wrapper to adapt native handler for Fabric API compatibility.
     */
    private static class NativeHandlerWrapper implements FluidRenderHandler {
        private final FluidRendererRegistry.FluidRenderHandler nativeHandler;
        
        NativeHandlerWrapper(FluidRendererRegistry.FluidRenderHandler nativeHandler) {
            this.nativeHandler = nativeHandler;
        }
        
        @Override
        public TextureAtlasSprite[] getFluidSprites(net.minecraft.world.level.BlockAndTintGetter view, net.minecraft.core.BlockPos pos, net.minecraft.world.level.material.FluidState state) {
            return nativeHandler.getFluidSprites(view, pos, state);
        }
        
        @Override
        public int getFluidColor(net.minecraft.world.level.BlockAndTintGetter view, net.minecraft.core.BlockPos pos, net.minecraft.world.level.material.FluidState state) {
            return nativeHandler.getFluidColor(view, pos, state);
        }
        
        @Override
        public void reloadTextures(net.minecraft.client.renderer.texture.TextureAtlas textureAtlas) {
            nativeHandler.reloadTextures(textureAtlas);
        }
    }
}
