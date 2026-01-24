package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.ModelLobster;
import net.minecraft.world.entity.animal.EntityLobster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.LobsterRenderState;
import net.minecraft.resources.ResourceLocation;

public class RenderLobster extends MobRenderer<EntityLobster, LobsterRenderState, ModelLobster> {
    private static final ResourceLocation TEXTURE_RED = ResourceLocation.withDefaultNamespace("textures/entity/lobster_red.png");
    private static final ResourceLocation TEXTURE_BLUE = ResourceLocation.withDefaultNamespace("textures/entity/lobster_blue.png");
    private static final ResourceLocation TEXTURE_YELLOW = ResourceLocation.withDefaultNamespace("textures/entity/lobster_yellow.png");
    private static final ResourceLocation TEXTURE_REDBLUE = ResourceLocation.withDefaultNamespace("textures/entity/lobster_redblue.png");
    private static final ResourceLocation TEXTURE_BLACK = ResourceLocation.withDefaultNamespace("textures/entity/lobster_black.png");
    private static final ResourceLocation TEXTURE_WHITE = ResourceLocation.withDefaultNamespace("textures/entity/lobster_white.png");

    public RenderLobster(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelLobster(), 0.25F);
    }

    public LobsterRenderState createRenderState() {
        return new LobsterRenderState();
    }

    public void extractRenderState(EntityLobster entity, LobsterRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = entity.getVariant();
        state.prevAttackProgress = entity.prevAttackProgress;
        state.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTick;
    }

    public ResourceLocation getTextureLocation(LobsterRenderState state) {
        return switch (state.variant) {
            case 1 -> TEXTURE_BLUE;
            case 2 -> TEXTURE_YELLOW;
            case 3 -> TEXTURE_REDBLUE;
            case 4 -> TEXTURE_BLACK;
            case 5 -> TEXTURE_WHITE;
            default -> TEXTURE_RED;
        };
    }
}

