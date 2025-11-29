package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelCockroach;
import net.matt.quantize.entities.layer.LayerCockroachMaracas;
import net.matt.quantize.entities.mobs.EntityCockroach;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCockroach extends MobRenderer<EntityCockroach, ModelCockroach> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/cockroach/cockroach.png");

    public RenderCockroach(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCockroach(), 0.3F);
        this.addLayer(new LayerCockroachMaracas(this, renderManagerIn));
    }

    protected void scale(EntityCockroach entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.85F, 0.85F, 0.85F);
    }


    public ResourceLocation getTextureLocation(EntityCockroach entity) {
        return TEXTURE;
    }
}
