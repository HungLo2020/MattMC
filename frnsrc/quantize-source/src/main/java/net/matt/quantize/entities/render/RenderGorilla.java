package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelGorilla;
import net.matt.quantize.entities.layer.LayerGorillaItem;
import net.matt.quantize.entities.mobs.EntityGorilla;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderGorilla extends MobRenderer<EntityGorilla, ModelGorilla> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/gorilla/gorilla.png");
    private static final ResourceLocation TEXTURE_SILVERBACK = new ResourceIdentifier("textures/model/entity/gorilla/gorilla_silverback.png");
    private static final ResourceLocation TEXTURE_DK = new ResourceIdentifier("textures/model/entity/gorilla/gorilla_dk.png");
    private static final ResourceLocation TEXTURE_FUNKY = new ResourceIdentifier("textures/model/entity/gorilla/gorilla_funky.png");

    public RenderGorilla(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGorilla(), 0.7F);
        this.addLayer(new LayerGorillaItem(this));
    }

    protected void scale(EntityGorilla entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(entitylivingbaseIn.getGorillaScale(), entitylivingbaseIn.getGorillaScale(), entitylivingbaseIn.getGorillaScale());
    }

    public ResourceLocation getTextureLocation(EntityGorilla entity) {
        return entity.isFunkyKong() ? TEXTURE_FUNKY : entity.isDonkeyKong() ? TEXTURE_DK : entity.isSilverback() ? TEXTURE_SILVERBACK : TEXTURE;
    }
}
