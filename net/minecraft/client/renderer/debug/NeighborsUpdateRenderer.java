package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public class NeighborsUpdateRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		int i = DebugSubscriptions.NEIGHBOR_UPDATES.expireAfterTicks();
		double g = 1.0 / (i * 2);
		Map<BlockPos, NeighborsUpdateRenderer.LastUpdate> map = new HashMap();
		debugValueAccess.forEachEvent(DebugSubscriptions.NEIGHBOR_UPDATES, (blockPosx, ix, j) -> {
			long l = j - ix;
			NeighborsUpdateRenderer.LastUpdate lastUpdatex = (NeighborsUpdateRenderer.LastUpdate)map.getOrDefault(blockPosx, NeighborsUpdateRenderer.LastUpdate.NONE);
			map.put(blockPosx, lastUpdatex.tryCount((int)l));
		});
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());

		for (Entry<BlockPos, NeighborsUpdateRenderer.LastUpdate> entry : map.entrySet()) {
			BlockPos blockPos = (BlockPos)entry.getKey();
			NeighborsUpdateRenderer.LastUpdate lastUpdate = (NeighborsUpdateRenderer.LastUpdate)entry.getValue();
			AABB aABB = new AABB(BlockPos.ZERO).inflate(0.002).deflate(g * lastUpdate.age).move(blockPos.getX(), blockPos.getY(), blockPos.getZ()).move(-d, -e, -f);
			ShapeRenderer.renderLineBox(poseStack.last(), vertexConsumer, aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
		}

		for (Entry<BlockPos, NeighborsUpdateRenderer.LastUpdate> entry : map.entrySet()) {
			BlockPos blockPos = (BlockPos)entry.getKey();
			NeighborsUpdateRenderer.LastUpdate lastUpdate = (NeighborsUpdateRenderer.LastUpdate)entry.getValue();
			DebugRenderer.renderFloatingText(poseStack, multiBufferSource, String.valueOf(lastUpdate.count), blockPos.getX(), blockPos.getY(), blockPos.getZ(), -1);
		}
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
