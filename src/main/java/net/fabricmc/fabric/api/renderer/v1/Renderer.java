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

package net.fabricmc.fabric.api.renderer.v1;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.BlockVertexConsumerProvider;

/**
 * Interface for rendering plug-ins that provide enhanced capabilities
 * for model lighting, buffering and rendering.
 */
public interface Renderer {
    /**
     * Access to the current Renderer for creating and retrieving mesh builders.
     */
    static Renderer get() {
        return RendererHolder.INSTANCE;
    }

    /**
     * Rendering extension mods must implement Renderer and call this method during initialization.
     */
    static void register(Renderer renderer) {
        RendererHolder.INSTANCE = renderer;
    }

    /**
     * Obtain a new MutableMesh instance to build optimized meshes.
     */
    MutableMesh mutableMesh();

    /**
     * Renders a block model with full context.
     */
    void render(ModelBlockRenderer modelRenderer, BlockAndTintGetter blockView, BlockStateModel model, 
                BlockState state, BlockPos pos, PoseStack matrices, BlockVertexConsumerProvider vertexConsumers, 
                boolean cull, long seed, int overlay);

    /**
     * Renders a block model with matrix entry.
     */
    void render(PoseStack.Pose matrices, BlockVertexConsumerProvider vertexConsumers, BlockStateModel model, 
                float red, float green, float blue, int light, int overlay, 
                BlockAndTintGetter blockView, BlockPos pos, BlockState state);

    /**
     * Renders a block as an entity.
     */
    void renderBlockAsEntity(BlockRenderDispatcher renderManager, BlockState state, PoseStack matrices, 
                             MultiBufferSource vertexConsumers, int light, int overlay, 
                             BlockAndTintGetter blockView, BlockPos pos);

    /**
     * Gets the quad emitter for a layer render state.
     */
    QuadEmitter getLayerRenderStateEmitter(ItemStackRenderState.LayerRenderState layer);
}

/**
 * Internal holder for the renderer instance.
 */
class RendererHolder {
    static Renderer INSTANCE = null;
}
