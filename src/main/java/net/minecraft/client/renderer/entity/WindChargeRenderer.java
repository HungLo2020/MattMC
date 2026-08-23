package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.WindChargeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;

@Environment(EnvType.CLIENT)
public class WindChargeRenderer extends EntityRenderer<AbstractWindCharge, EntityRenderState> {
	private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/wind_charge.png");
	private final WindChargeModel model;

	public WindChargeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new WindChargeModel(context.bakeLayer(ModelLayers.WIND_CHARGE));
	}

	@Override
	public void submit(EntityRenderState entityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		float uvOffsetU = this.xOffset(entityRenderState.ageInTicks) % 1.0F;
		this.model.setupAnim(entityRenderState);
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
			if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWindChargeModel(
				this.model.root(), poseStack.last(), TEXTURE_LOCATION, uvOffsetU, 0.0F, entityRenderState.lightCoords
			)) {
				super.submit(entityRenderState, poseStack, submitNodeCollector, cameraRenderState);
				return;
			}
			throw new IllegalStateException("Rust whole-frame wind-charge route has no semantic mesh");
		}
		submitNodeCollector.submitModel(
			this.model,
			entityRenderState,
			poseStack,
			RenderType.breezeWind(TEXTURE_LOCATION, uvOffsetU, 0.0F),
			entityRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			entityRenderState.outlineColor,
			null
		);
		super.submit(entityRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected float xOffset(float f) {
		return f * 0.03F;
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
