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

import java.util.function.Consumer;

/**
 * A mutable mesh that can be built and then converted to an immutable Mesh.
 */
public interface MutableMesh extends MeshView {
    
    /**
     * Gets the quad emitter for adding quads to this mesh.
     */
    QuadEmitter emitter();
    
    /**
     * Builds and returns an immutable copy of this mesh.
     * The mutable mesh is cleared after this call.
     */
    default Mesh build() {
        Mesh mesh = immutableCopy();
        clear();
        return mesh;
    }
    
    /**
     * Returns an immutable copy without clearing this mesh.
     */
    Mesh immutableCopy();
    
    /**
     * Clears all quads from this mesh.
     */
    void clear();
    
    /**
     * Iterates over all quads in this mesh with mutable access.
     */
    void forEachMutable(Consumer<? super MutableQuadView> action);
}
