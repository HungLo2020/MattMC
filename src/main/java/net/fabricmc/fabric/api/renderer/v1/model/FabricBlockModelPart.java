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

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Fabric extension interface for BlockModelPart to support enhanced rendering.
 * This interface is applied to BlockModelPart via mixin, NOT via inheritance
 * to avoid ClassCircularityError during class loading.
 */
public interface FabricBlockModelPart {
    /**
     * Emits quads from this model part to the given emitter.
     */
    default void emitQuads(QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
        // Default implementation does nothing - mixins will override
    }
}
