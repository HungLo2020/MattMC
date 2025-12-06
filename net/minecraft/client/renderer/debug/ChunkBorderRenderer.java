package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
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
}
