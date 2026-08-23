package net.alexscaves.client.render.entity;

import net.alexscaves.client.model.TremorsaurusModel;
import net.alexscaves.server.entity.living.TremorsaurusEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TremorsaurusRenderer extends MobRenderer<TremorsaurusEntity, TremorsaurusRenderState, TremorsaurusModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/tremorsaurus.png");
    private static final ResourceLocation TEXTURE_PRINCESS = ResourceLocation.withDefaultNamespace("textures/entity/tremorsaurus_princess.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.withDefaultNamespace("textures/entity/tremorsaurus_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.withDefaultNamespace("textures/entity/tremorsaurus_tectonic.png");

    public TremorsaurusRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new TremorsaurusModel(), 1.1F);
    }

    @Override
    public TremorsaurusRenderState createRenderState() {
        return new TremorsaurusRenderState();
    }

    @Override
    public void extractRenderState(TremorsaurusEntity entity, TremorsaurusRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.altSkin = entity.getAltSkin();
        renderState.running = entity.isRunning();
        renderState.heldMobId = entity.getHeldMobId();
        renderState.meterAmount = entity.getMeterAmount();
        renderState.animation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
        renderState.customName = entity.hasCustomName() ? entity.getName().getString() : "";
    }

    @Override
    public ResourceLocation getTextureLocation(TremorsaurusRenderState state) {
        return "princess".equalsIgnoreCase(state.customName) ? TEXTURE_PRINCESS : state.altSkin == 1 ? TEXTURE_RETRO : state.altSkin == 2 ? TEXTURE_TECTONIC : TEXTURE;
    }
}
