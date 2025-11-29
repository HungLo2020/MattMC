package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.TremorsaurusModel;
import net.matt.quantize.entities.layer.TremorsaurusHeldMobLayer;
import net.matt.quantize.entities.layer.TremorsaurusRiderLayer;
import net.matt.quantize.entities.mobs.TremorsaurusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TremorsaurusRenderer extends MobRenderer<TremorsaurusEntity, TremorsaurusModel> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/tremorsaurus/tremorsaurus.png");
    private static final ResourceLocation TEXTURE_PRINCESS = new ResourceIdentifier("textures/model/entity/tremorsaurus/tremorsaurus_princess.png");
    private static final ResourceLocation TEXTURE_RETRO = new ResourceIdentifier("textures/model/entity/tremorsaurus/tremorsaurus_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = new ResourceIdentifier("textures/model/entity/tremorsaurus/tremorsaurus_tectonic.png");

    public TremorsaurusRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new TremorsaurusModel(), 1.1F);
        this.addLayer(new TremorsaurusRiderLayer(this));
        this.addLayer(new TremorsaurusHeldMobLayer(this));
    }

    protected void scale(TremorsaurusEntity mob, PoseStack matrixStackIn, float partialTicks) {
    }

    public ResourceLocation getTextureLocation(TremorsaurusEntity entity) {
        return entity.hasCustomName() && "princess".equalsIgnoreCase(entity.getName().getString()) ? TEXTURE_PRINCESS : entity.getAltSkin() == 1 ? TEXTURE_RETRO : entity.getAltSkin() == 2 ? TEXTURE_TECTONIC : TEXTURE;
    }


}

