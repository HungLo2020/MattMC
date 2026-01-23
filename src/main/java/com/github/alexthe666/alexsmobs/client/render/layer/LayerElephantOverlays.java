package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelElephant;
import com.github.alexthe666.alexsmobs.client.render.ElephantRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderElephant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public class LayerElephantOverlays extends RenderLayer<ElephantRenderState, ModelElephant> {

    private static final ResourceLocation[] ELEPHANT_DECOR_TEXTURES = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/white.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/orange.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/magenta.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/light_blue.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/yellow.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/lime.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/pink.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/gray.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/light_gray.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/cyan.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/purple.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/blue.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/brown.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/green.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/red.png"), 
        ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/black.png")
    };
    private static final ResourceLocation TRADER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/elephant/decor/trader.png");

    private static final ResourceLocation TEXTURE_CHEST = ResourceLocation.withDefaultNamespace("textures/entity/elephant/elephant_chest.png");
    private final ModelElephant model = new ModelElephant(0.5F);

    public LayerElephantOverlays(RenderElephant renderElephant) {
        super(renderElephant);
    }

    public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, ElephantRenderState state, float limbSwing, float limbSwingAmount) {
        if(state.chested){
            VertexConsumer ivertexbuilder = bufferIn.getBuffer(RenderType.entityCutout(TEXTURE_CHEST));
            this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn, state.getOverlayCoords(), -1);
        }
        DyeColor color = state.carpetColor >= 0 && state.carpetColor < 16 ? DyeColor.byId(state.carpetColor) : null;
        // Note: Trader status is not in render state, would need to add if needed
        if(color != null) {
            ResourceLocation texture = ELEPHANT_DECOR_TEXTURES[color.getId()];

            this.getParentModel().copyPropertiesTo(this.model);
            this.model.setupAnim(state);
            VertexConsumer vertexConsumer = bufferIn.getBuffer(RenderType.entityCutoutNoCull(texture));
            this.model.renderToBuffer(matrixStackIn, vertexConsumer, packedLightIn, OverlayTexture.NO_OVERLAY, -1);
        }
    }
}
