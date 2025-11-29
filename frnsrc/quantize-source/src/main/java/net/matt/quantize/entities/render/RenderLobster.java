package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelLobster;
import net.matt.quantize.entities.mobs.EntityLobster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.matt.quantize.utils.ResourceIdentifier;

public class RenderLobster extends MobRenderer<EntityLobster, ModelLobster> {
    private static final ResourceLocation TEXTURE_RED = new ResourceIdentifier("textures/model/entity/lobster/lobster_red.png");
    private static final ResourceLocation TEXTURE_BLUE = new ResourceIdentifier("textures/model/entity/lobster/lobster_blue.png");
    private static final ResourceLocation TEXTURE_YELLOW = new ResourceIdentifier("textures/model/entity/lobster/lobster_yellow.png");
    private static final ResourceLocation TEXTURE_REDBLUE = new ResourceIdentifier("textures/model/entity/lobster/lobster_redblue.png");
    private static final ResourceLocation TEXTURE_BLACK = new ResourceIdentifier("textures/model/entity/lobster/lobster_black.png");
    private static final ResourceLocation TEXTURE_WHITE = new ResourceIdentifier("textures/model/entity/lobster/lobster_white.png");

    public RenderLobster(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelLobster(), 0.25F);
    }

    protected void scale(EntityLobster entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }


    public ResourceLocation getTextureLocation(EntityLobster entity) {
        return switch (entity.getVariant()) {
            case 1 -> TEXTURE_BLUE;
            case 2 -> TEXTURE_YELLOW;
            case 3 -> TEXTURE_REDBLUE;
            case 4 -> TEXTURE_BLACK;
            case 5 -> TEXTURE_WHITE;
            default -> TEXTURE_RED;
        };
    }
}
