package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

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
