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

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for custom fluid render handlers.
 */
public final class FluidRenderHandlerRegistry {
    public static final FluidRenderHandlerRegistry INSTANCE = new FluidRenderHandlerRegistry();
    private final Map<Fluid, FluidRenderHandler> handlers = new HashMap<>();
    private final Map<Fluid, FluidRenderHandler> overrides = new HashMap<>();
    
    private FluidRenderHandlerRegistry() { }
    
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
        handlers.put(fluid, handler);
    }
    
    /**
     * Sets an override handler for a fluid.
     */
    public void setOverride(Fluid fluid, FluidRenderHandler handler) {
        overrides.put(fluid, handler);
    }
    
    /**
     * Gets the render handler for a fluid.
     */
    @Nullable
    public FluidRenderHandler get(Fluid fluid) {
        FluidRenderHandler override = overrides.get(fluid);
        return override != null ? override : handlers.get(fluid);
    }
    
    /**
     * Gets the override handler for a fluid if one exists.
     */
    @Nullable
    public FluidRenderHandler getOverride(Fluid fluid) {
        return overrides.get(fluid);
    }
    
    /**
     * Checks if a block is transparent for fluid rendering purposes.
     */
    public boolean isBlockTransparent(Block block) {
        // By default, assume non-solid blocks are transparent
        return !block.defaultBlockState().canOcclude();
    }
}
