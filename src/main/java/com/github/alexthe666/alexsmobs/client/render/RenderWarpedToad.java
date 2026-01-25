package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelWarpedToad;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerWarpedToadGlow;
import com.github.alexthe666.alexsmobs.entity.EntityWarpedToad;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderWarpedToad extends MobRenderer<EntityWarpedToad, WarpedToadRenderState, ModelWarpedToad> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/warped_toad.png");
    private static final ResourceLocation TEXTURE_BLINKING = ResourceLocation.withDefaultNamespace("textures/entity/warped_toad_blink.png");
    private static final ResourceLocation TEXTURE_PEPE = ResourceLocation.withDefaultNamespace("textures/entity/warped_toad_pepe.png");
    private static final ResourceLocation TEXTURE_PEPE_BLINKING = ResourceLocation.withDefaultNamespace("textures/entity/warped_toad_pepe_blink.png");

    public RenderWarpedToad(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelWarpedToad(), 0.85F);
        this.addLayer(new LayerWarpedToadGlow(this));
    }

    @Override
    public WarpedToadRenderState createRenderState() {
        return new WarpedToadRenderState();
    }

    @Override
    public void extractRenderState(EntityWarpedToad entity, WarpedToadRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.blinkProgress = entity.prevBlinkProgress + (entity.blinkProgress - entity.prevBlinkProgress) * partialTick;
        state.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTick;
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.swimProgress = entity.prevSwimProgress + (entity.swimProgress - entity.prevSwimProgress) * partialTick;
        state.jumpProgress = entity.prevJumpProgress + (entity.jumpProgress - entity.prevJumpProgress) * partialTick;
        state.reboundProgress = entity.prevReboundProgress + (entity.reboundProgress - entity.prevReboundProgress) * partialTick;
        state.tongueLength = entity.getTongueLength();
        state.isBased = entity.isBased();
        state.isBlinking = entity.isBlinking();
    }

    protected void scale(WarpedToadRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(1.25F, 1.25F, 1.25F);
    }

    public ResourceLocation getTextureLocation(WarpedToadRenderState state) {
        if(state.isBased){
            return state.isBlinking ? TEXTURE_PEPE_BLINKING : TEXTURE_PEPE;
        }else{
            return state.isBlinking ? TEXTURE_BLINKING : TEXTURE;
        }
    }
}
