package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelJerboa;
import net.alexsmobs.client.render.state.JerboaRenderState;
import net.alexsmobs.entity.EntityJerboa;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderJerboa extends MobRenderer<EntityJerboa, JerboaRenderState, ModelJerboa> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/jerboa.png");
    private static final ResourceLocation TEXTURE_SLEEPING = ResourceLocation.withDefaultNamespace("textures/entity/jerboa_sleeping.png");

    public RenderJerboa(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelJerboa(), 0.1F);
    }

    @Override
    public JerboaRenderState createRenderState() {
        return new JerboaRenderState();
    }

    @Override
    public void extractRenderState(EntityJerboa entity, JerboaRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.jumpProgress = entity.prevJumpProgress + (entity.jumpProgress - entity.prevJumpProgress) * partialTick;
        renderState.reboundProgress = entity.prevReboundProgress + (entity.reboundProgress - entity.prevReboundProgress) * partialTick;
        renderState.begProgress = entity.prevBegProgress + (entity.begProgress - entity.prevBegProgress) * partialTick;
        renderState.sleepProgress = entity.prevSleepProgress + (entity.sleepProgress - entity.prevSleepProgress) * partialTick;
        renderState.isSleeping = entity.isSleeping();
    }

    protected void scale(JerboaRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }

    public ResourceLocation getTextureLocation(JerboaRenderState renderState) {
        return renderState.isSleeping ? TEXTURE_SLEEPING : TEXTURE;
    }
}
