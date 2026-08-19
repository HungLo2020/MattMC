package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.ArrowModel;
import net.minecraft.client.model.BeeStingerModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;
import net.vulkanic.world.WorldRenderRoutePolicy;

@Environment(EnvType.CLIENT)
public abstract class StuckInBodyLayer<M extends PlayerModel, S> extends RenderLayer<AvatarRenderState, M> {
	private final Model<S> model;
	private final S modelState;
	private final ResourceLocation texture;
	private final StuckInBodyLayer.PlacementStyle placementStyle;
	/**
	 * Semantic-only view over the exact stuck-item model root. The real model
	 * resets/applies its Java semantic state before Rust copies this shared root;
	 * the wrapper itself never carries renderer or backend state across FFI.
	 */
	private final Model.Simple rustSemanticModel;

	public StuckInBodyLayer(
		LivingEntityRenderer<?, AvatarRenderState, M> livingEntityRenderer,
		Model<S> model,
		S object,
		ResourceLocation resourceLocation,
		StuckInBodyLayer.PlacementStyle placementStyle
	) {
		super(livingEntityRenderer);
		this.model = model;
		this.modelState = object;
		this.texture = resourceLocation;
		this.placementStyle = placementStyle;
		this.rustSemanticModel = new Model.Simple(model.root(), model::renderType);
	}

	protected abstract int numStuck(AvatarRenderState avatarRenderState);

	private boolean isRustMigratedStuckModel() {
		// Keep this shared public layer fail-closed for unknown/modded subclasses.
		// Only the two vanilla producers whose complete cutout semantics were
		// audited are admitted by this milestone.
		return this.model.getClass().equals(ArrowModel.class)
			|| this.model.getClass().equals(BeeStingerModel.class);
	}

	private void submitStuckItem(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int packedLight,
		float xDirection,
		float yDirection,
		float zDirection,
		int outlineColor,
		RenderType renderType,
		ResourceLocation entityIdentity,
		boolean rustAvailable
	) {
		float horizontal = Mth.sqrt(xDirection * xDirection + zDirection * zDirection);
		float yaw = (float)(Math.atan2(xDirection, zDirection) * 180.0F / (float)Math.PI);
		float pitch = (float)(Math.atan2(yDirection, horizontal) * 180.0F / (float)Math.PI);
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
		if (rustAvailable) {
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.rustSemanticModel,
				Unit.INSTANCE,
				poseStack.last(),
				renderType,
				this.texture,
				entityIdentity,
				packedLight,
				OverlayTexture.NO_OVERLAY,
				-1
			)) {
				throw new IllegalStateException("Rust whole-frame stuck-body feature was admitted but did not enqueue a copied indexed mesh");
			}
			return;
		}
		submitNodeCollector.submitModel(
			this.model,
			this.modelState,
			poseStack,
			renderType,
			packedLight,
			OverlayTexture.NO_OVERLAY,
			outlineColor,
			null
		);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState avatarRenderState, float f, float g) {
		int stuckCount = this.numStuck(avatarRenderState);
		if (stuckCount <= 0) {
			return;
		}

		RenderType renderType = this.model.renderType(this.texture);
		ResourceLocation entityIdentity = RustGalWorldPrimitiveRenderer.entityIdentity(avatarRenderState);
		boolean rustEligible = this.isRustMigratedStuckModel()
			&& entityIdentity != null
			&& RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
				this.rustSemanticModel,
				renderType,
				this.texture,
				OverlayTexture.NO_OVERLAY,
				avatarRenderState.outlineColor,
				null
			);
		WorldRenderRoutePolicy.Route ownership = StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute();
		StandaloneModelRenderOwnershipPolicy.Disposition disposition = StandaloneModelRenderOwnershipPolicy.classify(
			submitNodeCollector.isSemanticCoverageOnly(), rustEligible, ownership
		);
		if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", this.texture, false, false, false
			);
			return;
		}

		boolean rustAvailable = disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE;
		if (rustAvailable) {
			// Vanilla ModelFeatureRenderer invokes setupAnim before each draw. These
			// bounded layer states are identical for every embedded instance, so one
			// reset/application populates the shared root for all copied instances.
			this.model.setupAnim(this.modelState);
		}

		RandomSource randomSource = RandomSource.create(avatarRenderState.id);
		for (int index = 0; index < stuckCount; index++) {
			poseStack.pushPose();
			ModelPart modelPart = this.getParentModel().getRandomBodyPart(randomSource);
			ModelPart.Cube cube = modelPart.getRandomCube(randomSource);
			modelPart.translateAndRotate(poseStack);
			float x = randomSource.nextFloat();
			float y = randomSource.nextFloat();
			float z = randomSource.nextFloat();
			if (this.placementStyle == StuckInBodyLayer.PlacementStyle.ON_SURFACE) {
				int face = randomSource.nextInt(3);
				switch (face) {
					case 0:
						x = snapToFace(x);
						break;
					case 1:
						y = snapToFace(y);
						break;
					default:
						z = snapToFace(z);
				}
			}

			poseStack.translate(
				Mth.lerp(x, cube.minX, cube.maxX) / 16.0F,
				Mth.lerp(y, cube.minY, cube.maxY) / 16.0F,
				Mth.lerp(z, cube.minZ, cube.maxZ) / 16.0F
			);
			this.submitStuckItem(
				poseStack,
				submitNodeCollector,
				packedLight,
				-(x * 2.0F - 1.0F),
				-(y * 2.0F - 1.0F),
				-(z * 2.0F - 1.0F),
				avatarRenderState.outlineColor,
				renderType,
				entityIdentity,
				rustAvailable
			);
			poseStack.popPose();
		}

		if (rustAvailable) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", this.texture, true, true, false
			);
		} else if (rustEligible && !submitNodeCollector.isSemanticCoverageOnly()) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				ownership == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
				this.texture,
				false,
				false,
				ownership.usesJavaCompatibility()
			);
		}
	}

	private static float snapToFace(float f) {
		return f > 0.5F ? 1.0F : 0.5F;
	}

	@Environment(EnvType.CLIENT)
	public static enum PlacementStyle {
		IN_CUBE,
		ON_SURFACE;
	}
}
