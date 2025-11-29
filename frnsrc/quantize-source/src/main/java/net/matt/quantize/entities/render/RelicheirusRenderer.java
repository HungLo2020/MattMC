package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.RelicheirusModel;
import net.matt.quantize.entities.layer.RelicheirusHeldTrilocarisLayer;
import net.matt.quantize.entities.mobs.RelicheirusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RelicheirusRenderer extends MobRenderer<RelicheirusEntity, RelicheirusModel> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/relicheirus/relicheirus.png");
    private static final ResourceLocation TEXTURE_RETRO = new ResourceIdentifier("textures/model/entity/relicheirus/relicheirus_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = new ResourceIdentifier("textures/model/entity/relicheirus/relicheirus_tectonic.png");

    public RelicheirusRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new RelicheirusModel(), 1.0F);
        this.addLayer(new RelicheirusHeldTrilocarisLayer(this));
    }

    protected void scale(RelicheirusEntity mob, PoseStack matrixStackIn, float partialTicks) {
    }

    public ResourceLocation getTextureLocation(RelicheirusEntity entity) {
        return entity.getAltSkin() == 2 ? TEXTURE_TECTONIC : entity.getAltSkin() == 1 ? TEXTURE_RETRO : TEXTURE;
    }
}

