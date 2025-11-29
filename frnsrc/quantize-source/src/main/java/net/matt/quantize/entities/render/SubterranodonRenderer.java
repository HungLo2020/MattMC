package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.SubterranodonModel;
import net.matt.quantize.entities.layer.SubterranodonRiderLayer;
import net.matt.quantize.entities.mobs.SubterranodonEntity;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SubterranodonRenderer extends MobRenderer<SubterranodonEntity, SubterranodonModel> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/subterranodon/subterranodon.png");
    private static final ResourceLocation TEXTURE_RETRO = new ResourceIdentifier("textures/model/entity/subterranodon/subterranodon_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = new ResourceIdentifier("textures/model/entity/subterranodon/subterranodon_tectonic.png");

    public SubterranodonRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new SubterranodonModel(), 0.5F);
        this.addLayer(new SubterranodonRiderLayer(this));

    }

    public ResourceLocation getTextureLocation(SubterranodonEntity entity) {
        return entity.getAltSkin() == 1 ? TEXTURE_RETRO : entity.getAltSkin() == 2 ? TEXTURE_TECTONIC : TEXTURE;
    }
}

