package net.alexscaves.client.render.entity;

import net.alexscaves.client.model.GrottoceratopsModel;
import net.alexscaves.server.entity.living.GrottoceratopsEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GrottoceratopsRenderer extends MobRenderer<GrottoceratopsEntity, GrottoceratopsRenderState, GrottoceratopsModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/grottoceratops.png");
    private static final ResourceLocation TEXTURE_BABY = ResourceLocation.withDefaultNamespace("textures/entity/grottoceratops_baby.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.withDefaultNamespace("textures/entity/grottoceratops_retro.png");
    private static final ResourceLocation TEXTURE_RETRO_BABY = ResourceLocation.withDefaultNamespace("textures/entity/grottoceratops_retro_baby.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.withDefaultNamespace("textures/entity/grottoceratops_tectonic.png");
    private static final ResourceLocation TEXTURE_TECTONIC_BABY = ResourceLocation.withDefaultNamespace("textures/entity/grottoceratops_tectonic_baby.png");

    public GrottoceratopsRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new GrottoceratopsModel(), 1.1F);
    }

    @Override
    public GrottoceratopsRenderState createRenderState() {
        return new GrottoceratopsRenderState();
    }

    @Override
    public void extractRenderState(GrottoceratopsEntity entity, GrottoceratopsRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.altSkin = entity.getAltSkin();
        renderState.tailSwingRot = entity.getTailSwingRot(partialTick);
        renderState.buryEggsProgress = entity.getBuryEggsProgress(partialTick);
        renderState.danceProgress = entity.getDanceProgress(partialTick);
        renderState.animation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
    }

    @Override
    public ResourceLocation getTextureLocation(GrottoceratopsRenderState state) {
        return state.altSkin == 1 ? state.isBaby ? TEXTURE_RETRO_BABY : TEXTURE_RETRO : state.altSkin == 2 ? state.isBaby ? TEXTURE_TECTONIC_BABY : TEXTURE_TECTONIC : state.isBaby ? TEXTURE_BABY : TEXTURE;
    }
}

