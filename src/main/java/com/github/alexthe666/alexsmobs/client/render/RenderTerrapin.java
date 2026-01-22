package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelTerrapin;
import com.github.alexthe666.alexsmobs.client.render.state.TerrapinRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityTerrapin;
import com.github.alexthe666.alexsmobs.entity.util.TerrapinTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RenderTerrapin extends MobRenderer<EntityTerrapin, TerrapinRenderState, ModelTerrapin> {

    private static final ResourceLocation[] SHELL_TEXTURES = {
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_shell_pattern_0.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_shell_pattern_1.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_shell_pattern_2.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_shell_pattern_3.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_shell_pattern_4.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_shell_pattern_5.png")
    };
    private static final ResourceLocation[] SKIN_PATTERN_TEXTURES = {
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_skin_pattern_0.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_skin_pattern_1.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_skin_pattern_2.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/terrapin/overlay/terrapin_skin_pattern_3.png")
    };

    public RenderTerrapin(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelTerrapin(), 0.3F);
        this.addLayer(new TurtleOverlayLayer(this, 0));
        this.addLayer(new TurtleOverlayLayer(this, 1));
        this.addLayer(new TurtleOverlayLayer(this, 2));
    }

    @Override
    public TerrapinRenderState createRenderState() {
        return new TerrapinRenderState();
    }

    @Override
    public void extractRenderState(EntityTerrapin entity, TerrapinRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.turtleType = entity.getTurtleType();
        renderState.shellType = entity.getShellType();
        renderState.skinType = entity.getSkinType();
        renderState.turtleColor = entity.getTurtleColor();
        renderState.shellColor = entity.getShellColor();
        renderState.skinColor = entity.getSkinColor();
        renderState.isKoopa = entity.isKoopa();
        renderState.clientSpin = entity.clientSpin;
        renderState.spinCounter = entity.spinCounter;
        renderState.prevSwimProgress = entity.prevSwimProgress;
        renderState.swimProgress = entity.swimProgress;
        renderState.prevRetreatProgress = entity.prevRetreatProgress;
        renderState.retreatProgress = entity.retreatProgress;
        renderState.prevSpinProgress = entity.prevSpinProgress;
        renderState.spinProgress = entity.spinProgress;
        renderState.isSpinning = entity.isSpinning();
        renderState.hasRetreated = entity.hasRetreated();
        renderState.partialTick = partialTick;
    }

    public ResourceLocation getTextureLocation(TerrapinRenderState renderState) {
        if(renderState.isKoopa){
            return TerrapinTypes.KOOPA.getTexture();
        }
        return renderState.turtleType.getTexture();
    }

    protected void setupRotations(TerrapinRenderState renderState, PoseStack stack, float yawIn, float tickDelta) {
        if (this.isShaking(renderState)) {
            yawIn += (float)(Math.cos((double)renderState.ageInTicks * 3.25D) * Math.PI * (double)0.4F);
        }
        if (!renderState.isSpinning) {
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - yawIn));
        }

        if (renderState.deathTime > 0) {
            float f = ((float)renderState.deathTime + tickDelta - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }

            stack.mulPose(Axis.ZP.rotationDegrees(f * this.getFlipDegrees(renderState)));
        } else if (isEntityUpsideDown(renderState)) {
            stack.translate(0.0D, (double)(renderState.boundingBoxHeight + 0.1F), 0.0D);
            stack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
    }

    static class TurtleOverlayLayer extends RenderLayer<TerrapinRenderState, ModelTerrapin> {

        private final int layer;

        public TurtleOverlayLayer(RenderTerrapin render, int layer) {
            super(render);
            this.layer = layer;
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource buffer, int packedLightIn, TerrapinRenderState renderState, float limbSwingIn, float ageInTicks) {
            if(renderState.turtleType == TerrapinTypes.OVERLAY && !renderState.isKoopa){
                ResourceLocation tex = layer == 0 ? this.getParentModel().getClass() == ModelTerrapin.class ? renderState.turtleType.getTexture() : renderState.turtleType.getTexture() : layer == 1 ? RenderTerrapin.SHELL_TEXTURES[renderState.shellType % RenderTerrapin.SHELL_TEXTURES.length] : RenderTerrapin.SKIN_PATTERN_TEXTURES[renderState.skinType % RenderTerrapin.SKIN_PATTERN_TEXTURES.length];
                int color = layer == 0 ? renderState.turtleColor : layer == 1 ? renderState.shellColor : renderState.skinColor;
                coloredCutoutModelCopyLayerRender(getParentModel(), tex, matrixStackIn, buffer, packedLightIn, renderState, color | 0xFF000000);
            }
        }
    }

}
