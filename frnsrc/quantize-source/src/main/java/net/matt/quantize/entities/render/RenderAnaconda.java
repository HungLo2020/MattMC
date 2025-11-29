package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelAnaconda;
import net.matt.quantize.entities.mobs.EntityAnaconda;
import net.matt.quantize.modules.entities.AnacondaPartIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderAnaconda extends MobRenderer<EntityAnaconda, ModelAnaconda<EntityAnaconda>> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/anaconda/anaconda.png");
    private static final ResourceLocation TEXTURE_SHEDDING = new ResourceIdentifier("textures/model/entity/anaconda/anaconda_shedding.png");
    private static final ResourceLocation TEXTURE_YELLOW = new ResourceIdentifier("textures/model/entity/anaconda/anaconda_yellow.png");
    private static final ResourceLocation TEXTURE_YELLOW_SHEDDING = new ResourceIdentifier("textures/model/entity/anaconda/anaconda_yellow_shedding.png");

    public RenderAnaconda(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAnaconda(AnacondaPartIndex.HEAD), 0.3F);
    }

    protected void scale(EntityAnaconda entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(entitylivingbaseIn.getScale(), entitylivingbaseIn.getScale(), entitylivingbaseIn.getScale());
    }

    public static ResourceLocation getAnacondaTexture(boolean yellow, boolean shedding) {
        return yellow ? shedding ? TEXTURE_YELLOW_SHEDDING : TEXTURE_YELLOW : shedding ? TEXTURE_SHEDDING : TEXTURE;
    }

    public ResourceLocation getTextureLocation(EntityAnaconda entity) {
        return getAnacondaTexture(entity.isYellow(), entity.isShedding());
    }
}
