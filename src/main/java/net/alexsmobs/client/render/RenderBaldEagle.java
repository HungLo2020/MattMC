package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelBaldEagle;
import net.alexsmobs.entity.EntityBaldEagle;
import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public class RenderBaldEagle extends MobRenderer<EntityBaldEagle, BaldEagleRenderState, ModelBaldEagle> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/bald_eagle.png");
    private static final ResourceLocation TEXTURE_CAP = ResourceLocation.withDefaultNamespace("textures/entity/bald_eagle_hood.png");

    public RenderBaldEagle(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelBaldEagle(), 0.3F);
        this.addLayer(new CapLayer(this));
    }

    @Override
    public BaldEagleRenderState createRenderState() {
        return new BaldEagleRenderState();
    }

    @Override
    public void extractRenderState(EntityBaldEagle entity, BaldEagleRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.hasCap = entity.hasCap();
        state.isPassenger = entity.isPassenger();
        state.vehicle = entity.getVehicle();
        state.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTicks;
        state.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTicks;
        state.tackleProgress = entity.prevTackleProgress + (entity.tackleProgress - entity.prevTackleProgress) * partialTicks;
        state.swoopProgress = entity.prevSwoopProgress + (entity.swoopProgress - entity.prevSwoopProgress) * partialTicks;
        state.flapAmount = entity.prevFlapAmount + (entity.flapAmount - entity.prevFlapAmount) * partialTicks;
        state.birdPitch = entity.prevBirdPitch + (entity.birdPitch - entity.prevBirdPitch) * partialTicks;
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTicks;
    }

    public boolean shouldRender(EntityBaldEagle baldEagle, Frustum p_225626_2_, double p_225626_3_, double p_225626_5_, double p_225626_7_) {
        if( baldEagle.isPassenger() && baldEagle.getVehicle() instanceof Player && Minecraft.getInstance().player == baldEagle.getVehicle() && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON){
            return false;
        }
        return super.shouldRender(baldEagle, p_225626_2_, p_225626_3_, p_225626_5_, p_225626_7_);
    }

    protected void scale(EntityBaldEagle eagle, PoseStack matrixStackIn, float partialTickTime) {
        if(eagle.isPassenger() && eagle.getVehicle() != null) {
            if (eagle.getVehicle() instanceof Player) {
                Player mount = (Player)eagle.getVehicle();
                boolean leftHand = false;
                if(mount.getItemInHand(InteractionHand.MAIN_HAND).getItem() == net.minecraft.world.item.Items.LEATHER_HORSE_ARMOR){
                    leftHand = mount.getMainArm() == HumanoidArm.LEFT;
                }else if(mount.getItemInHand(InteractionHand.OFF_HAND).getItem() == net.minecraft.world.item.Items.LEATHER_HORSE_ARMOR){
                    leftHand = mount.getMainArm() != HumanoidArm.LEFT;
                }
                EntityRenderer playerRender = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mount);
                if(Minecraft.getInstance().player == mount && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON){
                    //handled via event
                }else if (playerRender instanceof LivingEntityRenderer && ((LivingEntityRenderer) playerRender).getModel() instanceof HumanoidModel) {
                    if(leftHand){
                        matrixStackIn.translate(-0.3F, -0.7F, 0.5F);
                        ((HumanoidModel) ((LivingEntityRenderer) playerRender).getModel()).leftArm.translateAndRotate(matrixStackIn);
                        matrixStackIn.translate(-0.2F, 0.5F, -0.18F);
                        matrixStackIn.mulPose(Axis.XP.rotationDegrees(40F));
                        matrixStackIn.mulPose(Axis.YP.rotationDegrees(70F));
                    }else{
                        matrixStackIn.translate(0.3F, -0.7F, 0.5F);
                        ((HumanoidModel) ((LivingEntityRenderer) playerRender).getModel()).rightArm.translateAndRotate(matrixStackIn);
                        matrixStackIn.translate(0.2F, 0.5F, -0.18F);
                        matrixStackIn.mulPose(Axis.XP.rotationDegrees(40F));
                        matrixStackIn.mulPose(Axis.YP.rotationDegrees(-70F));
                    }
                }
            }
        }
    }


    public ResourceLocation getTextureLocation(BaldEagleRenderState state) {
        return TEXTURE;
    }

    static class CapLayer extends RenderLayer<BaldEagleRenderState, ModelBaldEagle> {

        public CapLayer(RenderBaldEagle p_i50928_1_) {
            super(p_i50928_1_);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, BaldEagleRenderState state, float limbSwing, float limbSwingAmount) {
            if (state.hasCap) {
                RenderType renderType = RenderType.entityTranslucent(TEXTURE_CAP);
                collector.order(1).submitModelSemanticTexture(this.getParentModel(), state, poseStack, renderType, packedLight, OverlayTexture.NO_OVERLAY, -1, TEXTURE_CAP, state.outlineColor, null);
            }
        }
    }
}
