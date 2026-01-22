package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelRoadrunner;
import com.github.alexthe666.alexsmobs.entity.EntityRoadrunner;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderRoadrunner extends MobRenderer<EntityRoadrunner, RoadrunnerRenderState, ModelRoadrunner> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/roadrunner.png");
    private static final ResourceLocation TEXTURE_MEEP = ResourceLocation.withDefaultNamespace("textures/entity/roadrunner_meep.png");

    public RenderRoadrunner(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelRoadrunner(), 0.3F);
    }

    @Override
    public RoadrunnerRenderState createRenderState() {
        return new RoadrunnerRenderState();
    }

    @Override
    public void extractRenderState(EntityRoadrunner entity, RoadrunnerRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.oFlap = entity.oFlap;
        renderState.wingRotation = entity.wingRotation;
        renderState.destPos = entity.destPos;
        renderState.oFlapSpeed = entity.oFlapSpeed;
        renderState.attackProgress = entity.attackProgress;
        renderState.prevAttackProgress = entity.prevAttackProgress;
        renderState.isMeep = entity.isMeep();
    }

    @Override
    public ResourceLocation getTextureLocation(RoadrunnerRenderState renderState) {
        return renderState.isMeep ? TEXTURE_MEEP : TEXTURE;
    }
}
