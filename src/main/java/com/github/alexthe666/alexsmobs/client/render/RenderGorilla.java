package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelGorilla;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerGorillaItem;
import com.github.alexthe666.alexsmobs.entity.EntityGorilla;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderGorilla extends MobRenderer<EntityGorilla, GorillaRenderState, ModelGorilla> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/gorilla.png");
    private static final ResourceLocation TEXTURE_SILVERBACK = ResourceLocation.withDefaultNamespace("textures/entity/gorilla_silverback.png");
    private static final ResourceLocation TEXTURE_DK = ResourceLocation.withDefaultNamespace("textures/entity/gorilla_dk.png");
    private static final ResourceLocation TEXTURE_FUNKY = ResourceLocation.withDefaultNamespace("textures/entity/gorilla_funky.png");

    public RenderGorilla(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGorilla(), 0.7F);
        this.addLayer(new LayerGorillaItem(this));
    }

    @Override
    public GorillaRenderState createRenderState() {
        return new GorillaRenderState();
    }

    @Override
    public void extractRenderState(EntityGorilla entity, GorillaRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        net.minecraft.client.renderer.entity.state.HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
        state.standProgress = entity.prevStandProgress + (entity.standProgress - entity.prevStandProgress) * partialTick;
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.gorillaScale = entity.getGorillaScale();
        state.isSilverback = entity.isSilverback();
        state.isDonkeyKong = entity.isDonkeyKong();
        state.isFunkyKong = entity.isFunkyKong();
        state.isBaby = entity.isBaby();
        state.animationTick = entity.getAnimationTick();
        state.name = entity.getName().getString();
    }

    protected void scale(GorillaRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(state.gorillaScale, state.gorillaScale, state.gorillaScale);
    }

    public ResourceLocation getTextureLocation(GorillaRenderState state) {
        return state.isFunkyKong ? TEXTURE_FUNKY : state.isDonkeyKong ? TEXTURE_DK : state.isSilverback ? TEXTURE_SILVERBACK : TEXTURE;
    }
}
