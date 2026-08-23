package net.minecraft.client.renderer.blockentity;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.util.EnumSet;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public abstract class AbstractEndPortalRenderer<T extends TheEndPortalBlockEntity, S extends EndPortalRenderState> implements BlockEntityRenderer<T, S> {
	public static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
	public static final ResourceLocation END_PORTAL_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");

	public void extractRenderState(
		T theEndPortalBlockEntity, S endPortalRenderState, float f, Vec3 vec3, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(theEndPortalBlockEntity, endPortalRenderState, f, vec3, crumblingOverlay);
		endPortalRenderState.facesToShow.clear();

		for (Direction direction : Direction.values()) {
			if (theEndPortalBlockEntity.shouldRenderFace(direction)) {
				endPortalRenderState.facesToShow.add(direction);
			}
		}
	}

	public void submit(S endPortalRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		boolean rustWholeFrame = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan();
		if (rustWholeFrame && submitNodeCollector.isSemanticCoverageOnly()) {
			// The whole-frame route keeps end-portal custom geometry unavailable
			// until its copied material primitive is admitted; do not count this
			// already-owned route as a hidden Java callback during source coverage.
			return;
		}
		if (rustWholeFrame) {
			boolean[] faces = new boolean[Direction.values().length];
			for (Direction direction : endPortalRenderState.facesToShow) faces[direction.ordinal()] = true;
			float gameTime = net.minecraft.client.Minecraft.getInstance().level.getGameTime()
				+ net.vulkanic.bridge.RustGalDeterministicTiming.partialTick(
					net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
				);
			if (submitNodeCollector.submitEndPortal(poseStack, faces, gameTime, 15728880)) return;
			throw new IllegalStateException("Rust whole-frame End Portal route unavailable for semantic cube");
		}
		// Iris: Cancel default rendering when shader pack is loaded (from MixinTheEndPortalRenderer)
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !rustWholeFrame && net.irisshaders.iris.Iris.getCurrentPack().isPresent()) {
			// Custom rendering is handled by the renderType override which returns RenderType.entitySolid()
			return;
		}
		
		submitNodeCollector.submitCustomGeometry(
			poseStack, this.renderType(), (pose, vertexConsumer) -> this.renderCube(endPortalRenderState.facesToShow, pose.pose(), vertexConsumer)
		);
	}

	private void renderCube(EnumSet<Direction> enumSet, Matrix4f matrix4f, VertexConsumer vertexConsumer) {
		float f = this.getOffsetDown();
		float g = this.getOffsetUp();
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Direction.SOUTH);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, Direction.NORTH);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, Direction.EAST);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, Direction.WEST);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, f, f, 0.0F, 0.0F, 1.0F, 1.0F, Direction.DOWN);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, g, g, 1.0F, 1.0F, 0.0F, 0.0F, Direction.UP);
	}

	private void renderFace(
		EnumSet<Direction> enumSet,
		Matrix4f matrix4f,
		VertexConsumer vertexConsumer,
		float f,
		float g,
		float h,
		float i,
		float j,
		float k,
		float l,
		float m,
		Direction direction
	) {
		if (enumSet.contains(direction)) {
			vertexConsumer.addVertex(matrix4f, f, h, j);
			vertexConsumer.addVertex(matrix4f, g, h, k);
			vertexConsumer.addVertex(matrix4f, g, i, l);
			vertexConsumer.addVertex(matrix4f, f, i, m);
		}
	}

	protected float getOffsetUp() {
		return 0.75F;
	}

	protected float getOffsetDown() {
		return 0.375F;
	}

	protected RenderType renderType() {
		// Iris: Use entitySolid render type when shader pack is loaded (from MixinTheEndPortalRenderer)
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !(net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan())
			&& net.irisshaders.iris.Iris.getCurrentPack().isPresent()) {
			return net.minecraft.client.renderer.RenderType.entitySolid(net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_PORTAL_LOCATION);
		}
		return RenderType.endPortal();
	}
}
