package net.matt.quantize.entities.layer;

import net.matt.quantize.entities.models.ModelElephant;
import net.matt.quantize.entities.render.RenderElephant;
import net.matt.quantize.entities.mobs.EntityElephant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public class LayerElephantOverlays extends RenderLayer<EntityElephant, ModelElephant> {

    private static final ResourceLocation[] ELEPHANT_DECOR_TEXTURES = new ResourceIdentifier[]{new ResourceIdentifier("textures/model/entity/elephant/decor/white.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/orange.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/magenta.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/light_blue.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/yellow.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/lime.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/pink.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/gray.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/light_gray.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/cyan.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/purple.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/blue.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/brown.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/green.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/red.png"), new ResourceIdentifier("textures/model/entity/elephant/decor/black.png")};
    private static final ResourceLocation TRADER_TEXTURE = new ResourceIdentifier("textures/model/entity/elephant/decor/trader.png");

    private static final ResourceLocation TEXTURE_CHEST = new ResourceIdentifier("textures/model/entity/elephant/elephant_chest.png");
    private final ModelElephant model = new ModelElephant(0.5F);

    public LayerElephantOverlays(RenderElephant renderElephant) {
        super(renderElephant);
    }

    public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, EntityElephant elephant, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if(elephant.isChested()){
            VertexConsumer ivertexbuilder = bufferIn.getBuffer(RenderType.entityCutout(TEXTURE_CHEST));
            this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn, LivingEntityRenderer.getOverlayCoords(elephant, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
        }
        DyeColor lvt_11_1_ = elephant.getColor();
        if(lvt_11_1_ != null || elephant.isTrader()) {
            ResourceLocation lvt_12_3_;
            if (!elephant.isTrader()) {
                lvt_12_3_ = ELEPHANT_DECOR_TEXTURES[lvt_11_1_.getId()];
            }else{
                lvt_12_3_ = TRADER_TEXTURE;
            }

            ((ModelElephant) this.getParentModel()).copyPropertiesTo(this.model);
            this.model.setupAnim(elephant, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer lvt_13_1_ = bufferIn.getBuffer(RenderType.entityCutoutNoCull(lvt_12_3_));
            this.model.renderToBuffer(matrixStackIn, lvt_13_1_, packedLightIn, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
