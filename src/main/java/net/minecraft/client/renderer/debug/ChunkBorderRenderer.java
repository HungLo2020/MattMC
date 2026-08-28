package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class ChunkBorderRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private static final int CELL_BORDER = ARGB.color(255, 0, 155, 155);
	private static final int YELLOW = ARGB.color(255, 255, 255, 0);

	public ChunkBorderRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
		float g = (float)(this.minecraft.level.getMinY() - e);
		float h = (float)(this.minecraft.level.getMaxY() + 1 - e);
		ChunkPos chunkPos = entity.chunkPosition();
		float i = (float)(chunkPos.getMinBlockX() - d);
		float j = (float)(chunkPos.getMinBlockZ() - f);
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.debugLineStrip(1.0));
		Matrix4f matrix4f = poseStack.last().pose();

		for (int k = -16; k <= 32; k += 16) {
			for (int l = -16; l <= 32; l += 16) {
				vertexConsumer.addVertex(matrix4f, i + k, g, j + l).setColor(1.0F, 0.0F, 0.0F, 0.0F);
				vertexConsumer.addVertex(matrix4f, i + k, g, j + l).setColor(1.0F, 0.0F, 0.0F, 0.5F);
				vertexConsumer.addVertex(matrix4f, i + k, h, j + l).setColor(1.0F, 0.0F, 0.0F, 0.5F);
				vertexConsumer.addVertex(matrix4f, i + k, h, j + l).setColor(1.0F, 0.0F, 0.0F, 0.0F);
			}
		}

		for (int k = 2; k < 16; k += 2) {
			int l = k % 4 == 0 ? CELL_BORDER : YELLOW;
			vertexConsumer.addVertex(matrix4f, i + k, g, j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i + k, g, j).setColor(l);
			vertexConsumer.addVertex(matrix4f, i + k, h, j).setColor(l);
			vertexConsumer.addVertex(matrix4f, i + k, h, j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i + k, g, j + 16.0F).setColor(1.0F, 1.0F, 0.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i + k, g, j + 16.0F).setColor(l);
			vertexConsumer.addVertex(matrix4f, i + k, h, j + 16.0F).setColor(l);
			vertexConsumer.addVertex(matrix4f, i + k, h, j + 16.0F).setColor(1.0F, 1.0F, 0.0F, 0.0F);
		}

		for (int k = 2; k < 16; k += 2) {
			int l = k % 4 == 0 ? CELL_BORDER : YELLOW;
			vertexConsumer.addVertex(matrix4f, i, g, j + k).setColor(1.0F, 1.0F, 0.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i, g, j + k).setColor(l);
			vertexConsumer.addVertex(matrix4f, i, h, j + k).setColor(l);
			vertexConsumer.addVertex(matrix4f, i, h, j + k).setColor(1.0F, 1.0F, 0.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, g, j + k).setColor(1.0F, 1.0F, 0.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, g, j + k).setColor(l);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, h, j + k).setColor(l);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, h, j + k).setColor(1.0F, 1.0F, 0.0F, 0.0F);
		}

		for (int k = this.minecraft.level.getMinY(); k <= this.minecraft.level.getMaxY() + 1; k += 2) {
			float m = (float)(k - e);
			int n = k % 8 == 0 ? CELL_BORDER : YELLOW;
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(n);
			vertexConsumer.addVertex(matrix4f, i, m, j + 16.0F).setColor(n);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, m, j + 16.0F).setColor(n);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, m, j).setColor(n);
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(n);
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
		}

		vertexConsumer = multiBufferSource.getBuffer(RenderType.debugLineStrip(2.0));

		for (int k = 0; k <= 16; k += 16) {
			for (int l = 0; l <= 16; l += 16) {
				vertexConsumer.addVertex(matrix4f, i + k, g, j + l).setColor(0.25F, 0.25F, 1.0F, 0.0F);
				vertexConsumer.addVertex(matrix4f, i + k, g, j + l).setColor(0.25F, 0.25F, 1.0F, 1.0F);
				vertexConsumer.addVertex(matrix4f, i + k, h, j + l).setColor(0.25F, 0.25F, 1.0F, 1.0F);
				vertexConsumer.addVertex(matrix4f, i + k, h, j + l).setColor(0.25F, 0.25F, 1.0F, 0.0F);
			}
		}

		for (int k = this.minecraft.level.getMinY(); k <= this.minecraft.level.getMaxY() + 1; k += 16) {
			float m = (float)(k - e);
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(0.25F, 0.25F, 1.0F, 0.0F);
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(0.25F, 0.25F, 1.0F, 1.0F);
			vertexConsumer.addVertex(matrix4f, i, m, j + 16.0F).setColor(0.25F, 0.25F, 1.0F, 1.0F);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, m, j + 16.0F).setColor(0.25F, 0.25F, 1.0F, 1.0F);
			vertexConsumer.addVertex(matrix4f, i + 16.0F, m, j).setColor(0.25F, 0.25F, 1.0F, 1.0F);
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(0.25F, 0.25F, 1.0F, 1.0F);
			vertexConsumer.addVertex(matrix4f, i, m, j).setColor(0.25F, 0.25F, 1.0F, 0.0F);
		}
	}

	/** Copies the chunk-border grid into Rust's explicit debug-line stream. */
	public void collectRustSemantics(Camera camera) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame chunk-border route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized() || minecraft.level == null) return;
		Entity entity = camera.getEntity();
		if (entity == null) return;
		ChunkPos chunk = entity.chunkPosition();
		float minY = minecraft.level.getMinY(), maxY = minecraft.level.getMaxY() + 1.0F;
		float minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
		org.joml.Matrix4f transform = new org.joml.Matrix4f().translate(
			(float)-camera.getPosition().x, (float)-camera.getPosition().y, (float)-camera.getPosition().z
		);
		for (int x = -16; x <= 32; x += 16) for (int z = -16; z <= 32; z += 16)
			queue(transform, minX + x, minY, minZ + z, minX + x, maxY, minZ + z, 0xFFFF0000, 1.0F);
		for (int x = 2; x < 16; x += 2) {
			int color = x % 4 == 0 ? CELL_BORDER : YELLOW;
			queue(transform, minX + x, minY, minZ, minX + x, maxY, minZ, color, 1.0F);
			queue(transform, minX + x, minY, minZ + 16, minX + x, maxY, minZ + 16, color, 1.0F);
		}
		for (int z = 2; z < 16; z += 2) {
			int color = z % 4 == 0 ? CELL_BORDER : YELLOW;
			queue(transform, minX, minY, minZ + z, minX, maxY, minZ + z, color, 1.0F);
			queue(transform, minX + 16, minY, minZ + z, minX + 16, maxY, minZ + z, color, 1.0F);
		}
		for (int y = minecraft.level.getMinY(); y <= minecraft.level.getMaxY() + 1; y += 2) {
			int color = y % 8 == 0 ? CELL_BORDER : YELLOW;
			queue(transform, minX, y, minZ, minX, y, minZ + 16, color, 1.0F);
			queue(transform, minX, y, minZ + 16, minX + 16, y, minZ + 16, color, 1.0F);
			queue(transform, minX + 16, y, minZ + 16, minX + 16, y, minZ, color, 1.0F);
			queue(transform, minX + 16, y, minZ, minX, y, minZ, color, 1.0F);
		}
	}

	private static void queue(org.joml.Matrix4f transform, float x0, float y0, float z0, float x1, float y1, float z1, int color, float width) {
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,
			new float[] {x0,y0,z0,x1,y1,z1}, color, width)) {
			throw new IllegalStateException("Rust whole-frame chunk-border route rejected semantic segment");
		}
	}
}
