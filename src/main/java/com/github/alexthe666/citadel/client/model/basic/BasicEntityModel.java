package com.github.alexthe666.citadel.client.model.basic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

// Citadel: 1.21 API change - EntityModel<T> now requires T extends EntityRenderState (not Entity)
// This is a major breaking change in the rendering system
public abstract class BasicEntityModel<T extends EntityRenderState> extends EntityModel<T> {
    public int textureWidth = 64;
    public int textureHeight = 32;

    protected BasicEntityModel(ModelPart root) {
        super(root); // Citadel: 1.21 - EntityModel constructor requires ModelPart
    }

    // Citadel: 1.21 - renderToBuffer() is now FINAL in EntityModel, cannot override
    // Use root().render() instead for custom rendering
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLightIn, int packedOverlayIn, int color) {
        this.parts().forEach((part) -> part.render(poseStack, vertexConsumer, packedLightIn, packedOverlayIn, color));
    }

    public abstract Iterable<BasicModelPart> parts();

    // Citadel: 1.21 - setupAnim signature changed completely
    // Old: setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    // New: setupAnim(T state) - all animation data is in the state object
    @Override
    public abstract void setupAnim(T state);
}