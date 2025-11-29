package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelRoadrunner;
import net.matt.quantize.entities.mobs.EntityRoadrunner;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderRoadrunner extends MobRenderer<EntityRoadrunner, ModelRoadrunner> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/roadrunner/roadrunner.png");
    private static final ResourceLocation TEXTURE_MEEP = new ResourceIdentifier("textures/model/entity/roadrunner/roadrunner_meep.png");

    public RenderRoadrunner(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelRoadrunner(), 0.3F);
    }

    public ResourceLocation getTextureLocation(EntityRoadrunner entity) {
        return entity.isMeep() ? TEXTURE_MEEP : TEXTURE;
    }
}
