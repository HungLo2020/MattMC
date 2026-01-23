package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCaiman;
import com.github.alexthe666.alexsmobs.entity.EntityCaiman;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCaiman extends MobRenderer<EntityCaiman, CaimanRenderState, ModelCaiman> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/caiman.png");

    public RenderCaiman(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCaiman(), 0.4F);
    }

    @Override
    public CaimanRenderState createRenderState() {
        return new CaimanRenderState();
    }

    @Override
    public void extractRenderState(EntityCaiman entity, CaimanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.holdProgress = entity.prevHoldProgress + (entity.holdProgress - entity.prevHoldProgress) * partialTick;
        state.swimProgress = entity.prevSwimProgress + (entity.swimProgress - entity.prevSwimProgress) * partialTick;
        state.vibrateProgress = entity.prevVibrateProgress + (entity.vibrateProgress - entity.prevVibrateProgress) * partialTick;
        state.isBaby = entity.isBaby();
    }

    public ResourceLocation getTextureLocation(CaimanRenderState state) {
        return TEXTURE;
    }
}
