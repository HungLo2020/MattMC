package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;
import net.vulkanic.world.WorldRenderRoutePolicy;

@Environment(EnvType.CLIENT)
public class SlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {
	// VoxelMap: Made accessible
	public final SlimeModel model;
	/** Semantic-only view over the exact baked outer-shell model root. */
	private final Model.Simple rustSemanticModel;

	public SlimeOuterLayer(RenderLayerParent<SlimeRenderState, SlimeModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new SlimeModel(entityModelSet.bakeLayer(ModelLayers.SLIME_OUTER));
		this.rustSemanticModel = new Model.Simple(this.model.root(), this.model::renderType);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, SlimeRenderState slimeRenderState, float f, float g) {
		boolean invisibleGlowOutline = slimeRenderState.appearsGlowing() && slimeRenderState.isInvisible;
		if (slimeRenderState.isInvisible && !invisibleGlowOutline) {
			return;
		}

		int overlayCoords = LivingEntityRenderer.getOverlayCoords(slimeRenderState, 0.0F);
		RenderType renderType = invisibleGlowOutline
			? RenderType.outline(SlimeRenderer.SLIME_LOCATION)
			: RenderType.entityTranslucent(SlimeRenderer.SLIME_LOCATION);
		ResourceLocation entityIdentity = RustGalWorldPrimitiveRenderer.entityIdentity(slimeRenderState);
		boolean rustEligible = !invisibleGlowOutline
			&& entityIdentity != null
			&& overlayCoords == OverlayTexture.NO_OVERLAY
			&& RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
				this.rustSemanticModel,
				renderType,
				SlimeRenderer.SLIME_LOCATION,
				overlayCoords,
				slimeRenderState.outlineColor,
				null
			);
		WorldRenderRoutePolicy.Route ownership = StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute();
		StandaloneModelRenderOwnershipPolicy.Disposition disposition = StandaloneModelRenderOwnershipPolicy.classify(
			submitNodeCollector.isSemanticCoverageOnly(), rustEligible, ownership
		);

		if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE) {
			this.model.setupAnim(slimeRenderState);
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.rustSemanticModel,
				Unit.INSTANCE,
				poseStack.last(),
				renderType,
				SlimeRenderer.SLIME_LOCATION,
				entityIdentity,
				packedLight,
				overlayCoords,
				-1
			)) {
				throw new IllegalStateException("Rust whole-frame slime outer shell was admitted but did not enqueue a copied indexed mesh");
			}
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", SlimeRenderer.SLIME_LOCATION, this.model.getClass().getName(), slimeRenderState.entityId, true, true, false
			);
			return;
		}
		if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", SlimeRenderer.SLIME_LOCATION, this.model.getClass().getName(), slimeRenderState.entityId, false, false, false
			);
			return;
		}

		if (rustEligible && !submitNodeCollector.isSemanticCoverageOnly()) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				ownership == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
				SlimeRenderer.SLIME_LOCATION,
				this.model.getClass().getName(),
				slimeRenderState.entityId,
				false,
				false,
				ownership.usesJavaCompatibility()
			);
		}
		submitNodeCollector.order(1)
			.submitModel(
				this.model,
				slimeRenderState,
				poseStack,
				renderType,
				packedLight,
				overlayCoords,
				-1,
				null,
				slimeRenderState.outlineColor,
				null
			);
	}
}
