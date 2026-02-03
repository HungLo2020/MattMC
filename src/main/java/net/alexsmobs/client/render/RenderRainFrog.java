package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelRainFrog;
import net.alexsmobs.entity.EntityRainFrog;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderRainFrog extends MobRenderer<EntityRainFrog, RainFrogRenderState, ModelRainFrog> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/rain_frog_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/rain_frog_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/rain_frog_2.png");

    public RenderRainFrog(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelRainFrog(), 0.2F);
    }

    @Override
    public RainFrogRenderState createRenderState() {
        return new RainFrogRenderState();
    }

    @Override
    public void extractRenderState(EntityRainFrog entity, RainFrogRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = entity.getVariant();
        state.burrowProgress = entity.prevBurrowProgress + (entity.burrowProgress - entity.prevBurrowProgress) * partialTick;
        state.danceProgress = entity.prevDanceProgress + (entity.danceProgress - entity.prevDanceProgress) * partialTick;
        state.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTick;
        state.stanceProgress = entity.prevStanceProgress + (entity.stanceProgress - entity.prevStanceProgress) * partialTick;
    }

    protected void scale(RainFrogRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.9F, 0.9F, 0.9F);
    }

    public ResourceLocation getTextureLocation(RainFrogRenderState state) {
        return state.variant == 2 ? TEXTURE_2 : state.variant == 1 ? TEXTURE_1 : TEXTURE_0;
    }
}
