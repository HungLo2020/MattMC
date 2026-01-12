package com.github.alexthe666.citadel.client.model.basic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public abstract class BasicEntityModel<T extends EntityRenderState> extends EntityModel<T> {
    public int textureWidth = 64;
    public int textureHeight = 32;

    protected BasicEntityModel() {
        this(RenderType::entityCutoutNoCull);
    }

    protected BasicEntityModel(Function<ResourceLocation, RenderType> p_102613_) {
        super(p_102613_);
    }


    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLightIn, int packedOverlayIn, int color) {
        this.parts().forEach((part) -> part.render(poseStack, vertexConsumer, packedLightIn, packedOverlayIn, color));
    }

    public abstract Iterable<BasicModelPart> parts();

    @Override
    public abstract void setupAnim(T renderState);
}