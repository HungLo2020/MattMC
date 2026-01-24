package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelCrow;
import com.github.alexthe666.alexsmobs.client.render.RenderCrow;
import com.github.alexthe666.alexsmobs.client.render.state.CrowRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;

public class LayerCrowItem extends RenderLayer<CrowRenderState, ModelCrow> {

    public LayerCrowItem(RenderCrow render) {
        super(render);
    }

    public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn,
            CrowRenderState renderState, float limbSwing, float limbSwingAmount) {
        // Item rendering layer - simplified for now
        // TODO: Implement item rendering when render state has item data
    }
}
