package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelAnteater;
import net.matt.quantize.entities.layer.LayerAnteaterBaby;
import net.matt.quantize.entities.layer.LayerAnteaterTongueItem;
import net.matt.quantize.entities.mobs.EntityAnteater;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderAnteater extends MobRenderer<EntityAnteater, ModelAnteater> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/anteater/anteater.png");
    private static final ResourceLocation TEXTURE_PETER = new ResourceIdentifier("textures/model/entity/anteater/anteater_peter.png");

    public RenderAnteater(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAnteater(), 0.45F);
        this.addLayer(new LayerAnteaterTongueItem(this));
        this.addLayer(new LayerAnteaterBaby(this));
    }

    public boolean shouldRender(EntityAnteater anteater, Frustum p_225626_2_, double p_225626_3_, double p_225626_5_, double p_225626_7_) {
        if(anteater.isBaby() && anteater.isPassenger() && anteater.getVehicle() instanceof EntityAnteater){
            return false;
        }
        return super.shouldRender(anteater, p_225626_2_, p_225626_3_, p_225626_5_, p_225626_7_);
    }

    protected void scale(EntityAnteater entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }


    public ResourceLocation getTextureLocation(EntityAnteater entity) {
        return entity.isPeter() ? TEXTURE_PETER : TEXTURE;
    }
}
