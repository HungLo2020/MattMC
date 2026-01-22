package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelSugarGlider;
import com.github.alexthe666.alexsmobs.entity.EntitySugarGlider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

public class RenderSugarGlider extends MobRenderer<EntitySugarGlider, SugarGliderRenderState, ModelSugarGlider> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/sugar_glider.png");

    public RenderSugarGlider(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSugarGlider(), 0.35F);
    }

    @Override
    public SugarGliderRenderState createRenderState() {
        return new SugarGliderRenderState();
    }

    @Override
    public void extractRenderState(EntitySugarGlider entity, SugarGliderRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.glideProgress = entity.prevGlideProgress + (entity.glideProgress - entity.prevGlideProgress) * partialTick;
        state.forageProgress = entity.prevForageProgress + (entity.forageProgress - entity.prevForageProgress) * partialTick;
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.attachChangeProgress = entity.prevAttachChangeProgress + (entity.attachChangeProgress - entity.prevAttachChangeProgress) * partialTick;
        state.attachmentFacing = entity.getAttachmentFacing();
        state.prevAttachDir = entity.prevAttachDir;
        state.isPassenger = entity.isPassenger();
    }

    private Direction rotate(Direction attachmentFacing){
        return attachmentFacing.getAxis() == Direction.Axis.Y ? Direction.UP : attachmentFacing;
    }

    @Override
    protected void setupRotations(SugarGliderRenderState state, PoseStack matrixStackIn, float bob, float yRot) {
        if(state.isPassenger){
            super.setupRotations(state, matrixStackIn, bob, yRot);
            return;
        }
        if (this.isShaking(state)) {
            yRot += (float)(Math.cos((double)state.ageInTicks * 3.25D) * Math.PI * (double)0.4F);
        }
        float trans = state.isBaby ? 0.2F : 0.4F;
        if (state.pose != Pose.SLEEPING) {
            float prevProg = state.attachChangeProgress;
            float yawMul = 0F;
            if(state.prevAttachDir == state.attachmentFacing && state.attachmentFacing.getAxis() == Direction.Axis.Y){
                yawMul = 1.0F;
            }
            matrixStackIn.mulPose(Axis.YP.rotationDegrees((180.0F - yawMul * yRot)));

            if(state.attachmentFacing == Direction.DOWN){
                matrixStackIn.translate(0.0D, trans, 0.0D);
                matrixStackIn.mulPose(Axis.XP.rotationDegrees(90 * prevProg));
                matrixStackIn.translate(0.0D, -trans, 0.0D);
            }

            matrixStackIn.translate(0.0D, trans, 0.0D);
            Quaternionf current = rotate(state.attachmentFacing).getRotation();
            current.mul(1F - prevProg);
            matrixStackIn.mulPose(current);
            matrixStackIn.translate(0.0D, -trans, 0.0D);
        }

        if (state.deathTime > 0) {
            float f = ((float)state.deathTime - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }

            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(f * this.getFlipDegrees()));
        } else if (state.nameTag != null && (ChatFormatting.stripFormatting(state.nameTag.getString()).equals("Dinnerbone") || ChatFormatting.stripFormatting(state.nameTag.getString()).equals("Grumm"))) {
            matrixStackIn.translate(0.0D, (double)(state.boundingBoxHeight + 0.1F), 0.0D);
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
    }

    protected void scale(SugarGliderRenderState state, PoseStack matrixStackIn) {
        // Simplified - removed player riding check for now
    }


    public ResourceLocation getTextureLocation(SugarGliderRenderState state) {
        return TEXTURE;
    }
}

