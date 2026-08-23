package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelSunbird;
import net.alexsmobs.client.render.layer.LayerSunbirdScorch;
import net.alexsmobs.entity.EntitySunbird;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderSunbird extends MobRenderer<EntitySunbird, SunbirdRenderState, ModelSunbird> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/sunbird.png");
    private static final ResourceLocation TEXTURE_GLOW = ResourceLocation.withDefaultNamespace("textures/entity/sunbird_glow.png");

    public RenderSunbird(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSunbird(), 0.5F);
        this.addLayer(new LayerSunbirdScorch(this));
    }

    @Override
    public SunbirdRenderState createRenderState() {
        return new SunbirdRenderState();
    }

    @Override
    public void extractRenderState(EntitySunbird entity, SunbirdRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.birdPitch = entity.prevBirdPitch + (entity.birdPitch - entity.prevBirdPitch) * partialTick;
        state.scorchProgress = entity.getScorchProgress(partialTick);
    }

    protected void scale(SunbirdRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(SunbirdRenderState state) {
        return TEXTURE;
    }

}
