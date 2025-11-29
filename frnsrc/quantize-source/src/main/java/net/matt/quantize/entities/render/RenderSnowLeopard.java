package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelSnowLeopard;
import net.matt.quantize.entities.mobs.EntitySnowLeopard;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderSnowLeopard extends MobRenderer<EntitySnowLeopard, ModelSnowLeopard> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/snow_leopard/snow_leopard.png");
    private static final ResourceLocation TEXTURE_SLEEPING = new ResourceIdentifier("textures/model/entity/snow_leopard/snow_leopard_sleeping.png");

    public RenderSnowLeopard(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSnowLeopard(), 0.4F);
    }

    protected void scale(EntitySnowLeopard entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.9F,0.9F, 0.9F);
    }


    public ResourceLocation getTextureLocation(EntitySnowLeopard entity) {
        return entity.isSleeping() ? TEXTURE_SLEEPING : TEXTURE;
    }
}
