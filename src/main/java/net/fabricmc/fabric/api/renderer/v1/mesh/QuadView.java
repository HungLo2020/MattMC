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

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Interface for reading quad data encoded in Meshes.
 */
public interface QuadView {
    /** Count of integers in a conventional block or item vertex. */
    int VANILLA_VERTEX_STRIDE = 8;
    /** Count of integers in a conventional block or item quad. */
    int VANILLA_QUAD_STRIDE = VANILLA_VERTEX_STRIDE * 4;

    float x(int vertexIndex);
    float y(int vertexIndex);
    float z(int vertexIndex);
    float posByIndex(int vertexIndex, int coordinateIndex);
    Vector3f copyPos(int vertexIndex, @Nullable Vector3f target);
    
    @Nullable Direction cullFace();
    @NotNull Direction lightFace();
    @Nullable Direction nominalFace();
    Vector3f faceNormal();
    
    @Nullable ChunkSectionLayer renderLayer();
    boolean emissive();
    boolean diffuseShade();
    TriState ambientOcclusion();
    @Nullable ItemStackRenderState.FoilType glint();
    ShadeMode shadeMode();
    
    int color(int vertexIndex);
    float u(int vertexIndex);
    float v(int vertexIndex);
    Vector2f copyUv(int vertexIndex, @Nullable Vector2f target);
    int lightmap(int vertexIndex);
    boolean hasNormal(int vertexIndex);
    float normalX(int vertexIndex);
    float normalY(int vertexIndex);
    float normalZ(int vertexIndex);
    @Nullable Vector3f copyNormal(int vertexIndex, @Nullable Vector3f target);
    
    int tag();
    int tintIndex();
    
    /**
     * Copies this quad's data to the target mutable quad view.
     * Default implementation does nothing - subclasses should override.
     */
    default void copyTo(MutableQuadView target) {
        // Default implementation - subclasses should override
    }
    
    /**
     * Converts this quad to vanilla format and copies to the target array.
     */
    default void toVanilla(int[] target, int targetIndex) {
        // Default implementation - subclasses should override
    }
    
    default @Nullable TextureAtlasSprite sprite() {
        return null;
    }
}
