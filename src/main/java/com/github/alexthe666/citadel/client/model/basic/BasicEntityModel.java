package com.github.alexthe666.citadel.client.model.basic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
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

    protected BasicEntityModel(Function<ResourceLocation, RenderType> renderType) {
        // In 1.21, EntityModel requires a ModelPart root
        // Create empty dummy root since BasicEntityModel uses its own part system
        super(new ModelPart(java.util.Collections.emptyList(), java.util.Collections.emptyMap()), renderType);
    }


    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLightIn, int packedOverlayIn) {
        this.parts().forEach((part) -> part.render(poseStack, vertexConsumer, packedLightIn, packedOverlayIn));
    }

    public abstract Iterable<BasicModelPart> parts();

    @Override
    public abstract void setupAnim(T renderState);
}