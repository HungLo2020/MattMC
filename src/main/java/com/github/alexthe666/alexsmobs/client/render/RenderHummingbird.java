package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelHummingbird;
import com.github.alexthe666.alexsmobs.client.render.state.HummingbirdRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityHummingbird;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderHummingbird extends MobRenderer<EntityHummingbird, HummingbirdRenderState, ModelHummingbird> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/hummingbird_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/hummingbird_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/hummingbird_2.png");

    public RenderHummingbird(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelHummingbird(), 0.15F);
    }

    @Override
    public HummingbirdRenderState createRenderState() {
        return new HummingbirdRenderState();
    }

    @Override
    public void extractRenderState(EntityHummingbird entity, HummingbirdRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTick;
        renderState.movingProgress = entity.prevMovingProgress + (entity.movingProgress - entity.prevMovingProgress) * partialTick;
        renderState.sipProgress = entity.prevSipProgress + (entity.sipProgress - entity.prevSipProgress) * partialTick;
        renderState.variant = entity.getVariant();
    }

    protected void scale(HummingbirdRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.75F, 0.75F, 0.75F);
    }

    public ResourceLocation getTextureLocation(HummingbirdRenderState renderState) {
        return renderState.variant == 0 ? TEXTURE_0 : renderState.variant == 1 ? TEXTURE_1 : TEXTURE_2;
    }
}
