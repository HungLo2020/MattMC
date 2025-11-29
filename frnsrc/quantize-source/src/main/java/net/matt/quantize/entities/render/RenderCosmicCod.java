package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelCosmicCod;
import net.matt.quantize.entities.render.LayerBasicGlow;
import net.matt.quantize.entities.mobs.EntityCosmicCod;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCosmicCod extends MobRenderer<EntityCosmicCod, EntityModel<EntityCosmicCod>> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/cosmic_cod/cosmic_cod.png");
    private static final ResourceLocation TEXTURE_EYES = new ResourceIdentifier("textures/model/entity/cosmic_cod/cosmic_cod_eyes.png");

    public RenderCosmicCod(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCosmicCod(), 0.25F);
        this.addLayer(new LayerBasicGlow<>(this, TEXTURE_EYES));
    }

    public ResourceLocation getTextureLocation(EntityCosmicCod entity) {
        return TEXTURE;
    }
}

