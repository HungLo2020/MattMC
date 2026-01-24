package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelTarantulaHawk;
import com.github.alexthe666.alexsmobs.client.model.ModelTarantulaHawkBaby;
import com.github.alexthe666.alexsmobs.entity.EntityTarantulaHawk;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class RenderTarantulaHawk extends MobRenderer<EntityTarantulaHawk, TarantulaHawkRenderState, ModelTarantulaHawk> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/tarantula_hawk.png");
    private static final ResourceLocation TEXTURE_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/tarantula_hawk_angry.png");
    private static final ResourceLocation TEXTURE_NETHER = ResourceLocation.withDefaultNamespace("textures/entity/tarantula_hawk_nether.png");
    private static final ResourceLocation TEXTURE_NETHER_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/tarantula_hawk_nether_angry.png");
    private static final ResourceLocation TEXTURE_BABY = ResourceLocation.withDefaultNamespace("textures/entity/tarantula_hawk_baby.png");
    private final ModelTarantulaHawkBaby modelBaby;
    
    public RenderTarantulaHawk(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelTarantulaHawk(), 0.5F);
        this.modelBaby = new ModelTarantulaHawkBaby();
    }

    @Override
    public TarantulaHawkRenderState createRenderState() {
        return new TarantulaHawkRenderState();
    }

    @Override
    public void extractRenderState(EntityTarantulaHawk entity, TarantulaHawkRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.prevFlyAngle = entity.prevFlyAngle;
        renderState.flyAngle = entity.getFlyAngle();
        renderState.prevSitProgress = entity.prevSitProgress;
        renderState.sitProgress = entity.sitProgress;
        renderState.prevDragProgress = entity.prevDragProgress;
        renderState.dragProgress = entity.dragProgress;
        renderState.prevFlyProgress = entity.prevFlyProgress;
        renderState.flyProgress = entity.flyProgress;
        renderState.prevAttackProgress = entity.prevAttackProgress;
        renderState.attackProgress = entity.attackProgress;
        renderState.prevDigProgress = entity.prevDigProgress;
        renderState.digProgress = entity.digProgress;
        renderState.isFlying = entity.isFlying();
        renderState.isSitting = entity.isSitting();
        renderState.isDragging = entity.isDragging();
        renderState.isDigging = entity.isDigging();
        renderState.isScared = entity.isScared();
        renderState.isAngry = entity.isAngry();
        renderState.isNether = entity.isNether();
    }

    protected void scale(TarantulaHawkRenderState renderState, PoseStack matrixStackIn) {
        if(!renderState.isBaby){
            matrixStackIn.scale(0.9F, 0.9F, 0.9F);
            float f = renderState.dragProgress;
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(f * 180 * 0.2F));
        }
    }

    protected boolean isShaking(TarantulaHawkRenderState renderState) {
        return renderState.isScared;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(TarantulaHawkRenderState renderState, boolean b0, boolean b1, boolean b2) {
        ResourceLocation resourcelocation = this.getTextureLocation(renderState);
        if (b1) {
            return RenderType.itemEntityTranslucentCull(resourcelocation);
        } else if (b0) {
            return RenderType.entityTranslucent(resourcelocation);
        } else {
            return b2 ? RenderType.outline(resourcelocation) : null;
        }
    }

    public ResourceLocation getTextureLocation(TarantulaHawkRenderState renderState) {
        return renderState.isBaby ? TEXTURE_BABY : renderState.isNether ? renderState.isAngry ? TEXTURE_NETHER_ANGRY : TEXTURE_NETHER : renderState.isAngry ? TEXTURE_ANGRY : TEXTURE;
    }
}
