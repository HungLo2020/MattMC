package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelCapuchinMonkey;
import net.alexsmobs.entity.EntityCapuchinMonkey;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCapuchinMonkey extends MobRenderer<EntityCapuchinMonkey, CapuchinMonkeyRenderState, ModelCapuchinMonkey> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/capuchin_monkey_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/capuchin_monkey_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/capuchin_monkey_2.png");
    private static final ResourceLocation TEXTURE_3 = ResourceLocation.withDefaultNamespace("textures/entity/capuchin_monkey_3.png");

    public RenderCapuchinMonkey(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCapuchinMonkey(), 0.25F);
    }

    @Override
    public CapuchinMonkeyRenderState createRenderState() {
        return new CapuchinMonkeyRenderState();
    }

    @Override
    public void extractRenderState(EntityCapuchinMonkey entity, CapuchinMonkeyRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.hasDart = entity.hasDart();
        state.variant = entity.getVariant();
        state.currentAnimation = entity.getAnimation();
        state.animationTick = entity.getAnimationTick();
        state.isBaby = entity.isBaby();
        state.vehicle = entity.getVehicle();
        state.isPassenger = entity.isPassenger();
    }

    protected void scale(CapuchinMonkeyRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }

    public ResourceLocation getTextureLocation(CapuchinMonkeyRenderState state) {
        return switch (state.variant) {
            case 1 -> TEXTURE_1;
            case 2 -> TEXTURE_2;
            case 3 -> TEXTURE_3;
            default -> TEXTURE_0;
        };
    }
}
