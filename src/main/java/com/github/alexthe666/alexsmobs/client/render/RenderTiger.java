package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelTiger;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerTigerEyes;
import com.github.alexthe666.alexsmobs.entity.EntityTiger;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderTiger extends MobRenderer<EntityTiger, TigerRenderState, ModelTiger> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/tiger/tiger.png");
    private static final ResourceLocation TEXTURE_ANGRY = ResourceLocation
            .withDefaultNamespace("textures/entity/tiger/tiger_angry.png");
    private static final ResourceLocation TEXTURE_SLEEPING = ResourceLocation
            .withDefaultNamespace("textures/entity/tiger/tiger_sleeping.png");
    private static final ResourceLocation TEXTURE_WHITE = ResourceLocation
            .withDefaultNamespace("textures/entity/tiger/tiger_white.png");
    private static final ResourceLocation TEXTURE_ANGRY_WHITE = ResourceLocation
            .withDefaultNamespace("textures/entity/tiger/tiger_white_angry.png");
    private static final ResourceLocation TEXTURE_SLEEPING_WHITE = ResourceLocation
            .withDefaultNamespace("textures/entity/tiger/tiger_white_sleeping.png");

    public RenderTiger(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelTiger(), 0.6F);
        this.addLayer(new LayerTigerEyes(this));
    }

    @Override
    public TigerRenderState createRenderState() {
        return new TigerRenderState();
    }

    @Override
    public void extractRenderState(EntityTiger entity, TigerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.sleepProgress = entity.prevSleepProgress + (entity.sleepProgress - entity.prevSleepProgress) * partialTick;
        state.holdProgress = entity.prevHoldProgress + (entity.holdProgress - entity.prevHoldProgress) * partialTick;
        state.stealthProgress = entity.prevStealthProgress + (entity.stealthProgress - entity.prevStealthProgress) * partialTick;
        state.isWhite = entity.isWhite();
        state.isSitting = entity.isSitting();
        state.isSleeping = entity.isSleeping();
        state.isRunning = entity.isRunning();
        state.isStealth = entity.isStealth();
        state.isHolding = entity.isHolding();
        state.isInWater = entity.isInWater();
        state.isBaby = entity.isBaby();
        state.remainingPersistentAngerTime = entity.getRemainingPersistentAngerTime();
        state.entityId = entity.getId();
    }

    protected void scale(TigerRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(TigerRenderState state) {
        if (state.isSleeping) {
            return state.isWhite ? TEXTURE_SLEEPING_WHITE : TEXTURE_SLEEPING;
        } else if (state.remainingPersistentAngerTime > 0) {
            return state.isWhite ? TEXTURE_ANGRY_WHITE : TEXTURE_ANGRY;
        } else {
            return state.isWhite ? TEXTURE_WHITE : TEXTURE;
        }
    }
}
