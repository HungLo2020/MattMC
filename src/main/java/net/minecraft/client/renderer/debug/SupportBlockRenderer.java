package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableList;
import net.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class SupportBlockRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private double lastUpdateTime = Double.MIN_VALUE;
	private List<Entity> surroundEntities = Collections.emptyList();

	public SupportBlockRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		this.refreshEntities();

		Player player = this.minecraft.player;
		if (player != null && player.mainSupportingBlockPos.isPresent()) {
			this.drawHighlights(poseStack, multiBufferSource, d, e, f, player, () -> 0.0, 1.0F, 0.0F, 0.0F);
		}

		for (Entity entity2 : this.surroundEntities) {
			if (entity2 != player) {
				this.drawHighlights(poseStack, multiBufferSource, d, e, f, entity2, () -> this.getBias(entity2), 0.0F, 1.0F, 0.0F);
			}
		}
	}

	/** Copies support-block highlights and collision outlines to Rust lines. */
	public void collectRustSemantics(Camera camera) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame support-block debug route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized() || this.minecraft.level == null) return;
		this.refreshEntities();
		PoseStack semanticPose = new PoseStack();
		Vec3 cameraPosition = camera.getPosition();
		semanticPose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
		Player player = this.minecraft.player;
		if (player != null && player.mainSupportingBlockPos.isPresent()) {
			collectHighlights(semanticPose, player, 0.0, 0xFF00FF00);
		}
		for (Entity entity : this.surroundEntities) {
			if (entity != player) collectHighlights(semanticPose, entity, getBias(entity), 0xFF00FF00);
		}
	}

	private void refreshEntities() {
		double now = Util.getNanos();
		if (now - this.lastUpdateTime > 1.0E8) {
			this.lastUpdateTime = now;
			Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
			if (entity != null && entity.level() != null) {
				this.surroundEntities = ImmutableList.copyOf(entity.level().getEntities(entity, entity.getBoundingBox().inflate(16.0)));
			} else {
				this.surroundEntities = Collections.emptyList();
			}
		}
	}

	private void collectHighlights(PoseStack poseStack, Entity entity, double bias, int color) {
		entity.mainSupportingBlockPos.ifPresent(ignored -> {
			collectPosition(poseStack, entity.getOnPos(), 0.02 + bias, color);
			BlockPos legacy = entity.getOnPosLegacy();
			if (!legacy.equals(entity.getOnPos())) collectPosition(poseStack, legacy, 0.04 + bias, 0xFF00FFFF);
		});
	}

	private void collectPosition(PoseStack poseStack, BlockPos pos, double expansion, int color) {
		float x0 = (float)(pos.getX() - 2.0 * expansion);
		float y0 = (float)(pos.getY() - 2.0 * expansion);
		float z0 = (float)(pos.getZ() - 2.0 * expansion);
		float x1 = (float)(x0 + 1.0 + 4.0 * expansion);
		float y1 = (float)(y0 + 1.0 + 4.0 * expansion);
		float z1 = (float)(z0 + 1.0 + 4.0 * expansion);
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(
			poseStack.last().pose(), boxEdges(x0, y0, z0, x1, y1, z1), color, 1.0F
		)) throw new IllegalStateException("Rust whole-frame support-block debug route rejected highlight box");
		for (AABB box : this.minecraft.level.getBlockState(pos).getCollisionShape(this.minecraft.level, pos, CollisionContext.empty()).toAabbs()) {
			AABB world = box.move(pos);
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(
				poseStack.last().pose(), boxEdges((float)world.minX, (float)world.minY, (float)world.minZ,
					(float)world.maxX, (float)world.maxY, (float)world.maxZ), color, 1.0F
			)) throw new IllegalStateException("Rust whole-frame support-block debug route rejected collision shape");
		}
	}

	private static float[] boxEdges(float x0, float y0, float z0, float x1, float y1, float z1) {
		return new float[] {x0,y0,z0,x1,y0,z0, x1,y0,z0,x1,y0,z1, x1,y0,z1,x0,y0,z1, x0,y0,z1,x0,y0,z0,
			x0,y1,z0,x1,y1,z0, x1,y1,z0,x1,y1,z1, x1,y1,z1,x0,y1,z1, x0,y1,z1,x0,y1,z0,
			x0,y0,z0,x0,y1,z0, x1,y0,z0,x1,y1,z0, x1,y0,z1,x1,y1,z1, x0,y0,z1,x0,y1,z1};
	}

	private void drawHighlights(
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		double d,
		double e,
		double f,
		Entity entity,
		DoubleSupplier doubleSupplier,
		float g,
		float h,
		float i
	) {
		entity.mainSupportingBlockPos.ifPresent(blockPos -> {
			double j = doubleSupplier.getAsDouble();
			BlockPos blockPos2 = entity.getOnPos();
			this.highlightPosition(blockPos2, poseStack, d, e, f, multiBufferSource, 0.02 + j, g, h, i);
			BlockPos blockPos3 = entity.getOnPosLegacy();
			if (!blockPos3.equals(blockPos2)) {
				this.highlightPosition(blockPos3, poseStack, d, e, f, multiBufferSource, 0.04 + j, 0.0F, 1.0F, 1.0F);
			}
		});
	}

	private double getBias(Entity entity) {
		return 0.02 * (String.valueOf(entity.getId() + 0.132453657).hashCode() % 1000) / 1000.0;
	}

	private void highlightPosition(
		BlockPos blockPos, PoseStack poseStack, double d, double e, double f, MultiBufferSource multiBufferSource, double g, float h, float i, float j
	) {
		double k = blockPos.getX() - d - 2.0 * g;
		double l = blockPos.getY() - e - 2.0 * g;
		double m = blockPos.getZ() - f - 2.0 * g;
		double n = k + 1.0 + 4.0 * g;
		double o = l + 1.0 + 4.0 * g;
		double p = m + 1.0 + 4.0 * g;
		ShapeRenderer.renderLineBox(poseStack.last(), multiBufferSource.getBuffer(RenderType.lines()), k, l, m, n, o, p, h, i, j, 0.4F);
		DebugRenderer.renderVoxelShape(
			poseStack,
			multiBufferSource.getBuffer(RenderType.lines()),
			this.minecraft.level.getBlockState(blockPos).getCollisionShape(this.minecraft.level, blockPos, CollisionContext.empty()).move(blockPos),
			-d,
			-e,
			-f,
			h,
			i,
			j,
			1.0F,
			false
		);
	}
}
