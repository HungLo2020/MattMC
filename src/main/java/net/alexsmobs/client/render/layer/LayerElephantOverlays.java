package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelElephant;
import net.alexsmobs.client.render.ElephantRenderState;
import net.alexsmobs.client.render.RenderElephant;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
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

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, ElephantRenderState state, float f, float g) {
        if(state.chested){
            submitNodeCollector.order(1)
                .submitModel(
                    this.getParentModel(),
                    state,
                    poseStack,
                    RenderType.entityCutout(TEXTURE_CHEST),
                    i,
                    OverlayTexture.NO_OVERLAY,
                    -1,
                    null,
                    state.outlineColor,
                    null
                );
        }
        DyeColor color = state.carpetColor >= 0 && state.carpetColor < 16 ? DyeColor.byId(state.carpetColor) : null;
        if(color != null) {
            ResourceLocation texture = ELEPHANT_DECOR_TEXTURES[color.getId()];
            this.model.setupAnim(state);
            submitNodeCollector.order(1)
                .submitModel(
                    this.model,
                    state,
                    poseStack,
                    RenderType.entityCutoutNoCull(texture),
                    i,
                    OverlayTexture.NO_OVERLAY,
                    -1,
                    null,
                    state.outlineColor,
                    null
                );
        }
    }
}
