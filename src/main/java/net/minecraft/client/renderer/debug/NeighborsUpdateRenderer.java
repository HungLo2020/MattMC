package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class NeighborsUpdateRenderer implements DebugRenderer.SimpleDebugRenderer {

	/** Copies neighbor-update boxes and labels into Rust semantic streams. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (camera == null || !camera.isInitialized()) return;
		int expiry = DebugSubscriptions.NEIGHBOR_UPDATES.expireAfterTicks();
		double half = 1.0 / (expiry * 2.0);
		Map<BlockPos, LastUpdate> updates = new HashMap<>();
		DebugValueAccess access = net.minecraft.client.Minecraft.getInstance().getConnection().createDebugValueAccess();
		access.forEachEvent(DebugSubscriptions.NEIGHBOR_UPDATES, (pos, tick, sequence) -> {
			long age = sequence - tick;
			LastUpdate prior = updates.getOrDefault(pos, LastUpdate.NONE);
			updates.put(pos, prior.tryCount((int)age));
		});
		Vec3 cameraPosition = camera.getPosition();
		PoseStack linePose = new PoseStack();
		linePose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
		for (Entry<BlockPos, LastUpdate> entry : updates.entrySet()) {
			BlockPos pos = entry.getKey();
			LastUpdate update = entry.getValue();
			double inset = 0.002 + half * update.age;
			AABB box = new AABB(pos).inflate(0.002).deflate(inset);
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(
				linePose.last().pose(), boxEdges((float)box.minX, (float)box.minY, (float)box.minZ,
					(float)box.maxX, (float)box.maxY, (float)box.maxZ), 0xFFFFFFFF, 1.0F
			)) throw new IllegalStateException("Rust whole-frame neighbor-update debug route rejected semantic box");
			String label = Integer.toString(update.count);
			PoseStack textPose = new PoseStack();
			textPose.translate(pos.getX() + 0.5F, pos.getY() + 1.1F, pos.getZ() + 0.5F);
			textPose.mulPose(camera.rotation());
			textPose.scale(0.02F, -0.02F, 0.02F);
			var visual = Component.literal(label).getVisualOrderText();
			float left = -net.minecraft.client.Minecraft.getInstance().font.width(visual) / 2.0F;
			text.submitTextSemantic(0, textPose, left, 0.0F, visual, false,
				net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, -1, 0, 15728880, 0);
		}
	}

	private static float[] boxEdges(float x0, float y0, float z0, float x1, float y1, float z1) {
		return new float[] {x0,y0,z0,x1,y0,z0, x1,y0,z0,x1,y0,z1, x1,y0,z1,x0,y0,z1, x0,y0,z1,x0,y0,z0,
			x0,y1,z0,x1,y1,z0, x1,y1,z0,x1,y1,z1, x1,y1,z1,x0,y1,z1, x0,y1,z1,x0,y1,z0,
			x0,y0,z0,x0,y1,z0, x1,y0,z0,x1,y1,z0, x1,y0,z1,x1,y1,z1, x0,y0,z1,x0,y1,z1};
	}

	@Environment(EnvType.CLIENT)
	record LastUpdate(int count, int age) {
		static final NeighborsUpdateRenderer.LastUpdate NONE = new NeighborsUpdateRenderer.LastUpdate(0, Integer.MAX_VALUE);

		public NeighborsUpdateRenderer.LastUpdate tryCount(int i) {
			if (i == this.age) {
				return new NeighborsUpdateRenderer.LastUpdate(this.count + 1, i);
			} else {
				return i < this.age ? new NeighborsUpdateRenderer.LastUpdate(1, i) : this;
			}
		}
	}
}
