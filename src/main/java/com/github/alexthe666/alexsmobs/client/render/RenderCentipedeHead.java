package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCaveCentipede;
import com.github.alexthe666.alexsmobs.entity.EntityCentipedeHead;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RenderCentipedeHead extends MobRenderer<EntityCentipedeHead, CentipedeHeadRenderState, ModelCaveCentipede> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cave_centipede.png");

    public RenderCentipedeHead(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCaveCentipede(0), 0.5F);
        //this.addLayer(new LayerCentipedeHeadEyes(this));
    }

    @Override
    public CentipedeHeadRenderState createRenderState() {
        return new CentipedeHeadRenderState();
    }

    @Override
    public void extractRenderState(EntityCentipedeHead entity, CentipedeHeadRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isDeadOrDying = entity.isDeadOrDying();
        state.deathTime = entity.deathTime;
    }

    protected void scale(CentipedeHeadRenderState state, PoseStack matrixStackIn) {
        if (state.isDeadOrDying) {
            float f = ((float) state.deathTime + state.ageInTicks - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }
            matrixStackIn.translate(0, f * 1F, 0);
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(f * 180.0F));
        }
    }

    public ResourceLocation getTextureLocation(CentipedeHeadRenderState state) {
        return TEXTURE;
    }
}
