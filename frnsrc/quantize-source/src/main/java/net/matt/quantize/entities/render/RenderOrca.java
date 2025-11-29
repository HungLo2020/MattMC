package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelOrca;
import net.matt.quantize.entities.mobs.EntityOrca;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderOrca extends MobRenderer<EntityOrca, ModelOrca> {
    private static final ResourceLocation TEXTURE_NE = new ResourceIdentifier("textures/model/entity/orca/orca_ne.png");
    private static final ResourceLocation TEXTURE_NW = new ResourceIdentifier("textures/model/entity/orca/orca_nw.png");
    private static final ResourceLocation TEXTURE_SE = new ResourceIdentifier("textures/model/entity/orca/orca_se.png");
    private static final ResourceLocation TEXTURE_SW = new ResourceIdentifier("textures/model/entity/orca/orca_sw.png");

    public RenderOrca(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelOrca(), 1.0F);
    }

    protected void scale(EntityOrca entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(1.3F, 1.3F, 1.3F);
    }


    public ResourceLocation getTextureLocation(EntityOrca entity) {
        return switch (entity.getVariant()) {
            case 0 -> TEXTURE_NE;
            case 1 -> TEXTURE_NW;
            case 2 -> TEXTURE_SE;
            default -> TEXTURE_SW;
        };
    }
}
