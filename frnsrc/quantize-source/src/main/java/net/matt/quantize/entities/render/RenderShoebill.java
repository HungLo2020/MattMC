package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelShoebill;
import net.matt.quantize.entities.mobs.EntityShoebill;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderShoebill extends MobRenderer<EntityShoebill, ModelShoebill> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/shoebill.png");

    public RenderShoebill(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelShoebill(), 0.3F);
    }

    protected void scale(EntityShoebill entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }

    public ResourceLocation getTextureLocation(EntityShoebill entity) {
        return TEXTURE;
    }
}
