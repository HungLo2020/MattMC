package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCaveCentipede;
import com.github.alexthe666.alexsmobs.entity.EntityCentipedeBody;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RenderCentipedeBody extends MobRenderer<EntityCentipedeBody, CentipedeBodyRenderState, ModelCaveCentipede> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cave_centipede.png");

    public RenderCentipedeBody(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCaveCentipede(1), 0.5F);
    }

    @Override
    public CentipedeBodyRenderState createRenderState() {
        return new CentipedeBodyRenderState();
    }

    @Override
    public void extractRenderState(EntityCentipedeBody entity, CentipedeBodyRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.bodyIndex = entity.getBodyIndex();
        state.isDeadOrDying = entity.isDeadOrDying();
        state.deathTime = entity.deathTime;
    }

    protected void scale(CentipedeBodyRenderState state, PoseStack matrixStackIn) {
        if (state.isDeadOrDying) {
            float f = ((float) state.deathTime + state.ageInTicks - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }
            matrixStackIn.translate(0, f * 1.15F, 0);
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(f * 180.0F));
        }
    }

    public ResourceLocation getTextureLocation(CentipedeBodyRenderState state) {
        return TEXTURE;
    }
}
