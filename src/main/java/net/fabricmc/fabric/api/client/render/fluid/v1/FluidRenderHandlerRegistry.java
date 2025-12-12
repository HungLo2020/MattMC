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

import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for custom fluid render handlers.
 */
public final class FluidRenderHandlerRegistry {
    private static final FluidRenderHandlerRegistry INSTANCE = new FluidRenderHandlerRegistry();
    private final Map<Fluid, FluidRenderHandler> handlers = new HashMap<>();
    
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
     * Gets the render handler for a fluid.
     */
    @Nullable
    public FluidRenderHandler get(Fluid fluid) {
        return handlers.get(fluid);
    }
}
