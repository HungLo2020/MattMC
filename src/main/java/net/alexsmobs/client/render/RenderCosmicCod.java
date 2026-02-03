package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelCosmicCod;
import net.alexsmobs.client.render.layer.LayerBasicGlow;
import net.alexsmobs.client.render.state.CosmicCodRenderState;
import net.alexsmobs.entity.EntityCosmicCod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCosmicCod extends MobRenderer<EntityCosmicCod, CosmicCodRenderState, EntityModel<CosmicCodRenderState>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cosmic_cod.png");
    private static final ResourceLocation TEXTURE_EYES = ResourceLocation.withDefaultNamespace("textures/entity/cosmic_cod_eyes.png");

    public RenderCosmicCod(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCosmicCod(), 0.25F);
        this.addLayer(new LayerBasicGlow<>(this, TEXTURE_EYES));
    }

    @Override
    public CosmicCodRenderState createRenderState() {
        return new CosmicCodRenderState();
    }

    @Override
    public void extractRenderState(EntityCosmicCod entity, CosmicCodRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.fishPitch = entity.getFishPitch();
        renderState.prevFishPitch = entity.prevFishPitch;
    }

    public ResourceLocation getTextureLocation(CosmicCodRenderState renderState) {
        return TEXTURE;
    }
}

