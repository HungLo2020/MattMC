package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelAnaconda;
import com.github.alexthe666.alexsmobs.entity.EntityAnacondaPart;
import com.github.alexthe666.alexsmobs.entity.util.AnacondaPartIndex;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

public class RenderAnacondaPart extends LivingEntityRenderer<EntityAnacondaPart, AnacondaPartRenderState, ModelAnaconda> {
    private final ModelAnaconda neckModel = new ModelAnaconda(AnacondaPartIndex.NECK);
    private final ModelAnaconda bodyModel = new ModelAnaconda(AnacondaPartIndex.BODY);
    private final ModelAnaconda tailModel = new ModelAnaconda(AnacondaPartIndex.TAIL);

    public RenderAnacondaPart(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAnaconda(AnacondaPartIndex.NECK), 0.3F);
    }

    @Override
    public AnacondaPartRenderState createRenderState() {
        return new AnacondaPartRenderState();
    }

    @Override
    public void extractRenderState(EntityAnacondaPart entity, AnacondaPartRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.bodyIndex = entity.getBodyIndex();
        renderState.partType = entity.getPartType();
        renderState.swell = entity.getSwellLerp(partialTick);
        renderState.strangleProgress = entity.getStrangleProgress(partialTick);
        renderState.isYellow = entity.isYellow();
        renderState.isShedding = entity.isShedding();
        renderState.scale = entity.isBaby() ? 0.75F : 1.0F;
    }

    protected void setupRotations(AnacondaPartRenderState renderState, PoseStack stack, float bodyRot, float scale) {
        float newYaw = renderState.bodyRot;
        if (this.isShaking(renderState)) {
            newYaw += (float)(Math.cos((double)renderState.ageInTicks * 3.25D) * Math.PI * (double)0.4F);
        }

        Pose pose = renderState.pose;
        if (pose != Pose.SLEEPING) {
         //   stack.mulPose(Axis.YP.rotationDegrees(180.0F - yawIn));
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - newYaw));
            stack.mulPose(Axis.XP.rotationDegrees(renderState.xRot));
        }

        if (renderState.deathTime > 0) {
            float f = ((float)renderState.deathTime - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }

            stack.mulPose(Axis.ZP.rotationDegrees(f * this.getFlipDegrees()));
         } else if (renderState.nameTag != null) {
            String s = ChatFormatting.stripFormatting(renderState.nameTag.getString());
            if (("Dinnerbone".equals(s) || "Grumm".equals(s))) {
                stack.translate(0.0D, (double)(renderState.boundingBoxHeight + 0.1F), 0.0D);
                stack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
        }

    }

    @Override
    protected void scale(AnacondaPartRenderState renderState, PoseStack matrixStackIn) {
        this.model = getModelForType(renderState.partType);
        matrixStackIn.scale(renderState.scale, renderState.scale, renderState.scale);
    }

    private ModelAnaconda getModelForType(AnacondaPartIndex partType) {
        switch (partType){
            case BODY: return bodyModel;
            case NECK: return neckModel;
            case TAIL: return tailModel;
        }
        return bodyModel;
    }


    @Override
    public ResourceLocation getTextureLocation(AnacondaPartRenderState renderState) {
        return RenderAnaconda.getAnacondaTexture(renderState.isYellow, renderState.isShedding);
    }
}
