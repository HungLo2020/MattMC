package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelAnteater;
import com.github.alexthe666.alexsmobs.client.model.ModelLeafcutterAnt;
import com.github.alexthe666.alexsmobs.client.render.AnteaterRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderAnteater;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class LayerAnteaterTongueItem extends RenderLayer<AnteaterRenderState, ModelAnteater> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/leafcutter_ant.png");
    private final ModelLeafcutterAnt ANT_MODEL = new ModelLeafcutterAnt();
    private final RenderAnteater renderer;

    public LayerAnteaterTongueItem(RenderAnteater render) {
        super(render);
        this.renderer = render;
    }

    @Override
    public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, AnteaterRenderState renderState, float f1, float f2) {
        // Note: In the render state architecture, we don't have direct access to items or entity state needed for this layer
        // This would need significant refactoring to work with the new architecture
        // For a full implementation, we would need to add item and ant-on-tongue state to AnteaterRenderState
        // For now, this is a stub that won't render anything
    }

    protected void translateToTongue(PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().head.translateAndRotate(matrixStack);
        this.getParentModel().snout.translateAndRotate(matrixStack);
        this.getParentModel().tongue1.translateAndRotate(matrixStack);
        this.getParentModel().tongue2.translateAndRotate(matrixStack);
    }
}
