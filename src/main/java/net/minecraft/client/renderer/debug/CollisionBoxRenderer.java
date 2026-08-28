package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableList;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.util.Collections;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public class CollisionBoxRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private double lastUpdateTime = Double.MIN_VALUE;
	private List<VoxelShape> shapes = Collections.emptyList();

	public CollisionBoxRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		this.refreshShapes();

		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());

		for (VoxelShape voxelShape : this.shapes) {
			DebugRenderer.renderVoxelShape(poseStack, vertexConsumer, voxelShape, -d, -e, -f, 1.0F, 1.0F, 1.0F, 1.0F, true);
		}
	}

	/**
	 * Copies the collision shapes into the explicit Rust debug-line stream. The
	 * legacy renderer above remains the private OpenGL compatibility lowering;
	 * this method is the only whole-frame Vulkan entrypoint for this family.
	 */
	public void collectRustSemantics(PoseStack poseStack, SubmitNodeStorage geometry, Camera camera) {
		this.refreshShapes();
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()) {
			if (!shapes.isEmpty() && net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame collision-debug route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized()) return;
		if (shapes.isEmpty()) return;
		PoseStack semanticPose = new PoseStack();
		Vec3 cameraPosition = camera.getPosition();
		semanticPose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
		for (VoxelShape shape : shapes) {
			for (AABB box : shape.toAabbs()) {
				float[] edges = boxEdges((float)box.minX, (float)box.minY, (float)box.minZ,
					(float)box.maxX, (float)box.maxY, (float)box.maxZ);
				if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(
					semanticPose.last().pose(), edges, 0xFFFFFFFF, 1.0F
				)) {
					throw new IllegalStateException("Rust whole-frame collision-debug route rejected semantic shape");
				}
			}
		}
	}

	private void refreshShapes() {
		double now = Util.getNanos();
		if (now - this.lastUpdateTime > 1.0E8) {
			this.lastUpdateTime = now;
			Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
			if (entity != null && entity.level() != null) {
				this.shapes = ImmutableList.copyOf(entity.level().getCollisions(entity, entity.getBoundingBox().inflate(6.0)));
			} else {
				this.shapes = Collections.emptyList();
			}
		}
	}

	private static float[] boxEdges(float x0, float y0, float z0, float x1, float y1, float z1) {
		return new float[] {
			x0,y0,z0, x1,y0,z0, x1,y0,z0, x1,y0,z1,
			x1,y0,z1, x0,y0,z1, x0,y0,z1, x0,y0,z0,
			x0,y1,z0, x1,y1,z0, x1,y1,z0, x1,y1,z1,
			x1,y1,z1, x0,y1,z1, x0,y1,z1, x0,y1,z0,
			x0,y0,z0, x0,y1,z0, x1,y0,z0, x1,y1,z0,
			x1,y0,z1, x1,y1,z1, x0,y0,z1, x0,y1,z1
		};
	}
}
