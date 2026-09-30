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

	/** Copies support-block highlights and collision outlines to Rust lines. */
	public void collectRustSemantics(Camera camera) {
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

	private double getBias(Entity entity) {
		return 0.02 * (String.valueOf(entity.getId() + 0.132453657).hashCode() % 1000) / 1000.0;
	}

}
