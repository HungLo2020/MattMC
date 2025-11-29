package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelMantisShrimp;
import net.matt.quantize.entities.layer.LayerMantisShrimpItem;
import net.matt.quantize.entities.mobs.EntityMantisShrimp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderMantisShrimp extends MobRenderer<EntityMantisShrimp, ModelMantisShrimp> {
    private static final ResourceLocation TEXTURE_0 = new ResourceIdentifier("textures/model/entity/mantis_shrimp/mantis_shrimp_0.png");
    private static final ResourceLocation TEXTURE_1 = new ResourceIdentifier("textures/model/entity/mantis_shrimp/mantis_shrimp_1.png");
    private static final ResourceLocation TEXTURE_2 = new ResourceIdentifier("textures/model/entity/mantis_shrimp/mantis_shrimp_2.png");
    private static final ResourceLocation TEXTURE_3 = new ResourceIdentifier("textures/model/entity/mantis_shrimp/mantis_shrimp_3.png");

    public RenderMantisShrimp(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelMantisShrimp(), 0.6F);
        this.addLayer(new LayerMantisShrimpItem(this));
    }

    protected void scale(EntityMantisShrimp entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }


    public ResourceLocation getTextureLocation(EntityMantisShrimp entity) {
        return entity.getVariant() == 3 ? TEXTURE_3 : entity.getVariant() == 2 ? TEXTURE_2 : entity.getVariant() == 1 ? TEXTURE_1 : TEXTURE_0;
    }
}
