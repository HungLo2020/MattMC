package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class GameTestBlockHighlightRenderer {
	private static final int SHOW_POS_DURATION_MS = 10000;
	private static final float PADDING = 0.02F;
	private final Map<BlockPos, GameTestBlockHighlightRenderer.Marker> markers = Maps.<BlockPos, GameTestBlockHighlightRenderer.Marker>newHashMap();

	public void highlightPos(BlockPos blockPos, BlockPos blockPos2) {
		String string = blockPos2.toShortString();
		this.markers.put(blockPos, new GameTestBlockHighlightRenderer.Marker(-2147418368, string, Util.getMillis() + 10000L));
	}

	public void clear() {
		this.markers.clear();
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource) {
		long l = Util.getMillis();
		this.markers.entrySet().removeIf(entry -> l > ((GameTestBlockHighlightRenderer.Marker)entry.getValue()).removeAtTime);
		this.markers.forEach((blockPos, marker) -> this.renderMarker(poseStack, multiBufferSource, blockPos, marker));
	}

	private void renderMarker(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos, GameTestBlockHighlightRenderer.Marker marker) {
		DebugRenderer.renderFilledBox(poseStack, multiBufferSource, blockPos, 0.02F, marker.getR(), marker.getG(), marker.getB(), marker.getA() * 0.75F);
		if (!marker.text.isEmpty()) {
			double d = blockPos.getX() + 0.5;
			double e = blockPos.getY() + 1.2;
			double f = blockPos.getZ() + 0.5;
			DebugRenderer.renderFloatingText(poseStack, multiBufferSource, marker.text, d, e, f, -1, 0.01F, true, 0.0F, true);
		}
	}

	@Environment(EnvType.CLIENT)
	record Marker(int color, String text, long removeAtTime) {

		public float getR() {
			return ARGB.redFloat(this.color);
		}

		public float getG() {
			return ARGB.greenFloat(this.color);
		}

		public float getB() {
			return ARGB.blueFloat(this.color);
		}

		public float getA() {
			return ARGB.alphaFloat(this.color);
		}
	}
}
