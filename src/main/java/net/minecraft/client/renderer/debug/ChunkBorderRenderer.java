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

	/** Copies the chunk-border grid into Rust's explicit debug-line stream. */
	public void collectRustSemantics(Camera camera) {
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
