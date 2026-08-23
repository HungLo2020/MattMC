package net.alexsmobs.client.render;

import net.alexsmobs.client.model.KangarooModel;
import net.alexsmobs.entity.EntityKangaroo;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public class KangarooRenderer extends MobRenderer<EntityKangaroo, KangarooRenderState, KangarooModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/kangaroo.png");

    public KangarooRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new KangarooModel(), 0.5F);
    }

    @Override
    public KangarooRenderState createRenderState() {
        return new KangarooRenderState();
    }

    @Override
    public void extractRenderState(EntityKangaroo entity, KangarooRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        renderState.standProgress = entity.prevStandProgress + (entity.standProgress - entity.prevStandProgress) * partialTick;
        renderState.pouchProgress = entity.prevPouchProgress + (entity.pouchProgress - entity.prevPouchProgress) * partialTick;
        renderState.totalMovingProgress = entity.prevTotalMovingProgress + (entity.totalMovingProgress - entity.prevTotalMovingProgress) * partialTick;
        renderState.isStanding = entity.isStanding();
        renderState.isSitting = entity.isSitting();
        renderState.visualFlag = entity.getVisualFlag();
        renderState.pouchTick = entity.getPouchTick();
        renderState.helmetIndex = entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 0 : 1;
        renderState.swordIndex = entity.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() ? 0 : 1;
        renderState.chestIndex = entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? 0 : 1;
        renderState.animation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
        renderState.jumpCompletion = entity.getJumpCompletion(partialTick);
        renderState.isLeftHanded = entity.isLeftHanded();
        renderState.isPassenger = entity.isPassenger();
        renderState.vehicleIsKangaroo = entity.getVehicle() instanceof EntityKangaroo;
    }

    public boolean shouldRender(EntityKangaroo kangaroo, Frustum p_225626_2_, double p_225626_3_, double p_225626_5_, double p_225626_7_) {
        if(kangaroo.isBaby() && kangaroo.isPassenger() && kangaroo.getVehicle() instanceof EntityKangaroo){
            return false;
        }
        return super.shouldRender(kangaroo, p_225626_2_, p_225626_3_, p_225626_5_, p_225626_7_);
    }

    protected void scale(KangarooRenderState renderState, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(KangarooRenderState renderState) {
        return TEXTURE;
    }
}
