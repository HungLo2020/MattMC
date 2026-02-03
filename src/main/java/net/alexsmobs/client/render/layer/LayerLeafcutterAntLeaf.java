package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelLeafcutterAnt;
import net.alexsmobs.client.render.AMColorUtil;
import net.alexsmobs.client.render.LeafcutterAntRenderState;
import net.alexsmobs.client.render.OctopusColorRegistry;
import net.alexsmobs.client.render.RenderLeafcutterAnt;
import net.citadel.client.model.AdvancedEntityModel;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class LayerLeafcutterAntLeaf extends RenderLayer<LeafcutterAntRenderState, AdvancedEntityModel<LeafcutterAntRenderState>> {

    private static final ResourceLocation TEXTURE_0 = ResourceLocation
            .withDefaultNamespace("textures/entity/leafcutter_ant_leaf_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation
            .withDefaultNamespace("textures/entity/leafcutter_ant_leaf_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation
            .withDefaultNamespace("textures/entity/leafcutter_ant_leaf_2.png");

    public LayerLeafcutterAntLeaf(RenderLeafcutterAnt render) {
        super(render);
    }

    @Override
    public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector bufferSource, int packedLightIn, LeafcutterAntRenderState state, float limbSwing, float limbSwingAmount) {
        if (state.hasLeaf && !state.isQueen
                && this.getParentModel() instanceof ModelLeafcutterAnt) {
            final int leafType = state.id % 3;
            final ResourceLocation res = switch (leafType) {
                case 2 -> TEXTURE_2;
                case 1 -> TEXTURE_1;
                default -> TEXTURE_0;
            };
            int leafColor = Minecraft.getInstance().getBlockColors().getColor(net.minecraft.world.level.block.Blocks.JUNGLE_LEAVES.defaultBlockState(), null, null);
            if (state.leafHarvestedPos != null && state.leafHarvestedState != null) {
                leafColor = OctopusColorRegistry.getBlockColor(state.leafHarvestedState);
            }
            final float f = (float) (leafColor >> 16 & 255) / 255.0F;
            final float f1 = (float) (leafColor >> 8 & 255) / 255.0F;
            final float f2 = (float) (leafColor & 255) / 255.0F;
            // Use order(1) to ensure proper rendering order with depth testing
            bufferSource.order(1).submitModel(
                this.getParentModel(),
                state,
                matrixStackIn,
                RenderType.entityCutout(res),
                packedLightIn,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                AMColorUtil.packColor(f, f1, f2, 1.0F),
                null,
                state.outlineColor,
                null
            );
        }
    }
}
