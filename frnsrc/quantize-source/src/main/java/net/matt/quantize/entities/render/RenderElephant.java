package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelElephant;
import net.matt.quantize.entities.layer.LayerElephantItem;
import net.matt.quantize.entities.layer.LayerElephantOverlays;
import net.matt.quantize.entities.mobs.EntityElephant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderElephant extends MobRenderer<EntityElephant, ModelElephant> {
    private static final ResourceLocation TEXTURE_TUSK = new ResourceIdentifier("textures/model/entity/elephant/elephant_tusks.png");
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/elephant/elephant.png");

    public RenderElephant(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelElephant(0), 1.4F);
        this.addLayer(new LayerElephantOverlays(this));
        this.addLayer(new LayerElephantItem(this));
    }

    protected void scale(EntityElephant entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
       if(entitylivingbaseIn.isTusked()){
           matrixStackIn.scale(1.1F, 1.1F, 1.1F);
       }
    }


    public ResourceLocation getTextureLocation(EntityElephant entity) {
        return entity.isTusked() && !entity.isBaby() ? TEXTURE_TUSK : TEXTURE;
    }
}
