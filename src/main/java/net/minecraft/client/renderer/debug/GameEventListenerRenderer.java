package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class GameEventListenerRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float BOX_HEIGHT = 1.0F;

	private void forEachListener(DebugValueAccess debugValueAccess, GameEventListenerRenderer.ListenerVisitor listenerVisitor) {
		debugValueAccess.forEachBlock(
			DebugSubscriptions.GAME_EVENT_LISTENERS,
			(blockPos, debugGameEventListenerInfo) -> listenerVisitor.accept(blockPos.getCenter(), debugGameEventListenerInfo.listenerRadius())
		);
		debugValueAccess.forEachEntity(
			DebugSubscriptions.GAME_EVENT_LISTENERS,
			(entity, debugGameEventListenerInfo) -> listenerVisitor.accept(entity.position(), debugGameEventListenerInfo.listenerRadius())
		);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
		this.forEachListener(debugValueAccess, (vec3, i) -> {
			double g = i * 2.0;
			DebugRenderer.renderVoxelShape(poseStack, vertexConsumer, Shapes.create(AABB.ofSize(vec3, g, g, g)), -d, -e, -f, 1.0F, 1.0F, 0.0F, 0.35F, true);
		});
		VertexConsumer vertexConsumer2 = multiBufferSource.getBuffer(RenderType.debugFilledBox());
		this.forEachListener(
			debugValueAccess,
			(vec3, i) -> ShapeRenderer.addChainedFilledBoxVertices(
				poseStack,
				vertexConsumer2,
				vec3.x() - 0.25 - d,
				vec3.y() - e,
				vec3.z() - 0.25 - f,
				vec3.x() + 0.25 - d,
				vec3.y() - e + 1.0,
				vec3.z() + 0.25 - f,
				1.0F,
				1.0F,
				0.0F,
				0.35F
			)
		);
		this.forEachListener(debugValueAccess, (vec3, i) -> {
			DebugRenderer.renderFloatingText(poseStack, multiBufferSource, "Listener Origin", vec3.x(), vec3.y() + 1.8F, vec3.z(), -1, 0.025F);
			DebugRenderer.renderFloatingText(poseStack, multiBufferSource, BlockPos.containing(vec3).toString(), vec3.x(), vec3.y() + 1.5, vec3.z(), -6959665, 0.025F);
		});
		debugValueAccess.forEachEvent(
			DebugSubscriptions.GAME_EVENTS,
			(debugGameEventInfo, i, j) -> {
				Vec3 vec3 = debugGameEventInfo.pos();
				double dx = 0.4;
				AABB aABB = AABB.ofSize(vec3.add(0.0, 0.5, 0.0), 0.4, 0.9, 0.4);
				renderFilledBox(poseStack, multiBufferSource, aABB, 1.0F, 1.0F, 1.0F, 0.2F);
				DebugRenderer.renderFloatingText(
					poseStack, multiBufferSource, debugGameEventInfo.event().getRegisteredName(), vec3.x, vec3.y + 0.85F, vec3.z, -7564911, 0.0075F
				);
			}
		);
	}

	/** Copies listener radii, origins, and labels into Rust semantic streams. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame game-event debug route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized()) return;
		DebugValueAccess access = Minecraft.getInstance().getConnection().createDebugValueAccess();
		Vec3 cameraPosition = camera.getPosition();
		PoseStack pose = new PoseStack();
		pose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
		this.forEachListener(access, (origin, radius) -> {
			AABB radiusBox = AABB.ofSize(origin, radius * 2.0, radius * 2.0, radius * 2.0);
			queueLines(pose, radiusBox, 0xFFFFFF00);
			queueFilledBox(geometry, pose, AABB.ofSize(origin.add(0.0, 0.5, 0.0), 0.5, 1.0, 0.5), 0x5900FF00);
			queueLabel(text, camera, origin.add(0.0, 1.8, 0.0), "Listener Origin", -1, 0.025F);
			queueLabel(text, camera, origin.add(0.0, 1.5, 0.0), BlockPos.containing(origin).toString(), -6959665, 0.025F);
		});
		access.forEachEvent(DebugSubscriptions.GAME_EVENTS, (event, tick, sequence) -> {
			Vec3 origin = event.pos();
			AABB box = AABB.ofSize(origin.add(0.0, 0.5, 0.0), 0.4, 0.9, 0.4);
			queueFilledBox(geometry, pose, box, 0x33FFFFFF);
			queueLabel(text, camera, origin.add(0.0, 0.85, 0.0), event.event().getRegisteredName(), -7564911, 0.0075F);
		});
	}

	private static void queueLines(PoseStack pose, AABB box, int color) {
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(pose.last().pose(), boxEdges(box), color, 1.0F)) {
			throw new IllegalStateException("Rust whole-frame game-event debug route rejected listener radius");
		}
	}

	private static void queueFilledBox(SubmitNodeStorage geometry, PoseStack pose, AABB box, int color) {
		float x0=(float)box.minX,y0=(float)box.minY,z0=(float)box.minZ,x1=(float)box.maxX,y1=(float)box.maxY,z1=(float)box.maxZ;
		float[] uv={0,0,1,0,1,1,0,1}; int[] colors={color};
		for (int face=0; face<6; face++) {
			if (!geometry.submitColoredQuadsSemantic(pose, RenderType.debugFilledBox(), faceVertices(face,x0,y0,z0,x1,y1,z1), uv, colors, 15728880)) {
				throw new IllegalStateException("Rust whole-frame game-event debug route rejected origin marker");
			}
		}
	}

	private static void queueLabel(SubmitNodeStorage text, Camera camera, Vec3 origin, String value, int color, float scale) {
		PoseStack pose = new PoseStack();
		pose.translate(origin.x, origin.y, origin.z);
		pose.mulPose(camera.rotation()); pose.scale(scale, -scale, scale);
		var visual = Component.literal(value).getVisualOrderText();
		float left = -Minecraft.getInstance().font.width(visual) / 2.0F;
		text.submitTextSemantic(0, pose, left, 0.0F, visual, false, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, color, 0, 15728880, 0);
	}

	private static float[] boxEdges(AABB b) {
		return new float[] {(float)b.minX,(float)b.minY,(float)b.minZ,(float)b.maxX,(float)b.minY,(float)b.minZ,
			(float)b.maxX,(float)b.minY,(float)b.minZ,(float)b.maxX,(float)b.minY,(float)b.maxZ,
			(float)b.maxX,(float)b.minY,(float)b.maxZ,(float)b.minX,(float)b.minY,(float)b.maxZ,
			(float)b.minX,(float)b.minY,(float)b.maxZ,(float)b.minX,(float)b.minY,(float)b.minZ,
			(float)b.minX,(float)b.maxY,(float)b.minZ,(float)b.maxX,(float)b.maxY,(float)b.minZ,
			(float)b.maxX,(float)b.maxY,(float)b.minZ,(float)b.maxX,(float)b.maxY,(float)b.maxZ,
			(float)b.maxX,(float)b.maxY,(float)b.maxZ,(float)b.minX,(float)b.maxY,(float)b.maxZ,
			(float)b.minX,(float)b.maxY,(float)b.maxZ,(float)b.minX,(float)b.maxY,(float)b.minZ,
			(float)b.minX,(float)b.minY,(float)b.minZ,(float)b.minX,(float)b.maxY,(float)b.minZ,
			(float)b.maxX,(float)b.minY,(float)b.minZ,(float)b.maxX,(float)b.maxY,(float)b.minZ,
			(float)b.maxX,(float)b.minY,(float)b.maxZ,(float)b.maxX,(float)b.maxY,(float)b.maxZ,
			(float)b.minX,(float)b.minY,(float)b.maxZ,(float)b.minX,(float)b.maxY,(float)b.maxZ};
	}

	private static float[] faceVertices(int face,float x0,float y0,float z0,float x1,float y1,float z1){return switch(face){
		case 0->new float[]{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0};
		case 1->new float[]{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1};
		case 2->new float[]{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1};
		case 3->new float[]{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0};
		case 4->new float[]{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0};
		default->new float[]{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1};};}

	private static void renderFilledBox(PoseStack poseStack, MultiBufferSource multiBufferSource, AABB aABB, float f, float g, float h, float i) {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (camera.isInitialized()) {
			Vec3 vec3 = camera.getPosition().reverse();
			DebugRenderer.renderFilledBox(poseStack, multiBufferSource, aABB.move(vec3), f, g, h, i);
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface ListenerVisitor {
		void accept(Vec3 vec3, int i);
	}
}
