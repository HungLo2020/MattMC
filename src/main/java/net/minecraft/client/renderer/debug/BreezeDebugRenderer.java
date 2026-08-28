package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class BreezeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int JUMP_TARGET_LINE_COLOR = ARGB.color(255, 255, 100, 255);
	private static final int TARGET_LINE_COLOR = ARGB.color(255, 100, 255, 255);
	private static final int INNER_CIRCLE_COLOR = ARGB.color(255, 0, 255, 0);
	private static final int MIDDLE_CIRCLE_COLOR = ARGB.color(255, 255, 165, 0);
	private static final int OUTER_CIRCLE_COLOR = ARGB.color(255, 255, 0, 0);
	private static final int CIRCLE_VERTICES = 20;
	private static final float SEGMENT_SIZE_RADIANS = (float) (Math.PI / 10);
	private final Minecraft minecraft;

	public BreezeDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		ClientLevel clientLevel = this.minecraft.level;
		debugValueAccess.forEachEntity(
			DebugSubscriptions.BREEZES,
			(entity, debugBreezeInfo) -> {
				debugBreezeInfo.attackTarget()
					.map(clientLevel::getEntity)
					.map(entityx -> entityx.getPosition(this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true)))
					.ifPresent(vec3 -> {
						drawLine(poseStack, multiBufferSource, d, e, f, entity.position(), vec3, TARGET_LINE_COLOR);
						Vec3 vec32 = vec3.add(0.0, 0.01F, 0.0);
						drawCircle(poseStack.last().pose(), d, e, f, multiBufferSource.getBuffer(RenderType.debugLineStrip(2.0)), vec32, 4.0F, INNER_CIRCLE_COLOR);
						drawCircle(poseStack.last().pose(), d, e, f, multiBufferSource.getBuffer(RenderType.debugLineStrip(2.0)), vec32, 8.0F, MIDDLE_CIRCLE_COLOR);
						drawCircle(poseStack.last().pose(), d, e, f, multiBufferSource.getBuffer(RenderType.debugLineStrip(2.0)), vec32, 24.0F, OUTER_CIRCLE_COLOR);
					});
				debugBreezeInfo.jumpTarget()
					.ifPresent(
						blockPos -> {
							drawLine(poseStack, multiBufferSource, d, e, f, entity.position(), blockPos.getCenter(), JUMP_TARGET_LINE_COLOR);
							DebugRenderer.renderFilledBox(
								poseStack, multiBufferSource, AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(blockPos)).move(-d, -e, -f), 1.0F, 0.0F, 0.0F, 1.0F
							);
						}
					);
			}
		);
	}

	/** Copies Breeze target diagnostics into Rust semantic primitives. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame Breeze-debug route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized() || minecraft.level == null) return;
		DebugValueAccess access = minecraft.getConnection().createDebugValueAccess();
		org.joml.Matrix4f transform = new org.joml.Matrix4f().translate(
			(float)-camera.getPosition().x, (float)-camera.getPosition().y, (float)-camera.getPosition().z
		);
		access.forEachEntity(DebugSubscriptions.BREEZES, (entity, info) -> {
			info.attackTarget().map(minecraft.level::getEntity).map(target -> target.getPosition(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true))).ifPresent(target -> {
				queueLine(transform, entity.position(), target, TARGET_LINE_COLOR);
				queueCircle(transform, target.add(0.0, 0.01, 0.0), 4.0F, INNER_CIRCLE_COLOR);
				queueCircle(transform, target.add(0.0, 0.01, 0.0), 8.0F, MIDDLE_CIRCLE_COLOR);
				queueCircle(transform, target.add(0.0, 0.01, 0.0), 24.0F, OUTER_CIRCLE_COLOR);
			});
			info.jumpTarget().ifPresent(pos -> {
				queueLine(transform, entity.position(), pos.getCenter(), JUMP_TARGET_LINE_COLOR);
				queueFilledBox(geometry, transform, AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos)), 0xFFFF0000);
			});
		});
	}

	private static void queueLine(org.joml.Matrix4f transform, Vec3 a, Vec3 b, int color) {
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,
			new float[] {(float)a.x,(float)a.y,(float)a.z,(float)b.x,(float)b.y,(float)b.z}, color, 2.0F))
			throw new IllegalStateException("Rust whole-frame Breeze-debug route rejected target line");
	}
	private static void queueCircle(org.joml.Matrix4f transform, Vec3 center, float radius, int color) {
		for (int i=0;i<20;i++) { float a=i*(float)(Math.PI/10), b=(i+1)*(float)(Math.PI/10);
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,
				new float[] {(float)(center.x+radius*Math.cos(a)),(float)center.y,(float)(center.z+radius*Math.sin(a)),(float)(center.x+radius*Math.cos(b)),(float)center.y,(float)(center.z+radius*Math.sin(b))}, color, 2.0F))
				throw new IllegalStateException("Rust whole-frame Breeze-debug route rejected target circle"); }
	}
	private static void queueFilledBox(SubmitNodeStorage geometry, org.joml.Matrix4f transform, AABB b, int color) {
		PoseStack pose = new PoseStack(); pose.last().pose().set(transform);
		float x0=(float)b.minX,y0=(float)b.minY,z0=(float)b.minZ,x1=(float)b.maxX,y1=(float)b.maxY,z1=(float)b.maxZ;
		float[] uv={0,0,1,0,1,1,0,1}; int[] c={color};
		float[][] faces={{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1},{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}};
		for (float[] face:faces) if(!geometry.submitColoredQuadsSemantic(pose,RenderType.debugFilledBox(),face,uv,c,15728880)) throw new IllegalStateException("Rust whole-frame Breeze-debug route rejected jump marker");
	}

	private static void drawLine(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, Vec3 vec3, Vec3 vec32, int i) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.debugLineStrip(2.0));
		vertexConsumer.addVertex(poseStack.last(), (float)(vec3.x - d), (float)(vec3.y - e), (float)(vec3.z - f)).setColor(i);
		vertexConsumer.addVertex(poseStack.last(), (float)(vec32.x - d), (float)(vec32.y - e), (float)(vec32.z - f)).setColor(i);
	}

	private static void drawCircle(Matrix4f matrix4f, double d, double e, double f, VertexConsumer vertexConsumer, Vec3 vec3, float g, int i) {
		for (int j = 0; j < 20; j++) {
			drawCircleVertex(j, matrix4f, d, e, f, vertexConsumer, vec3, g, i);
		}

		drawCircleVertex(0, matrix4f, d, e, f, vertexConsumer, vec3, g, i);
	}

	private static void drawCircleVertex(int i, Matrix4f matrix4f, double d, double e, double f, VertexConsumer vertexConsumer, Vec3 vec3, float g, int j) {
		float h = i * (float) (Math.PI / 10);
		Vec3 vec32 = vec3.add(g * Math.cos(h), 0.0, g * Math.sin(h));
		vertexConsumer.addVertex(matrix4f, (float)(vec32.x - d), (float)(vec32.y - e), (float)(vec32.z - f)).setColor(j);
	}
}
