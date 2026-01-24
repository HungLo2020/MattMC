package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelLeafcutterAnt;
import com.github.alexthe666.alexsmobs.client.model.ModelLeafcutterAntQueen;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerLeafcutterAntLeaf;
import com.github.alexthe666.alexsmobs.entity.EntityLeafcutterAnt;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

public class RenderLeafcutterAnt extends MobRenderer<EntityLeafcutterAnt, LeafcutterAntRenderState, AdvancedEntityModel<LeafcutterAntRenderState>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/leafcutter_ant.png");
    private static final ResourceLocation TEXTURE_QUEEN = ResourceLocation.withDefaultNamespace("textures/entity/leafcutter_ant_queen.png");
    private static final ResourceLocation TEXTURE_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/leafcutter_ant_angry.png");
    private static final ResourceLocation TEXTURE_QUEEN_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/leafcutter_ant_queen_angry.png");
    private final ModelLeafcutterAnt modelAnt = new ModelLeafcutterAnt();
    private final ModelLeafcutterAntQueen modelQueen = new ModelLeafcutterAntQueen();

    public RenderLeafcutterAnt(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelLeafcutterAnt(), 0.25F);
        this.addLayer(new LayerLeafcutterAntLeaf(this));
    }

    @Override
    public LeafcutterAntRenderState createRenderState() {
        return new LeafcutterAntRenderState();
    }

    @Override
    public void extractRenderState(EntityLeafcutterAnt entity, LeafcutterAntRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.hasLeaf = entity.hasLeaf();
        state.isQueen = entity.isQueen();
        state.isAngry = entity.getRemainingPersistentAngerTime() > 0;
        state.antScale = entity.getAntScale();
        state.attachmentFacing = entity.getAttachmentFacing();
        state.attachChangeProgress = entity.attachChangeProgress;
        state.prevAttachChangeProgress = entity.prevAttachChangeProgress;
        state.leafHarvestedState = entity.getLeafHarvestedState().orElse(null);
        state.leafHarvestedPos = entity.getLeafHarvestedPos().orElse(null);
        state.animationTick = entity.getAnimationTick();
        state.id = entity.getId();
    }


    @Override
    protected void setupRotations(LeafcutterAntRenderState state, PoseStack matrixStackIn, float ageInTicks, float scaleFactor) {
        super.setupRotations(state, matrixStackIn, ageInTicks, scaleFactor);
        
        // Rotate model 180 degrees to face the correct direction
        matrixStackIn.mulPose(Axis.YP.rotationDegrees(180.0F));
        
        float trans = state.isBaby ? 0.25F : 0.5F;
        Pose pose = state.pose;
        if (pose != Pose.SLEEPING) {
            float progresso = 1F - (state.prevAttachChangeProgress + (state.attachChangeProgress - state.prevAttachChangeProgress));

            if(state.attachmentFacing == Direction.DOWN){
                matrixStackIn.mulPose(Axis.YP.rotationDegrees (180.0F - state.yRot));
                matrixStackIn.translate(0.0D, trans, 0.0D);
                // Can't access yo/y in render state, skip transition rotation
                matrixStackIn.translate(0.0D, -trans, 0.0D);

            }else if(state.attachmentFacing == Direction.UP){
                matrixStackIn.mulPose(Axis.YP.rotationDegrees (180.0F - state.yRot));
                matrixStackIn.mulPose(Axis.XP.rotationDegrees(180));
                matrixStackIn.mulPose(Axis.YP.rotationDegrees(180));
                matrixStackIn.translate(0.0D, -trans, 0.0D);

            }else{
                matrixStackIn.translate(0.0D, trans, 0.0D);
                switch (state.attachmentFacing){
                    case NORTH:
                        matrixStackIn.mulPose(Axis.XP.rotationDegrees(90.0F * progresso));
                        matrixStackIn.mulPose(Axis.ZP.rotationDegrees(0));
                        break;
                    case SOUTH:
                        matrixStackIn.mulPose(Axis.YP.rotationDegrees(180.0F));
                        matrixStackIn.mulPose(Axis.XP.rotationDegrees(90.0F * progresso ));
                        break;
                    case WEST:
                        matrixStackIn.mulPose(Axis.XP.rotationDegrees(90.0F));
                        matrixStackIn.mulPose(Axis.YP.rotationDegrees(90F - 90.0F * progresso));
                        matrixStackIn.mulPose(Axis.ZP.rotationDegrees(-90.0F));
                        break;
                    case EAST:
                        matrixStackIn.mulPose(Axis.XP.rotationDegrees(90.0F ));
                        matrixStackIn.mulPose(Axis.YP.rotationDegrees(90.0F * progresso - 90F));
                        matrixStackIn.mulPose(Axis.ZP.rotationDegrees(90.0F));
                        break;
                }
                matrixStackIn.translate(0.0D, -trans, 0.0D);
            }
        }
    }

    protected void scale(LeafcutterAntRenderState state, PoseStack matrixStackIn) {
        model = state.isQueen ? modelQueen : modelAnt;
    }


    public ResourceLocation getTextureLocation(LeafcutterAntRenderState state) {
        if(state.isAngry){
            return state.isQueen ? TEXTURE_QUEEN_ANGRY : TEXTURE_ANGRY;
        }else {
            return state.isQueen ? TEXTURE_QUEEN : TEXTURE;
        }
    }
}
