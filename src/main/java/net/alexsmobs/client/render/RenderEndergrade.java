package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelEndergrade;
import net.alexsmobs.client.render.layer.LayerEndergradeSaddle;
import net.alexsmobs.entity.EntityEndergrade;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class RenderEndergrade extends MobRenderer<EntityEndergrade, EndergadeRenderState, ModelEndergrade> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/endergrade.png");

    public RenderEndergrade(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelEndergrade(), 0.6F);
        this.addLayer(new LayerEndergradeSaddle(this));
    }

    @Override
    public EndergadeRenderState createRenderState() {
        return new EndergadeRenderState();
    }

    @Override
    public void extractRenderState(EntityEndergrade entity, EndergadeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.tartigradePitch = entity.prevTartigradePitch + (entity.tartigradePitch - entity.prevTartigradePitch) * partialTick;
        state.biteProgress = entity.prevBiteProgress + (entity.biteProgress - entity.prevBiteProgress) * partialTick;
        state.isSaddled = entity.isSaddled();
    }

    @Nullable
    @Override
    protected RenderType getRenderType(EndergadeRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
        ResourceLocation resourcelocation = this.getTextureLocation(state);
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(resourcelocation);
        } else if (bodyVisible) {
            return RenderType.entityTranslucent(resourcelocation);
        } else {
            return glowing ? RenderType.outline(resourcelocation) : null;
        }
    }

    protected void scale(EndergadeRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(1.2F, 1.2F, 1.2F);
    }


    public ResourceLocation getTextureLocation(EndergadeRenderState state) {
        return TEXTURE;
    }
}
