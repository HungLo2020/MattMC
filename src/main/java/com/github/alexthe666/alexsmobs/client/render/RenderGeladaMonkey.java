package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelGeladaMonkey;
import com.github.alexthe666.alexsmobs.entity.EntityGeladaMonkey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderGeladaMonkey extends MobRenderer<EntityGeladaMonkey, GeladaMonkeyRenderState, ModelGeladaMonkey> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/gelada_monkey.png");
    private static final ResourceLocation TEXTURE_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/gelada_monkey_angry.png");
    private static final ResourceLocation TEXTURE_LEADER = ResourceLocation.withDefaultNamespace("textures/entity/gelada_monkey_leader.png");
    private static final ResourceLocation TEXTURE_LEADER_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/gelada_monkey_leader_angry.png");

    public RenderGeladaMonkey(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGeladaMonkey(), 0.45F);
    }

    @Override
    public GeladaMonkeyRenderState createRenderState() {
        return new GeladaMonkeyRenderState();
    }

    @Override
    public void extractRenderState(EntityGeladaMonkey entity, GeladaMonkeyRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.isLeader = entity.isLeader();
        state.isAggro = entity.isAggro();
        state.currentAnimation = entity.getAnimation();
        state.animationTick = entity.getAnimationTick();
        state.isBaby = entity.isBaby();
        state.geladaScale = entity.getGeladaScale();
    }

    protected void scale(GeladaMonkeyRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(state.geladaScale, state.geladaScale, state.geladaScale);
    }

    public ResourceLocation getTextureLocation(GeladaMonkeyRenderState state) {
        return state.isLeader ? state.isAggro ? TEXTURE_LEADER_ANGRY : TEXTURE_LEADER : state.isAggro ? TEXTURE_ANGRY : TEXTURE;
    }
}
