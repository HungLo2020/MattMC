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
    private final ModelPart customRoot;

    protected BasicEntityModel() {
        this(RenderType::entityCutoutNoCull);
    }

    protected BasicEntityModel(Function<ResourceLocation, RenderType> renderType) {
        // In 1.21, EntityModel requires a ModelPart root
        // Create a custom root that will render our BasicModelPart parts
        super(createCustomRoot(), renderType);
        this.customRoot = root;
    }

    private static ModelPart createCustomRoot() {
        // Create a wrapper ModelPart that will be used to render BasicModelPart parts
        return new ModelPart(java.util.Collections.emptyList(), java.util.Collections.emptyMap());
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        // Render all our custom BasicModelPart parts
        for (BasicModelPart part : parts()) {
            part.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        }
    }

    public abstract Iterable<BasicModelPart> parts();

    @Override
    public abstract void setupAnim(T renderState);
}