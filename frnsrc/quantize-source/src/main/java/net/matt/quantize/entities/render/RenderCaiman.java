package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelCaiman;
import net.matt.quantize.entities.mobs.EntityCaiman;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCaiman extends MobRenderer<EntityCaiman, ModelCaiman> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/caiman.png");

    public RenderCaiman(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCaiman(), 0.4F);
    }

    public ResourceLocation getTextureLocation(EntityCaiman entity) {
        return TEXTURE;
    }
}
