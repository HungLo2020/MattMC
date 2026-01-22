package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelSeal;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerSealItem;
import com.github.alexthe666.alexsmobs.client.render.state.SealRenderState;
import com.github.alexthe666.alexsmobs.entity.EntitySeal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class RenderSeal extends MobRenderer<EntitySeal, SealRenderState, ModelSeal> {
    private static final ResourceLocation TEXTURE_BROWN_0 = ResourceLocation.withDefaultNamespace("textures/entity/seal/seal_brown_0.png");
    private static final ResourceLocation TEXTURE_BROWN_1 = ResourceLocation.withDefaultNamespace("textures/entity/seal/seal_brown_1.png");
    private static final ResourceLocation TEXTURE_ARCTIC_0 = ResourceLocation.withDefaultNamespace("textures/entity/seal/seal_arctic_0.png");
    private static final ResourceLocation TEXTURE_ARCTIC_1 = ResourceLocation.withDefaultNamespace("textures/entity/seal/seal_arctic_1.png");
    private static final ResourceLocation TEXTURE_ARCTIC_BABY = ResourceLocation.withDefaultNamespace("textures/entity/seal/seal_arctic_baby.png");
    private static final ResourceLocation TEXTURE_TEARS = ResourceLocation.withDefaultNamespace("textures/entity/seal/seal_crying.png");
    private static final ResourceLocation TEXTURE_TONGUE = ResourceLocation.withDefaultNamespace("textures/entity/seal/seal_tongue.png");

    public RenderSeal(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSeal(), 0.45F);
        this.addLayer(new LayerSealItem(this));
        this.addLayer(new SealTearsLayer(this));
    }

    @Override
    public SealRenderState createRenderState() {
        return new SealRenderState();
    }

    @Override
    public void extractRenderState(EntitySeal entity, SealRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.prevBaskProgress = entity.prevBaskProgress;
        renderState.baskProgress = entity.baskProgress;
        renderState.prevSwimAngle = entity.prevSwimAngle;
        renderState.swimAngle = entity.getSwimAngle();
        renderState.prevDigProgress = entity.prevDigProgress;
        renderState.digProgress = entity.digProgress;
        renderState.prevBobbingProgress = entity.prevBobbingProgress;
        renderState.bobbingProgress = entity.bobbingProgress;
        renderState.isTearsEasterEgg = entity.isTearsEasterEgg();
        renderState.entityId = entity.getId();
        renderState.isInWater = entity.isInWater();
        renderState.isArctic = entity.isArctic();
        renderState.variant = entity.getVariant();
    }

    public ResourceLocation getTextureLocation(SealRenderState renderState) {
        if(renderState.isArctic){
            return renderState.isBaby ? TEXTURE_ARCTIC_BABY : renderState.variant == 1 ? TEXTURE_ARCTIC_1 : TEXTURE_ARCTIC_0;
        }
        return renderState.variant == 1 ? TEXTURE_BROWN_1 : TEXTURE_BROWN_0;
    }

    static class SealTearsLayer extends RenderLayer<SealRenderState, ModelSeal> {

        public SealTearsLayer(RenderSeal p_i50928_1_) {
            super(p_i50928_1_);
        }

        public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn, SealRenderState renderState, float limbSwing, float limbSwingAmount) {
            // TODO: Implement tears layer when needed
        }
    }
}
