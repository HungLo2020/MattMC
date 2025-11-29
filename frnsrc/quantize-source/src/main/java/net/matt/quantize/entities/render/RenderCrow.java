package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelCrow;
import net.matt.quantize.entities.layer.LayerCrowItem;
import net.matt.quantize.entities.mobs.EntityCrow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCrow extends MobRenderer<EntityCrow, ModelCrow> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/crow/crow.png");

    public RenderCrow(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCrow(), 0.2F);
        this.addLayer(new LayerCrowItem(this));
    }

    protected void scale(EntityCrow entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }


    public ResourceLocation getTextureLocation(EntityCrow entity) {
        return TEXTURE;
    }
}
