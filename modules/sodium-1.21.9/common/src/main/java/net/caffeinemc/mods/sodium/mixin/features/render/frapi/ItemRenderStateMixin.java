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

package net.caffeinemc.mods.sodium.mixin.features.render.frapi;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableMeshImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AccessLayerRenderState;
import net.caffeinemc.mods.sodium.client.render.frapi.render.QuadToPosPipe;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
abstract class ItemRenderStateMixin {
    @Shadow
    ItemDisplayContext displayContext;

    @Shadow
    private int activeLayerCount;

    @Shadow
    private ItemStackRenderState.LayerRenderState[] layers;

    /**
     * Injects custom FRAPI mesh rendering into visitExtents.
     * Rewritten to avoid @Local captures that fail on MC 1.21.10.
     */
    @Inject(method = "visitExtents(Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void sodium$visitExtentsWithFRAPI(Consumer<Vector3fc> posConsumer, CallbackInfo ci) {
        // Replicate the original visitExtents logic with FRAPI mesh support
        Vector3f vec = new Vector3f();
        PoseStack.Pose pose = new PoseStack.Pose();
        QuadToPosPipe pipe = null;

        for (int i = 0; i < this.activeLayerCount; i++) {
            ItemStackRenderState.LayerRenderState layer = this.layers[i];
            layer.transform.apply(this.displayContext.leftHand(), pose);
            Matrix4f matrix = pose.pose();
            Vector3f[] extents = (Vector3f[]) layer.extents.get();

            // Process original extents
            for (Vector3f extent : extents) {
                posConsumer.accept(vec.set(extent).mulPosition(matrix));
            }

            // Process FRAPI mesh extents
            MutableMeshImpl mutableMesh = ((AccessLayerRenderState) layer).fabric_getMutableMesh();
            if (mutableMesh.size() > 0) {
                if (pipe == null) {
                    pipe = new QuadToPosPipe(posConsumer, vec);
                }
                pipe.matrix = matrix;
                mutableMesh.forEachMutable(pipe);
            }

            pose.setIdentity();
        }

        ci.cancel();
    }
}