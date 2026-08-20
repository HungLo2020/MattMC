package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.Maps;
import net.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.animal.horse.Markings;
import net.vulkanic.world.IndexedMeshMaterialCapabilities;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;
import net.vulkanic.world.WorldRenderRoutePolicy;

@Environment(EnvType.CLIENT)
public class HorseMarkingLayer extends RenderLayer<HorseRenderState, HorseModel> {
	private static final ResourceLocation INVISIBLE_TEXTURE = ResourceLocation.withDefaultNamespace("invisible");
	private static final Map<Markings, ResourceLocation> LOCATION_BY_MARKINGS = Maps.newEnumMap(
		Map.of(
			Markings.NONE,
			INVISIBLE_TEXTURE,
			Markings.WHITE,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_white.png"),
			Markings.WHITE_FIELD,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_whitefield.png"),
			Markings.WHITE_DOTS,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_whitedots.png"),
			Markings.BLACK_DOTS,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_blackdots.png")
		)
	);
	/** Semantic-only view over the exact parent horse model root. */
	private final Model.Simple rustSemanticModel;

	public HorseMarkingLayer(RenderLayerParent<HorseRenderState, HorseModel> renderLayerParent) {
		super(renderLayerParent);
		HorseModel parentModel = this.getParentModel();
		this.rustSemanticModel = new Model.Simple(parentModel.root(), parentModel::renderType);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HorseRenderState horseRenderState, float f, float g) {
		ResourceLocation texture = (ResourceLocation)LOCATION_BY_MARKINGS.get(horseRenderState.markings);
		if (texture == INVISIBLE_TEXTURE || horseRenderState.isInvisible) {
			return;
		}

		RenderType renderType = RenderType.entityTranslucent(texture);
		int overlayCoords = LivingEntityRenderer.getOverlayCoords(horseRenderState, 0.0F);
		ResourceLocation entityIdentity = RustGalWorldPrimitiveRenderer.entityIdentity(horseRenderState);
		boolean rustEligible = IndexedMeshMaterialCapabilities.preservesAlphaCutout(renderType)
			&& entityIdentity != null
			&& overlayCoords == OverlayTexture.NO_OVERLAY
			&& RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
				this.rustSemanticModel,
				renderType,
				texture,
				overlayCoords,
				horseRenderState.outlineColor,
				null
			);
		WorldRenderRoutePolicy.Route ownership = StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute();
		StandaloneModelRenderOwnershipPolicy.Disposition disposition = StandaloneModelRenderOwnershipPolicy.classify(
			submitNodeCollector.isSemanticCoverageOnly(), rustEligible, ownership
		);

		if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE) {
			// Match ModelFeatureRenderer: reset and apply the real parent-model state
			// immediately before the semantic wrapper copies the shared root.
			this.getParentModel().setupAnim(horseRenderState);
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.rustSemanticModel,
				Unit.INSTANCE,
				poseStack.last(),
				renderType,
				texture,
				entityIdentity,
				packedLight,
				overlayCoords,
				-1
			)) {
				throw new IllegalStateException("Rust whole-frame horse-marking feature was admitted but did not enqueue a copied indexed mesh");
			}
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", texture, this.getParentModel().getClass().getName(), horseRenderState.entityId, true, true, false
			);
			return;
		}
		if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", texture, this.getParentModel().getClass().getName(), horseRenderState.entityId, false, false, false
			);
			return;
		}

		if (rustEligible && !submitNodeCollector.isSemanticCoverageOnly()) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				ownership == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
				texture,
				this.getParentModel().getClass().getName(),
				horseRenderState.entityId,
				false,
				false,
				ownership.usesJavaCompatibility()
			);
		}
		submitNodeCollector.order(1)
			.submitModel(
				this.getParentModel(),
				horseRenderState,
				poseStack,
				renderType,
				packedLight,
				overlayCoords,
				-1,
				null,
				horseRenderState.outlineColor,
				null
			);
	}
}
