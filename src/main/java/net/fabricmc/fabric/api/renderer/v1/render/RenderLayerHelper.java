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

package net.fabricmc.fabric.api.renderer.v1.render;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for render layer handling.
 */
public final class RenderLayerHelper {
    
    private RenderLayerHelper() { }
    
    /**
     * Creates a BlockVertexConsumerProvider that delegates to a MultiBufferSource.
     */
    public static BlockVertexConsumerProvider entityDelegate(MultiBufferSource multiBufferSource) {
        return layer -> multiBufferSource.getBuffer(getEntityBlockLayer(layer));
    }
    
    /**
     * Gets the entity RenderType for a given blend mode / chunk section layer.
     */
    public static RenderType getEntityBlockLayer(@Nullable ChunkSectionLayer layer) {
        if (layer == null) {
            return RenderType.solid();
        }
        return switch (layer) {
            case SOLID -> RenderType.solid();
            case CUTOUT -> RenderType.cutout();
            case CUTOUT_MIPPED -> RenderType.cutoutMipped();
            case TRANSLUCENT, TRIPWIRE -> RenderType.translucentMovingBlock();
        };
    }
}
