package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelGrizzlyBear;
import net.matt.quantize.entities.layer.LayerGrizzlyHoney;
import net.matt.quantize.entities.layer.LayerGrizzlyItem;
import net.matt.quantize.entities.mobs.EntityGrizzlyBear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.matt.quantize.modules.entities.AMRenderTypes;

public class RenderGrizzlyBear extends MobRenderer<EntityGrizzlyBear, ModelGrizzlyBear> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/grizzly_bear/grizzly_bear.png");
    private static final ResourceLocation TEXTURE_SNOWY = new ResourceIdentifier("textures/model/entity/grizzly_bear/grizzly_bear_snowy.png");
    public static final ResourceLocation TEXTURE_FREDDY = new ResourceIdentifier("textures/model/entity/grizzly_bear/grizzly_bear_freddy.png");
    private static final ResourceLocation TEXTURE_FREDDY_EYES = new ResourceIdentifier("textures/model/entity/grizzly_bear/grizzly_bear_freddy_eyes.png");

    public RenderGrizzlyBear(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGrizzlyBear(), 0.8F);
        this.addLayer(new LayerFreddyEyes());
        this.addLayer(new LayerGrizzlyHoney(this));
        this.addLayer(new LayerSnow());
        this.addLayer(new LayerGrizzlyItem(this));
    }

    public boolean shouldRender(EntityGrizzlyBear livingEntityIn, Frustum camera, double camX, double camY, double camZ) {
        if (livingEntityIn.getAprilFoolsFlag() == 5) {
            return false;
        }
        return super.shouldRender(livingEntityIn, camera, camX, camY, camZ);
    }

    public ResourceLocation getTextureLocation(EntityGrizzlyBear entity) {
        return entity.isFreddy() ? TEXTURE_FREDDY : TEXTURE;
    }

    class LayerSnow extends RenderLayer<EntityGrizzlyBear, ModelGrizzlyBear> {

        public LayerSnow() {
            super(RenderGrizzlyBear.this);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, EntityGrizzlyBear entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entitylivingbaseIn.isSnowy()) {
                VertexConsumer ivertexbuilder = bufferIn.getBuffer(RenderType.entityCutoutNoCull(TEXTURE_SNOWY));
                this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn, LivingEntityRenderer.getOverlayCoords(entitylivingbaseIn, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    class LayerFreddyEyes extends RenderLayer<EntityGrizzlyBear, ModelGrizzlyBear> {

        public LayerFreddyEyes() {
            super(RenderGrizzlyBear.this);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, EntityGrizzlyBear entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entitylivingbaseIn.getAprilFoolsFlag() == 4 && entitylivingbaseIn.tickCount % 6 <= 2) {
                VertexConsumer ivertexbuilder = bufferIn.getBuffer(AMRenderTypes.getEyesNoFog(TEXTURE_FREDDY_EYES));
                this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn, LivingEntityRenderer.getOverlayCoords(entitylivingbaseIn, 0.0F), 1.0F, 1.0F, 1.0F, 0.1F);
            }
        }
    }
}
