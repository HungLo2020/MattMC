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

package net.fabricmc.fabric.api.renderer.v1.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for finding sprites from UV coordinates.
 */
public interface SpriteFinder {
    
    /**
     * Gets a SpriteFinder for the given atlas.
     */
    static SpriteFinder get(TextureAtlas atlas) {
        // Return a no-op implementation for now
        return new SpriteFinder() {
            @Override
            public @Nullable TextureAtlasSprite find(float u, float v) {
                return null;
            }
            
            @Override
            public @Nullable TextureAtlasSprite find(QuadView quad) {
                return null;
            }
        };
    }
    
    /**
     * Finds a sprite at the given UV coordinates.
     */
    @Nullable
    TextureAtlasSprite find(float u, float v);
    
    /**
     * Finds a sprite for the given quad.
     */
    @Nullable
    TextureAtlasSprite find(QuadView quad);
}
