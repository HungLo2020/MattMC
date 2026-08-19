package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;

@Environment(EnvType.CLIENT)
public class BoatRenderer extends AbstractBoatRenderer {
	private final Model.Simple waterPatchModel;
	private final ResourceLocation texture;
	private final EntityModel<BoatRenderState> model;
	private final Model.Simple rustSemanticModel;

	public BoatRenderer(EntityRendererProvider.Context context, ModelLayerLocation modelLayerLocation) {
		super(context);
		this.texture = modelLayerLocation.model().withPath(string -> "textures/entity/" + string + ".png");
		this.waterPatchModel = new Model.Simple(context.bakeLayer(ModelLayers.BOAT_WATER_PATCH), resourceLocation -> RenderType.waterMask());
		this.model = new BoatModel(context.bakeLayer(modelLayerLocation));
		this.rustSemanticModel = new Model.Simple(this.model.root(), this.model::renderType);
	}

	@Override
	protected EntityModel<BoatRenderState> model() {
		return this.model;
	}

	@Override
	protected Model.Simple rustSemanticModel() {
		return this.rustSemanticModel;
	}

	@Override
	protected RenderType renderType() {
		return this.model.renderType(this.texture);
	}

	@Override
	protected ResourceLocation textureLocation() {
		return this.texture;
	}

	@Override
	protected void submitTypeAdditions(BoatRenderState boatRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i) {
		if (!boatRenderState.isUnderWater) {
			// Vanilla WATER_MASK is a depth-writing, color-write-disabled pass.
			// VulkanicGAL does not yet expose a color-write mask in its explicit
			// graphics-pipeline contract, so a Rust-owned whole frame must keep
			// this capability unavailable rather than escape into a Java draw.
			// Semantic coverage remains observational and normal Java/OpenGL keeps
			// the original water-mask submit unchanged.
			if (!submitNodeCollector.isSemanticCoverageOnly()
				&& StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute().usesRustWholeFrameVulkan()) {
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity("boat-water-mask", "rust-vulkan-unavailable");
				return;
			}
			submitNodeCollector.submitModel(
				this.waterPatchModel,
				Unit.INSTANCE,
				poseStack,
				this.waterPatchModel.renderType(this.texture),
				i,
				OverlayTexture.NO_OVERLAY,
				boatRenderState.outlineColor,
				null
			);
		}
	}
}
