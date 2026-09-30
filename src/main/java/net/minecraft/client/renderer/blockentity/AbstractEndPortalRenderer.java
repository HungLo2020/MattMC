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
		if (submitNodeCollector.isSemanticCoverageOnly()) {
			// The whole-frame route keeps end-portal custom geometry unavailable
			// until its copied material primitive is admitted; do not count this
			// already-owned route as a hidden Java callback during source coverage.
			return;
		}
		boolean[] faces = new boolean[Direction.values().length];
		for (Direction direction : endPortalRenderState.facesToShow) faces[direction.ordinal()] = true;
		float gameTime = net.minecraft.client.Minecraft.getInstance().level.getGameTime()
			+ net.vulkanic.bridge.RustGalDeterministicTiming.partialTick(
				net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
			);
		if (submitNodeCollector.submitEndPortalSemantic(
			poseStack, faces, this.getOffsetDown(), this.getOffsetUp(), gameTime, 15728880
		)) return;
		throw new IllegalStateException("Rust whole-frame End Portal route unavailable for semantic cube");
		
	}

	protected float getOffsetUp() {
		return 0.75F;
	}

	protected float getOffsetDown() {
		return 0.375F;
	}

	protected RenderType renderType() {
		return RenderType.endPortal();
	}
}
