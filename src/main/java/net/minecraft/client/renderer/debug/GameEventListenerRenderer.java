package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

	/** Copies listener radii, origins, and labels into Rust semantic streams. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
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

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface ListenerVisitor {
		void accept(Vec3 vec3, int i);
	}
}
