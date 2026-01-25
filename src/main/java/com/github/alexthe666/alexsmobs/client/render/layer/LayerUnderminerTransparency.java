package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelUnderminerWrapper;
import com.github.alexthe666.alexsmobs.client.render.AMColorUtil;
import com.github.alexthe666.alexsmobs.client.render.AMRenderTypes;
import com.github.alexthe666.alexsmobs.client.render.RenderUnderminer;
import com.github.alexthe666.alexsmobs.client.render.UnderminerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Transparency layer for the underminer that applies alpha based on hiding progress
 */
public class LayerUnderminerTransparency extends RenderLayer<UnderminerRenderState, ModelUnderminerWrapper> {
    
    private final RenderUnderminer renderer;

    public LayerUnderminerTransparency(RenderUnderminer renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, 
                       UnderminerRenderState renderState, float f, float g) {
        // Don't render if fully hidden
        if (renderState.isFullyHidden) {
            return;
        }
        
        // Calculate alpha based on hiding progress
        float alpha = renderer.getAlphaForRender(renderState);
        
        // Pack color with alpha (white RGB, custom alpha)
        int color = AMColorUtil.packColor(1.0F, 1.0F, 1.0F, alpha);
        
        // Get the texture for this variant
        ResourceLocation texture = renderer.getTextureLocation(renderState);
        
        // Get the appropriate model and render it with transparency
        ModelUnderminerWrapper wrapperModel = this.getParentModel();
        if (renderState.isDwarf) {
            // Render dwarf model with transparency
            submitNodeCollector.order(0).submitModel(
                wrapperModel.getDwarfModel(), 
                renderState, 
                poseStack, 
                AMRenderTypes.getUnderminer(texture),
                packedLight,
                OverlayTexture.NO_OVERLAY, 
                color, 
                null, 
                renderState.outlineColor, 
                null
            );
        } else {
            // Render tall (humanoid) model with transparency
            submitNodeCollector.order(0).submitModel(
                wrapperModel.getTallModel(), 
                renderState, 
                poseStack, 
                AMRenderTypes.getUnderminer(texture),
                packedLight,
                OverlayTexture.NO_OVERLAY, 
                color, 
                null, 
                renderState.outlineColor, 
                null
            );
        }
    }
}
