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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * A mutable view of a quad for modification during mesh building.
 */
public interface MutableQuadView extends QuadView {
    
    /** When set, UVs are assumed to be 0-16 and will NOT be normalized. */
    int BAKE_NORMALIZED = 1;
    /** When set, UVs are locked to the rotation of the nominal face. */
    int BAKE_LOCK_UV = 2;
    /** When set, U coordinate is flipped. */
    int BAKE_FLIP_U = 4;
    /** When set, V coordinate is flipped. */
    int BAKE_FLIP_V = 8;
    /** When set, quad is rotated 90 degrees clockwise. */
    int BAKE_ROTATE_90 = 16;
    /** When set, quad is rotated 180 degrees. */
    int BAKE_ROTATE_180 = 32;
    /** When set, quad is rotated 270 degrees clockwise. */
    int BAKE_ROTATE_270 = 48;
    /** Mask for rotation bits. */
    int BAKE_ROTATE_MASK = 48;
    
    MutableQuadView pos(int vertexIndex, float x, float y, float z);
    
    default MutableQuadView pos(int vertexIndex, Vector3f vec) {
        return pos(vertexIndex, vec.x(), vec.y(), vec.z());
    }
    
    MutableQuadView color(int vertexIndex, int color);
    
    default MutableQuadView color(int c0, int c1, int c2, int c3) {
        color(0, c0);
        color(1, c1);
        color(2, c2);
        color(3, c3);
        return this;
    }
    
    MutableQuadView uv(int vertexIndex, float u, float v);
    
    default MutableQuadView uv(int vertexIndex, Vector2f uv) {
        return uv(vertexIndex, uv.x, uv.y);
    }
    
    MutableQuadView spriteBake(TextureAtlasSprite sprite, int bakeFlags);
    
    MutableQuadView lightmap(int vertexIndex, int lightmap);
    
    default MutableQuadView lightmap(int l0, int l1, int l2, int l3) {
        lightmap(0, l0);
        lightmap(1, l1);
        lightmap(2, l2);
        lightmap(3, l3);
        return this;
    }
    
    MutableQuadView normal(int vertexIndex, float x, float y, float z);
    
    default MutableQuadView normal(int vertexIndex, Vector3f vec) {
        return normal(vertexIndex, vec.x(), vec.y(), vec.z());
    }
    
    MutableQuadView cullFace(@Nullable Direction face);
    
    MutableQuadView nominalFace(@Nullable Direction face);
    
    MutableQuadView renderLayer(@Nullable ChunkSectionLayer renderLayer);
    
    MutableQuadView emissive(boolean emissive);
    
    MutableQuadView diffuseShade(boolean diffuseShade);
    
    MutableQuadView ambientOcclusion(TriState ambientOcclusion);
    
    MutableQuadView glint(@Nullable ItemStackRenderState.FoilType glint);
    
    MutableQuadView shadeMode(ShadeMode shadeMode);
    
    MutableQuadView tag(int tag);
    
    MutableQuadView copyFrom(QuadView quad);
    
    MutableQuadView fromVanilla(int[] quadData, int startIndex);
    
    default MutableQuadView sprite(TextureAtlasSprite sprite) {
        return this;
    }
}
