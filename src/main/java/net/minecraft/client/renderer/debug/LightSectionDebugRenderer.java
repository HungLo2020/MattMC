package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage.SectionType;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class LightSectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500L);
	private static final int RADIUS = 10;
	private static final Vector4f LIGHT_AND_BLOCKS_COLOR = new Vector4f(1.0F, 1.0F, 0.0F, 0.25F);
	private static final Vector4f LIGHT_ONLY_COLOR = new Vector4f(0.25F, 0.125F, 0.0F, 0.125F);
	private final Minecraft minecraft;
	private final LightLayer lightLayer;
	private Instant lastUpdateTime = Instant.now();
	@Nullable
	private LightSectionDebugRenderer.SectionData data;

	public LightSectionDebugRenderer(Minecraft minecraft, LightLayer lightLayer) {
		this.minecraft = minecraft;
		this.lightLayer = lightLayer;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java light-section debug rendering is unavailable on selected Vulkan");
		}
		Instant instant = Instant.now();
		if (this.data == null || Duration.between(this.lastUpdateTime, instant).compareTo(REFRESH_INTERVAL) > 0) {
			this.lastUpdateTime = instant;
			this.data = new LightSectionDebugRenderer.SectionData(
				this.minecraft.level.getLightEngine(), SectionPos.of(this.minecraft.player.blockPosition()), 10, this.lightLayer
			);
		}

		renderEdges(poseStack, this.data.lightAndBlocksShape, this.data.minPos, multiBufferSource, d, e, f, LIGHT_AND_BLOCKS_COLOR);
		renderEdges(poseStack, this.data.lightShape, this.data.minPos, multiBufferSource, d, e, f, LIGHT_ONLY_COLOR);
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.debugSectionQuads());
		renderFaces(poseStack, this.data.lightAndBlocksShape, this.data.minPos, vertexConsumer, d, e, f, LIGHT_AND_BLOCKS_COLOR);
		renderFaces(poseStack, this.data.lightShape, this.data.minPos, vertexConsumer, d, e, f, LIGHT_ONLY_COLOR);
	}

	/** Copies light-section edges and faces into Rust semantic primitives. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame light-section debug route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized() || minecraft.level == null) return;
		Instant now = Instant.now();
		if (this.data == null || Duration.between(this.lastUpdateTime, now).compareTo(REFRESH_INTERVAL) > 0) {
			this.lastUpdateTime = now;
			this.data = new SectionData(this.minecraft.level.getLightEngine(), SectionPos.of(this.minecraft.player.blockPosition()), RADIUS, this.lightLayer);
		}
		SectionData snapshot = this.data;
		org.joml.Matrix4f transform = new org.joml.Matrix4f().translate(
			(float)-camera.getPosition().x, (float)-camera.getPosition().y, (float)-camera.getPosition().z
		);
		collectEdges(snapshot.lightAndBlocksShape, snapshot.minPos, transform, geometry, 0x66FFFF00);
		collectEdges(snapshot.lightShape, snapshot.minPos, transform, geometry, 0x33204000);
		collectFaces(snapshot.lightAndBlocksShape, snapshot.minPos, transform, geometry, 0x40FFFF00);
		collectFaces(snapshot.lightShape, snapshot.minPos, transform, geometry, 0x20204000);
	}

	private static void collectEdges(DiscreteVoxelShape shape, SectionPos min, org.joml.Matrix4f transform, SubmitNodeStorage geometry, int color) {
		shape.forAllEdges((x0,y0,z0,x1,y1,z1) -> {
			float ax=SectionPos.sectionToBlockCoord(min.x())+x0*16.0F, ay=SectionPos.sectionToBlockCoord(min.y())+y0*16.0F, az=SectionPos.sectionToBlockCoord(min.z())+z0*16.0F;
			float bx=SectionPos.sectionToBlockCoord(min.x())+x1*16.0F, by=SectionPos.sectionToBlockCoord(min.y())+y1*16.0F, bz=SectionPos.sectionToBlockCoord(min.z())+z1*16.0F;
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,new float[]{ax,ay,az,bx,by,bz},color,1.0F)) throw new IllegalStateException("Rust whole-frame light-section debug route rejected edge");
		}, true);
	}

	private static void collectFaces(DiscreteVoxelShape shape, SectionPos min, org.joml.Matrix4f transform, SubmitNodeStorage geometry, int color) {
		shape.forAllFaces((direction,x,y,z) -> {
			float x0=SectionPos.sectionToBlockCoord(min.x())+x*16.0F, y0=SectionPos.sectionToBlockCoord(min.y())+y*16.0F, z0=SectionPos.sectionToBlockCoord(min.z())+z*16.0F;
			float x1=x0+16.0F,y1=y0+16.0F,z1=z0+16.0F; float[] v=switch(direction){
				case WEST->new float[]{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0}; case EAST->new float[]{x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1};
				case DOWN->new float[]{x0,y0,z1,x0,y0,z0,x1,y0,z0,x1,y0,z1}; case UP->new float[]{x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0};
				case NORTH->new float[]{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0}; default->new float[]{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1};};
			PoseStack pose=new PoseStack();pose.last().pose().set(transform); if(!geometry.submitColoredQuadsSemantic(pose,RenderType.debugFilledBox(),v,new float[]{0,0,1,0,1,1,0,1},new int[]{color},15728880))throw new IllegalStateException("Rust whole-frame light-section debug route rejected face");
		});
	}

	private static void renderFaces(
		PoseStack poseStack,
		DiscreteVoxelShape discreteVoxelShape,
		SectionPos sectionPos,
		VertexConsumer vertexConsumer,
		double d,
		double e,
		double f,
		Vector4f vector4f
	) {
		discreteVoxelShape.forAllFaces((direction, i, j, k) -> {
			int l = i + sectionPos.getX();
			int m = j + sectionPos.getY();
			int n = k + sectionPos.getZ();
			renderFace(poseStack, vertexConsumer, direction, d, e, f, l, m, n, vector4f);
		});
	}

	private static void renderEdges(
		PoseStack poseStack,
		DiscreteVoxelShape discreteVoxelShape,
		SectionPos sectionPos,
		MultiBufferSource multiBufferSource,
		double d,
		double e,
		double f,
		Vector4f vector4f
	) {
		discreteVoxelShape.forAllEdges((i, j, k, l, m, n) -> {
			int o = i + sectionPos.getX();
			int p = j + sectionPos.getY();
			int q = k + sectionPos.getZ();
			int r = l + sectionPos.getX();
			int s = m + sectionPos.getY();
			int t = n + sectionPos.getZ();
			VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.debugLineStrip(1.0));
			renderEdge(poseStack, vertexConsumer, d, e, f, o, p, q, r, s, t, vector4f);
		}, true);
	}

	private static void renderFace(
		PoseStack poseStack, VertexConsumer vertexConsumer, Direction direction, double d, double e, double f, int i, int j, int k, Vector4f vector4f
	) {
		float g = (float)(SectionPos.sectionToBlockCoord(i) - d);
		float h = (float)(SectionPos.sectionToBlockCoord(j) - e);
		float l = (float)(SectionPos.sectionToBlockCoord(k) - f);
		ShapeRenderer.renderFace(
			poseStack.last().pose(), vertexConsumer, direction, g, h, l, g + 16.0F, h + 16.0F, l + 16.0F, vector4f.x(), vector4f.y(), vector4f.z(), vector4f.w()
		);
	}

	private static void renderEdge(
		PoseStack poseStack, VertexConsumer vertexConsumer, double d, double e, double f, int i, int j, int k, int l, int m, int n, Vector4f vector4f
	) {
		float g = (float)(SectionPos.sectionToBlockCoord(i) - d);
		float h = (float)(SectionPos.sectionToBlockCoord(j) - e);
		float o = (float)(SectionPos.sectionToBlockCoord(k) - f);
		float p = (float)(SectionPos.sectionToBlockCoord(l) - d);
		float q = (float)(SectionPos.sectionToBlockCoord(m) - e);
		float r = (float)(SectionPos.sectionToBlockCoord(n) - f);
		Matrix4f matrix4f = poseStack.last().pose();
		vertexConsumer.addVertex(matrix4f, g, h, o).setColor(vector4f.x(), vector4f.y(), vector4f.z(), 1.0F);
		vertexConsumer.addVertex(matrix4f, p, q, r).setColor(vector4f.x(), vector4f.y(), vector4f.z(), 1.0F);
	}

	@Environment(EnvType.CLIENT)
	static final class SectionData {
		final DiscreteVoxelShape lightAndBlocksShape;
		final DiscreteVoxelShape lightShape;
		final SectionPos minPos;

		SectionData(LevelLightEngine levelLightEngine, SectionPos sectionPos, int i, LightLayer lightLayer) {
			int j = i * 2 + 1;
			this.lightAndBlocksShape = new BitSetDiscreteVoxelShape(j, j, j);
			this.lightShape = new BitSetDiscreteVoxelShape(j, j, j);

			for (int k = 0; k < j; k++) {
				for (int l = 0; l < j; l++) {
					for (int m = 0; m < j; m++) {
						SectionPos sectionPos2 = SectionPos.of(sectionPos.x() + m - i, sectionPos.y() + l - i, sectionPos.z() + k - i);
						SectionType sectionType = levelLightEngine.getDebugSectionType(lightLayer, sectionPos2);
						if (sectionType == SectionType.LIGHT_AND_DATA) {
							this.lightAndBlocksShape.fill(m, l, k);
							this.lightShape.fill(m, l, k);
						} else if (sectionType == SectionType.LIGHT_ONLY) {
							this.lightShape.fill(m, l, k);
						}
					}
				}
			}

			this.minPos = SectionPos.of(sectionPos.x() - i, sectionPos.y() - i, sectionPos.z() - i);
		}
	}
}
