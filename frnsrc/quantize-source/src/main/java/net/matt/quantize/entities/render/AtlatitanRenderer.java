package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.AtlatitanModel;
import net.matt.quantize.render.QRenderTypes;
import net.matt.quantize.entities.layer.AtlatitanRiderLayer;
import net.matt.quantize.entities.mobs.AtlatitanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.entity.PartEntity;
import net.matt.quantize.render.renderer.CustomBookEntityRenderer;

import javax.annotation.Nullable;

public class AtlatitanRenderer extends MobRenderer<AtlatitanEntity, AtlatitanModel> implements CustomBookEntityRenderer {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/atlatitan/atlatitan.png");
    private static final ResourceLocation TEXTURE_RETRO = new ResourceIdentifier("textures/model/entity/atlatitan/atlatitan_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = new ResourceIdentifier("textures/model/entity/atlatitan/atlatitan_tectonic.png");

    private boolean sepia;

    public AtlatitanRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new AtlatitanModel(), 4.0F);
        this.addLayer(new AtlatitanRiderLayer(this));
    }

    protected void scale(AtlatitanEntity mob, PoseStack matrixStackIn, float partialTicks) {
    }


    @Nullable
    protected RenderType getRenderType(AtlatitanEntity mob, boolean normal, boolean translucent, boolean outline) {
        ResourceLocation resourcelocation = this.getTextureLocation(mob);
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(resourcelocation);
        } else if (normal) {
            return sepia ? QRenderTypes.getBookWidget(resourcelocation, true) : RenderType.entityCutoutNoCull(resourcelocation);
        } else {
            return outline ? RenderType.outline(resourcelocation) : null;
        }
    }

    public ResourceLocation getTextureLocation(AtlatitanEntity entity) {
        return entity.getAltSkin() == 2 ? TEXTURE_TECTONIC : entity.getAltSkin() == 1 ? TEXTURE_RETRO : TEXTURE;
    }

    public void render(AtlatitanEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource source, int packedLight) {
        if(sepia){
            this.model.straighten = true;
        }
        super.render(entity, entityYaw, partialTicks, poseStack, source, packedLight);
        if(sepia){
            this.model.straighten = false;
        }
    }

    @Override
    public void setSepiaFlag(boolean sepiaFlag) {
        this.sepia = sepiaFlag;
    }

    public boolean shouldRender(AtlatitanEntity entity, Frustum camera, double x, double y, double z) {
        if (super.shouldRender(entity, camera, x, y, z)) {
            return true;
        } else {
            for (PartEntity part : entity.getParts()) {
                if (camera.isVisible(part.getBoundingBoxForCulling())) {
                    return true;
                }
            }
            return false;
        }
    }
}

