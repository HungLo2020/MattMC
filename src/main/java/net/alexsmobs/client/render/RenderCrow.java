package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelCrow;
import net.alexsmobs.client.render.layer.LayerCrowItem;
import net.alexsmobs.client.render.state.CrowRenderState;
import net.alexsmobs.entity.EntityCrow;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCrow extends MobRenderer<EntityCrow, CrowRenderState, ModelCrow> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/crow.png");

    public RenderCrow(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCrow(), 0.2F);
        this.addLayer(new LayerCrowItem(this));
    }

    @Override
    public CrowRenderState createRenderState() {
        return new CrowRenderState();
    }

    @Override
    public void extractRenderState(EntityCrow entity, CrowRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTick;
        renderState.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTick;
        renderState.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        renderState.hasItemInBeak = !entity.getMainHandItem().isEmpty();
    }

    protected void scale(CrowRenderState renderState, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(CrowRenderState renderState) {
        return TEXTURE;
    }
}
