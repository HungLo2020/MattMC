package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.Camera;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debug.DebugStructureInfo.Piece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

@Environment(EnvType.CLIENT)
public class StructureRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
		debugValueAccess.forEachChunk(DebugSubscriptions.STRUCTURES, (chunkPos, list) -> {
			for (DebugStructureInfo debugStructureInfo : list) {
				renderBox(poseStack, d, e, f, vertexConsumer, debugStructureInfo.boundingBox(), 1.0F, 1.0F, 1.0F, 1.0F);

				for (Piece piece : debugStructureInfo.pieces()) {
					if (piece.isStart()) {
						renderBox(poseStack, d, e, f, vertexConsumer, piece.boundingBox(), 0.0F, 1.0F, 0.0F, 1.0F);
					} else {
						renderBox(poseStack, d, e, f, vertexConsumer, piece.boundingBox(), 0.0F, 0.0F, 1.0F, 1.0F);
					}
				}
			}
		});
	}

	/** Copies structure and piece boxes into Rust's explicit debug-line stream. */
	public void collectRustSemantics(Camera camera) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame structure-debug route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized()) return;
		DebugValueAccess access = net.minecraft.client.Minecraft.getInstance().getConnection().createDebugValueAccess();
		org.joml.Matrix4f transform = new org.joml.Matrix4f().translate(
			(float)-camera.getPosition().x, (float)-camera.getPosition().y, (float)-camera.getPosition().z
		);
		access.forEachChunk(DebugSubscriptions.STRUCTURES, (chunkPos, structures) -> {
			for (DebugStructureInfo structure : structures) {
				if (!enqueueBox(transform, structure.boundingBox(), 0xFFFFFFFF)) {
					throw new IllegalStateException("Rust whole-frame structure-debug route rejected structure box");
				}
				for (Piece piece : structure.pieces()) {
					int color = piece.isStart() ? 0xFF00FF00 : 0xFF0000FF;
					if (!enqueueBox(transform, piece.boundingBox(), color)) {
						throw new IllegalStateException("Rust whole-frame structure-debug route rejected piece box");
					}
				}
			}
		});
	}

	private static boolean enqueueBox(org.joml.Matrix4f transform, BoundingBox box, int color) {
		return net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,
			new float[] {
				box.minX(), box.minY(), box.minZ(), box.maxX() + 1.0F, box.minY(), box.minZ(),
				box.maxX() + 1.0F, box.minY(), box.minZ(), box.maxX() + 1.0F, box.minY(), box.maxZ() + 1.0F,
				box.maxX() + 1.0F, box.minY(), box.maxZ() + 1.0F, box.minX(), box.minY(), box.maxZ() + 1.0F,
				box.minX(), box.minY(), box.maxZ() + 1.0F, box.minX(), box.minY(), box.minZ(),
				box.minX(), box.maxY() + 1.0F, box.minZ(), box.maxX() + 1.0F, box.maxY() + 1.0F, box.minZ(),
				box.maxX() + 1.0F, box.maxY() + 1.0F, box.minZ(), box.maxX() + 1.0F, box.maxY() + 1.0F, box.maxZ() + 1.0F,
				box.maxX() + 1.0F, box.maxY() + 1.0F, box.maxZ() + 1.0F, box.minX(), box.maxY() + 1.0F, box.maxZ() + 1.0F,
				box.minX(), box.maxY() + 1.0F, box.maxZ() + 1.0F, box.minX(), box.maxY() + 1.0F, box.minZ(),
				box.minX(), box.minY(), box.minZ(), box.minX(), box.maxY() + 1.0F, box.minZ(),
				box.maxX() + 1.0F, box.minY(), box.minZ(), box.maxX() + 1.0F, box.maxY() + 1.0F, box.minZ(),
				box.maxX() + 1.0F, box.minY(), box.maxZ() + 1.0F, box.maxX() + 1.0F, box.maxY() + 1.0F, box.maxZ() + 1.0F,
				box.minX(), box.minY(), box.maxZ() + 1.0F, box.minX(), box.maxY() + 1.0F, box.maxZ() + 1.0F
			}, color, 1.0F);
	}

	private static void renderBox(
		PoseStack poseStack, double d, double e, double f, VertexConsumer vertexConsumer, BoundingBox boundingBox, float g, float h, float i, float j
	) {
		ShapeRenderer.renderLineBox(
			poseStack.last(),
			vertexConsumer,
			boundingBox.minX() - d,
			boundingBox.minY() - e,
			boundingBox.minZ() - f,
			boundingBox.maxX() + 1 - d,
			boundingBox.maxY() + 1 - e,
			boundingBox.maxZ() + 1 - f,
			g,
			h,
			i,
			j,
			g,
			h,
			i
		);
	}
}
