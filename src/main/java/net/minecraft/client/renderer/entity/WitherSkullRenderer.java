package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.WitherSkullRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;
import net.vulkanic.world.WorldRenderRoutePolicy;
import net.minecraft.world.entity.projectile.WitherSkull;

@Environment(EnvType.CLIENT)
public class WitherSkullRenderer extends EntityRenderer<WitherSkull, WitherSkullRenderState> {
	private static final ResourceLocation WITHER_INVULNERABLE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/wither/wither_invulnerable.png");
	private static final ResourceLocation WITHER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/wither/wither.png");
	private static final ResourceLocation WITHER_SKULL_ENTITY_ID = ResourceLocation.withDefaultNamespace("wither_skull");
	private final SkullModel model;

	public WitherSkullRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new SkullModel(context.bakeLayer(ModelLayers.WITHER_SKULL));
	}

	public static LayerDefinition createSkullLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 35).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	protected int getBlockLightLevel(WitherSkull witherSkull, BlockPos blockPos) {
		return 15;
	}

	public void submit(
		WitherSkullRenderState witherSkullRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		ResourceLocation texture = this.getTextureLocation(witherSkullRenderState);
		var renderType = this.model.renderType(texture);
		boolean eligible = RustGalWorldPrimitiveRenderer.isStandaloneTranslucentModelMeshEligible(
			this.model, renderType, texture, OverlayTexture.NO_OVERLAY, witherSkullRenderState.outlineColor, null
		);
		WorldRenderRoutePolicy.Route ownership = StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute();
		StandaloneModelRenderOwnershipPolicy.Disposition disposition = StandaloneModelRenderOwnershipPolicy.classify(
			submitNodeCollector.isSemanticCoverageOnly(), eligible, ownership
		);
		if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE) {
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.model,
				witherSkullRenderState.modelState,
				poseStack.last(),
				renderType,
				texture,
				WITHER_SKULL_ENTITY_ID,
				witherSkullRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				witherSkullRenderState.outlineColor
			)) {
				throw new IllegalStateException("Rust whole-frame WitherSkull route selected without a copied indexed mesh request");
			}
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", texture, true, true, false
			);
		} else if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", texture, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame WitherSkull route has no semantic mesh");
		} else {
			if (eligible) {
				RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					ownership == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
					texture,
					false,
					false,
					!submitNodeCollector.isSemanticCoverageOnly() && ownership.usesJavaCompatibility()
				);
			}
			submitNodeCollector.submitModelSemantic(
				this.model,
				witherSkullRenderState.modelState,
				poseStack,
				renderType,
				witherSkullRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				witherSkullRenderState.outlineColor,
				null
			);
		}
		poseStack.popPose();
		super.submit(witherSkullRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	private ResourceLocation getTextureLocation(WitherSkullRenderState witherSkullRenderState) {
		return witherSkullRenderState.isDangerous ? WITHER_INVULNERABLE_LOCATION : WITHER_LOCATION;
	}

	public WitherSkullRenderState createRenderState() {
		return new WitherSkullRenderState();
	}

	public void extractRenderState(WitherSkull witherSkull, WitherSkullRenderState witherSkullRenderState, float f) {
		super.extractRenderState(witherSkull, witherSkullRenderState, f);
		witherSkullRenderState.isDangerous = witherSkull.isDangerous();
		witherSkullRenderState.modelState.animationPos = 0.0F;
		witherSkullRenderState.modelState.yRot = witherSkull.getYRot(f);
		witherSkullRenderState.modelState.xRot = witherSkull.getXRot(f);
	}
}
