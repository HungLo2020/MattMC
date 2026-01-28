package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelHammerheadShark;
import com.github.alexthe666.alexsmobs.entity.EntityHammerheadShark;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderHammerheadShark extends MobRenderer<EntityHammerheadShark, HammerheadSharkRenderState, ModelHammerheadShark> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/hammerhead_shark.png");

    public RenderHammerheadShark(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelHammerheadShark(), 0.8F);
    }

    @Override
    public HammerheadSharkRenderState createRenderState() {
        return new HammerheadSharkRenderState();
    }

    @Override
    public void extractRenderState(EntityHammerheadShark entity, HammerheadSharkRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    protected void scale(HammerheadSharkRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(HammerheadSharkRenderState state) {
        return TEXTURE;
    }
}
