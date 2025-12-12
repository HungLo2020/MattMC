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

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for model rendering.
 */
public final class ModelHelper {
    private static final Direction[] DIRECTIONS = Direction.values();
    
    /** Face ID for null/non-culled face (the 7th face, index 6). */
    public static final int NULL_FACE_ID = DIRECTIONS.length;
    
    private ModelHelper() { }
    
    /**
     * Gets a Direction from a face index, or null for the non-culled face.
     */
    @Nullable
    public static Direction faceFromIndex(int faceIndex) {
        if (faceIndex < 0 || faceIndex >= DIRECTIONS.length) {
            return null;
        }
        return DIRECTIONS[faceIndex];
    }
    
    /**
     * Gets the face index for a direction, or the count of directions for null (non-culled).
     */
    public static int toFaceIndex(@Nullable Direction face) {
        return face == null ? DIRECTIONS.length : face.ordinal();
    }
}
