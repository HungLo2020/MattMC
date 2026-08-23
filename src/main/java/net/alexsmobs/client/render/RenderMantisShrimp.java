package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelMantisShrimp;
import net.alexsmobs.client.render.layer.LayerMantisShrimpItem;
import net.alexsmobs.entity.EntityMantisShrimp;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderMantisShrimp extends MobRenderer<EntityMantisShrimp, MantisShrimpRenderState, ModelMantisShrimp> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/mantis_shrimp_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/mantis_shrimp_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/mantis_shrimp_2.png");
    private static final ResourceLocation TEXTURE_3 = ResourceLocation.withDefaultNamespace("textures/entity/mantis_shrimp_3.png");

    public RenderMantisShrimp(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelMantisShrimp(), 0.6F);
        this.addLayer(new LayerMantisShrimpItem(this));
    }

    @Override
    public MantisShrimpRenderState createRenderState() {
        return new MantisShrimpRenderState();
    }

    @Override
    public void extractRenderState(EntityMantisShrimp entity, MantisShrimpRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.rightEyePitch = entity.prevRightPitch + (entity.getEyePitch(false) - entity.prevRightPitch) * partialTick;
        state.rightEyeYaw = entity.prevRightYaw + (entity.getEyeYaw(false) - entity.prevRightYaw) * partialTick;
        state.leftEyePitch = entity.prevLeftPitch + (entity.getEyePitch(true) - entity.prevLeftPitch) * partialTick;
        state.leftEyeYaw = entity.prevLeftYaw + (entity.getEyeYaw(true) - entity.prevLeftYaw) * partialTick;
        state.inWaterProgress = entity.prevInWaterProgress + (entity.inWaterProgress - entity.prevInWaterProgress) * partialTick;
        state.punchProgress = entity.prevPunchProgress + (entity.punchProgress - entity.prevPunchProgress) * partialTick;
        state.isBaby = entity.isBaby();
        state.variant = entity.getVariant();
        this.itemModelResolver.updateForLiving(state.mainHandItem, entity.getMainHandItem(),
            net.minecraft.world.item.ItemDisplayContext.GROUND, entity);
        state.isLeftHanded = entity.isLeftHanded();
    }

    protected void scale(MantisShrimpRenderState state, PoseStack matrixStackIn) {
        if (state.isBaby) {
            matrixStackIn.scale(0.4F, 0.4F, 0.4F);
        } else {
            matrixStackIn.scale(0.8F, 0.8F, 0.8F);
        }
    }


    public ResourceLocation getTextureLocation(MantisShrimpRenderState state) {
        return state.variant == 3 ? TEXTURE_3 : state.variant == 2 ? TEXTURE_2 : state.variant == 1 ? TEXTURE_1 : TEXTURE_0;
    }
}
