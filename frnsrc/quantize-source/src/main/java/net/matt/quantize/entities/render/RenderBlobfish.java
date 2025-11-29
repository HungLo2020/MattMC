package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelBlobfish;
import net.matt.quantize.entities.models.ModelBlobfishDepressurized;
import net.matt.quantize.entities.mobs.EntityBlobfish;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderBlobfish extends MobRenderer<EntityBlobfish, EntityModel<EntityBlobfish>> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/blobfish/blobfish.png");
    private static final ResourceLocation TEXTURE_DEPRESSURIZED = new ResourceIdentifier("textures/model/entity/blobfish/blobfish_depressurized.png");
    private final ModelBlobfish modelFish = new ModelBlobfish();
    private final ModelBlobfishDepressurized modelDepressurized = new ModelBlobfishDepressurized();

    public RenderBlobfish(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelBlobfish(), 0.35F);
    }

    protected void scale(EntityBlobfish entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        if(entitylivingbaseIn.isDepressurized()){
            model = modelDepressurized;
        }else{
            model = modelFish;
        }
        matrixStackIn.scale(entitylivingbaseIn.getBlobfishScale(), entitylivingbaseIn.getBlobfishScale(), entitylivingbaseIn.getBlobfishScale());
    }


    public ResourceLocation getTextureLocation(EntityBlobfish entity) {
        return entity.isDepressurized() ? TEXTURE_DEPRESSURIZED : TEXTURE;
    }
}
