package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.TrilocarisModel;
import net.matt.quantize.entities.mobs.TrilocarisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TrilocarisRenderer extends MobRenderer<TrilocarisEntity, TrilocarisModel> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/trilocaris/trilocaris.png");

    public TrilocarisRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new TrilocarisModel(), 0.3F);
    }

    protected float getFlipDegrees(TrilocarisEntity centipede) {
        return 180.0F;
    }


    protected void scale(TrilocarisEntity mob, PoseStack matrixStackIn, float partialTicks) {
    }

    public ResourceLocation getTextureLocation(TrilocarisEntity entity) {
        return TEXTURE;
    }
}

