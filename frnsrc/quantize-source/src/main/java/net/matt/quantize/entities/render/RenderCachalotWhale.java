package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelCachalotWhale;
import net.matt.quantize.entities.layer.LayerCachalotWhaleCapturedSquid;
import net.matt.quantize.entities.mobs.EntityCachalotPart;
import net.matt.quantize.entities.mobs.EntityCachalotWhale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCachalotWhale extends MobRenderer<EntityCachalotWhale, ModelCachalotWhale> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/cachalot/cachalot_whale.png");
    private static final ResourceLocation TEXTURE_SLEEPING = new ResourceIdentifier("textures/model/entity/cachalot/cachalot_whale_sleeping.png");
    private static final ResourceLocation TEXTURE_ALBINO = new ResourceIdentifier("textures/model/entity/cachalot/cachalot_whale_albino.png");
    private static final ResourceLocation TEXTURE_ALBINO_SLEEPING = new ResourceIdentifier("textures/model/entity/cachalot/cachalot_whale_albino_sleeping.png");

    public RenderCachalotWhale(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCachalotWhale(), 4.2F);
        this.addLayer(new LayerCachalotWhaleCapturedSquid(this));
    }

    protected void scale(EntityCachalotWhale entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }

    public boolean shouldRender(EntityCachalotWhale livingEntityIn, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(livingEntityIn, camera, camX, camY, camZ)) {
            return true;
        } else {
            for(EntityCachalotPart part : livingEntityIn.whaleParts){
                if(camera.isVisible(part.getBoundingBox())){
                    return true;
                }
            }
            return false;
        }
    }

    public ResourceLocation getTextureLocation(EntityCachalotWhale entity) {
        if(entity.isAlbino()){
            return entity.isSleeping() || entity.isBeached() ? TEXTURE_ALBINO_SLEEPING : TEXTURE_ALBINO;
        }else {
            return entity.isSleeping() || entity.isBeached()  ? TEXTURE_SLEEPING : TEXTURE;
        }
    }
}
