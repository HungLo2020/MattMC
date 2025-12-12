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
 * Read-only view of a Mesh. Used to iterate over quad data.
 */
public interface MeshView {
    
    /**
     * Returns the number of quads in this mesh.
     */
    int size();
    
    /**
     * Iterates over all quads in this mesh, passing each to the consumer.
     */
    void forEach(Consumer<? super QuadView> consumer);
    
    /**
     * Outputs all quads from this mesh to the given emitter.
     */
    void outputTo(QuadEmitter emitter);
}
