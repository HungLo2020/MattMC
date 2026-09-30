package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.client.Camera;

@Environment(EnvType.CLIENT)
public class ChunkCullingDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	public static final Direction[] DIRECTIONS = Direction.values();
	private final Minecraft minecraft;

	public ChunkCullingDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Copies visible-section occlusion diagnostics into Rust-owned semantic primitives. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry) {
		LevelRenderer levelRenderer = this.minecraft.levelRenderer;
		boolean paths = this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_PATHS);
		boolean visibility = this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_VISIBILITY);
		if (!paths && !visibility) return;
		float camX = (float)camera.getPosition().x, camY = (float)camera.getPosition().y, camZ = (float)camera.getPosition().z;
		SectionOcclusionGraph graph = levelRenderer.getSectionOcclusionGraph();
		for (SectionRenderDispatcher.RenderSection section : levelRenderer.getVisibleSections()) {
			SectionOcclusionGraph.Node node = graph.getNode(section);
			if (node == null) continue;
			BlockPos origin = section.getRenderOrigin();
			Matrix4f transform = new Matrix4f().translation(origin.getX() - camX, origin.getY() - camY, origin.getZ() - camZ);
			if (paths) {
				int rgb = node.step == 0 ? 0 : Mth.hsvToRgb(node.step / 50.0F, 0.9F, 0.9F);
				for (Direction direction : DIRECTIONS) {
					if (node.hasSourceDirection(direction.ordinal())) {
						line(transform, 8.0F, 8.0F, 8.0F,
							8.0F - 16.0F * direction.getStepX(),
							8.0F - 16.0F * direction.getStepY(),
							8.0F - 16.0F * direction.getStepZ(), 0xFF000000 | rgb);
					}
				}
			}
			if (visibility && section.getSectionMesh().hasRenderableLayers()) {
				int missing = 0;
				for (Direction first : DIRECTIONS) for (Direction second : DIRECTIONS) {
					if (!section.getSectionMesh().facesCanSeeEachother(first, second)) {
						missing++;
						line(transform, 8.0F + 8.0F * first.getStepX(), 8.0F + 8.0F * first.getStepY(), 8.0F + 8.0F * first.getStepZ(),
							8.0F + 8.0F * second.getStepX(), 8.0F + 8.0F * second.getStepY(), 8.0F + 8.0F * second.getStepZ(), 0xFFFF0000);
					}
				}
				if (missing > 0) {
					float[][] faces = {
						{0.5F,15.5F,0.5F, 15.5F,15.5F,0.5F, 15.5F,15.5F,15.5F, 0.5F,15.5F,15.5F},
						{0.5F,0.5F,15.5F, 15.5F,0.5F,15.5F, 15.5F,0.5F,0.5F, 0.5F,0.5F,0.5F},
						{0.5F,0.5F,0.5F, 15.5F,0.5F,0.5F, 15.5F,15.5F,0.5F, 0.5F,15.5F,0.5F},
						{15.5F,0.5F,15.5F, 0.5F,0.5F,15.5F, 0.5F,15.5F,15.5F, 15.5F,15.5F,15.5F},
						{0.5F,0.5F,0.5F, 0.5F,0.5F,15.5F, 15.5F,0.5F,15.5F, 15.5F,0.5F,0.5F},
						{0.5F,15.5F,15.5F, 0.5F,15.5F,0.5F, 15.5F,15.5F,0.5F, 15.5F,15.5F,15.5F}
					};
					PoseStack pose = new PoseStack(); pose.last().pose().set(transform);
					for (float[] face : faces) if (!geometry.submitColoredQuadsSemantic(pose, RenderType.debugQuads(), face,
							new float[]{0,0,1,0,1,1,0,1}, new int[]{0x33999900}, 15728880)) {
						throw new IllegalStateException("Rust whole-frame chunk-culling route rejected semantic visibility quads");
					}
				}
			}
		}
		Frustum captured = levelRenderer.getCapturedFrustum();
		if (captured != null) {
			Vector4f[] points = captured.getFrustumPoints();
			Matrix4f transform = new Matrix4f().translation((float)(captured.getCamX() - camX), (float)(captured.getCamY() - camY), (float)(captured.getCamZ() - camZ));
			int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
			for (int[] edge : edges) line(transform, points[edge[0]].x(), points[edge[0]].y(), points[edge[0]].z(), points[edge[1]].x(), points[edge[1]].y(), points[edge[1]].z(), 0xFF000000);
		}
	}

	private static void line(Matrix4f transform, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,
				new float[]{x0,y0,z0,x1,y1,z1}, color, 1.0F)) {
			throw new IllegalStateException("Rust whole-frame chunk-culling route rejected semantic line");
		}
	}

}
