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

package net.fabricmc.fabric.api.blockview.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric extension interface for BlockAndTintGetter.
 */
public interface FabricBlockView extends BlockAndTintGetter {
    
    /**
     * Gets the render data associated with a block entity at the given position.
     */
    @Nullable
    default Object getBlockEntityRenderData(BlockPos pos) {
        return null;
    }
    
    /**
     * Returns whether this block view has biome information.
     */
    default boolean hasBiomes() {
        return false;
    }
    
    /**
     * Gets the biome at the given position using Fabric's extension method.
     */
    @Nullable
    default Holder<Biome> getBiomeFabric(BlockPos pos) {
        return null;
    }
}
