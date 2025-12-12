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

package net.fabricmc.fabric.api.renderer.v1.mesh;

import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Interface for building quads and emitting them to a mesh.
 */
public interface QuadEmitter extends MutableQuadView {
    
    @Override
    QuadEmitter pos(int vertexIndex, float x, float y, float z);
    
    @Override
    QuadEmitter color(int vertexIndex, int color);
    
    @Override
    QuadEmitter uv(int vertexIndex, float u, float v);
    
    @Override
    QuadEmitter lightmap(int vertexIndex, int lightmap);
    
    @Override
    QuadEmitter normal(int vertexIndex, float x, float y, float z);
    
    /**
     * Emits the current quad and resets state for the next quad.
     */
    QuadEmitter emit();
    
    /**
     * Copies data from a BakedQuad to this emitter.
     */
    QuadEmitter fromBakedQuad(BakedQuad quad);
}
