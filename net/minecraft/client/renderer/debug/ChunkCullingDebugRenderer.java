package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class ChunkCullingDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	public static final Direction[] DIRECTIONS = Direction.values();
	private final Minecraft minecraft;

	public ChunkCullingDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		LevelRenderer levelRenderer = this.minecraft.levelRenderer;
		boolean bl = this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_PATHS);
		boolean bl2 = this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_VISIBILITY);
		if (bl || bl2) {
			SectionOcclusionGraph sectionOcclusionGraph = levelRenderer.getSectionOcclusionGraph();

			for (SectionRenderDispatcher.RenderSection renderSection : levelRenderer.getVisibleSections()) {
				SectionOcclusionGraph.Node node = sectionOcclusionGraph.getNode(renderSection);
				if (node != null) {
					BlockPos blockPos = renderSection.getRenderOrigin();
					poseStack.pushPose();
					poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
					Matrix4f matrix4f = poseStack.last().pose();
					if (bl) {
						VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
						int i = node.step == 0 ? 0 : Mth.hsvToRgb(node.step / 50.0F, 0.9F, 0.9F);
						int j = i >> 16 & 0xFF;
						int k = i >> 8 & 0xFF;
						int l = i & 0xFF;

						for (int m = 0; m < DIRECTIONS.length; m++) {
							if (node.hasSourceDirection(m)) {
								Direction direction = DIRECTIONS[m];
								vertexConsumer.addVertex(matrix4f, 8.0F, 8.0F, 8.0F).setColor(j, k, l, 255).setNormal(direction.getStepX(), direction.getStepY(), direction.getStepZ());
								vertexConsumer.addVertex(
										matrix4f, (float)(8 - 16 * direction.getStepX()), (float)(8 - 16 * direction.getStepY()), (float)(8 - 16 * direction.getStepZ())
									)
									.setColor(j, k, l, 255)
									.setNormal(direction.getStepX(), direction.getStepY(), direction.getStepZ());
							}
						}
					}

					if (bl2 && renderSection.getSectionMesh().hasRenderableLayers()) {
						VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
						int i = 0;

						for (Direction direction2 : DIRECTIONS) {
							for (Direction direction3 : DIRECTIONS) {
								boolean bl3 = renderSection.getSectionMesh().facesCanSeeEachother(direction2, direction3);
								if (!bl3) {
									i++;
									vertexConsumer.addVertex(
											matrix4f, (float)(8 + 8 * direction2.getStepX()), (float)(8 + 8 * direction2.getStepY()), (float)(8 + 8 * direction2.getStepZ())
										)
										.setColor(255, 0, 0, 255)
										.setNormal(direction2.getStepX(), direction2.getStepY(), direction2.getStepZ());
									vertexConsumer.addVertex(
											matrix4f, (float)(8 + 8 * direction3.getStepX()), (float)(8 + 8 * direction3.getStepY()), (float)(8 + 8 * direction3.getStepZ())
										)
										.setColor(255, 0, 0, 255)
										.setNormal(direction3.getStepX(), direction3.getStepY(), direction3.getStepZ());
								}
							}
						}

						if (i > 0) {
							VertexConsumer vertexConsumer2 = multiBufferSource.getBuffer(RenderType.debugQuads());
							float g = 0.5F;
							float h = 0.2F;
							vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 0.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 15.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 15.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
							vertexConsumer2.addVertex(matrix4f, 0.5F, 0.5F, 15.5F).setColor(0.9F, 0.9F, 0.0F, 0.2F);
						}
					}

					poseStack.popPose();
				}
			}
		}

		Frustum frustum2 = levelRenderer.getCapturedFrustum();
		if (frustum2 != null) {
			poseStack.pushPose();
			poseStack.translate((float)(frustum2.getCamX() - d), (float)(frustum2.getCamY() - e), (float)(frustum2.getCamZ() - f));
			Matrix4f matrix4f2 = poseStack.last().pose();
			Vector4f[] vector4fs = frustum2.getFrustumPoints();
			VertexConsumer vertexConsumer3 = multiBufferSource.getBuffer(RenderType.debugQuads());
			this.addFrustumQuad(vertexConsumer3, matrix4f2, vector4fs, 0, 1, 2, 3, 0, 1, 1);
			this.addFrustumQuad(vertexConsumer3, matrix4f2, vector4fs, 4, 5, 6, 7, 1, 0, 0);
			this.addFrustumQuad(vertexConsumer3, matrix4f2, vector4fs, 0, 1, 5, 4, 1, 1, 0);
			this.addFrustumQuad(vertexConsumer3, matrix4f2, vector4fs, 2, 3, 7, 6, 0, 0, 1);
			this.addFrustumQuad(vertexConsumer3, matrix4f2, vector4fs, 0, 4, 7, 3, 0, 1, 0);
			this.addFrustumQuad(vertexConsumer3, matrix4f2, vector4fs, 1, 5, 6, 2, 1, 0, 1);
			VertexConsumer vertexConsumer4 = multiBufferSource.getBuffer(RenderType.lines());
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[0]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[1]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[1]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[2]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[2]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[3]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[3]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[0]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[4]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[5]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[5]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[6]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[6]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[7]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[7]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[4]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[0]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[4]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[1]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[5]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[2]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[6]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[3]);
			this.addFrustumVertex(vertexConsumer4, matrix4f2, vector4fs[7]);
			poseStack.popPose();
		}
	}

	private void addFrustumVertex(VertexConsumer vertexConsumer, Matrix4f matrix4f, Vector4f vector4f) {
		vertexConsumer.addVertex(matrix4f, vector4f.x(), vector4f.y(), vector4f.z()).setColor(-16777216).setNormal(0.0F, 0.0F, -1.0F);
	}

	private void addFrustumQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f, Vector4f[] vector4fs, int i, int j, int k, int l, int m, int n, int o) {
		float f = 0.25F;
		vertexConsumer.addVertex(matrix4f, vector4fs[i].x(), vector4fs[i].y(), vector4fs[i].z()).setColor((float)m, (float)n, (float)o, 0.25F);
		vertexConsumer.addVertex(matrix4f, vector4fs[j].x(), vector4fs[j].y(), vector4fs[j].z()).setColor((float)m, (float)n, (float)o, 0.25F);
		vertexConsumer.addVertex(matrix4f, vector4fs[k].x(), vector4fs[k].y(), vector4fs[k].z()).setColor((float)m, (float)n, (float)o, 0.25F);
		vertexConsumer.addVertex(matrix4f, vector4fs[l].x(), vector4fs[l].y(), vector4fs[l].z()).setColor((float)m, (float)n, (float)o, 0.25F);
	}
}
