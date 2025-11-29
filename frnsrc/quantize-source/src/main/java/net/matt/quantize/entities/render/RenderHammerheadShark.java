package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelHammerheadShark;
import net.matt.quantize.entities.mobs.EntityHammerheadShark;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderHammerheadShark extends MobRenderer<EntityHammerheadShark, ModelHammerheadShark> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/hammerhead_shark/hammerhead_shark.png");

    public RenderHammerheadShark(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelHammerheadShark(), 0.8F);
    }

    protected void scale(EntityHammerheadShark entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }


    public ResourceLocation getTextureLocation(EntityHammerheadShark entity) {
        return TEXTURE;
    }
}
