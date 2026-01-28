package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelMimicube;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerMimicubeHeldItem;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerMimicubeHelmet;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerMimicubeTexture;
import com.github.alexthe666.alexsmobs.entity.EntityMimicube;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderMimicube extends MobRenderer<EntityMimicube, MimicubeRenderState, ModelMimicube> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/mimicube.png");

    public RenderMimicube(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelMimicube(), 0.5F);
        this.addLayer(new LayerMimicubeHelmet(this, renderManagerIn));
        this.addLayer(new LayerMimicubeHeldItem(this));
        this.addLayer(new LayerMimicubeTexture(this));
    }

    @Override
    public MimicubeRenderState createRenderState() {
        return new MimicubeRenderState();
    }

    @Override
    public void extractRenderState(EntityMimicube entity, MimicubeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.squishFactor = entity.prevSquishFactor + (entity.squishFactor - entity.prevSquishFactor) * partialTick;
        state.squishAmount = entity.squishAmount;
    }

    protected void scale(MimicubeRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(MimicubeRenderState state) {
        return TEXTURE;
    }
}
