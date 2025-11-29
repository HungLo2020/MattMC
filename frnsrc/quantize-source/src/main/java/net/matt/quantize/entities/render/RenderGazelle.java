package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelGazelle;
import net.matt.quantize.entities.mobs.EntityGazelle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderGazelle extends MobRenderer<EntityGazelle, ModelGazelle> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/gazelle/gazelle.png");

    public RenderGazelle(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGazelle(), 0.4F);
    }

    protected void scale(EntityGazelle entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }


    public ResourceLocation getTextureLocation(EntityGazelle entity) {
        return TEXTURE;
    }
}
