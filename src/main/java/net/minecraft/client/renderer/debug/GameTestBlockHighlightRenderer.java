package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import net.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class GameTestBlockHighlightRenderer {
	private static final int SHOW_POS_DURATION_MS = 10000;
	private static final float PADDING = 0.02F;
	private static final int MAX_MARKERS = 4096;
	private final Map<BlockPos, GameTestBlockHighlightRenderer.Marker> markers = Maps.<BlockPos, GameTestBlockHighlightRenderer.Marker>newHashMap();

	public void highlightPos(BlockPos blockPos, BlockPos blockPos2) {
		if (!this.markers.containsKey(blockPos) && this.markers.size() >= MAX_MARKERS) {
			throw new IllegalStateException("game-test highlight marker capacity exceeded " + MAX_MARKERS);
		}
		String string = blockPos2.toShortString();
		this.markers.put(blockPos, new GameTestBlockHighlightRenderer.Marker(-2147418368, string, Util.getMillis() + 10000L));
	}

	public void clear() {
		this.markers.clear();
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException("Java game-test block highlights are unavailable while Rust owns whole-frame presentation or Vulkan is selected");
		}
		long l = Util.getMillis();
		this.markers.entrySet().removeIf(entry -> l > ((GameTestBlockHighlightRenderer.Marker)entry.getValue()).removeAtTime);
		this.markers.forEach((blockPos, marker) -> this.renderMarker(poseStack, multiBufferSource, blockPos, marker));
	}

	/** Copies active game-test markers into the explicit Rust debug and text streams. */
	public void collectRustSemantics(PoseStack poseStack, SubmitNodeStorage geometryStorage, SubmitNodeStorage textStorage) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled() && !this.markers.isEmpty()) {
				throw new IllegalStateException("Rust whole-frame game-test highlight route is unavailable while Rust owns presentation");
			}
			return;
		}
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()
			&& this.markers.values().stream().anyMatch(marker -> !marker.text.isEmpty())) {
			throw new IllegalStateException("Rust whole-frame game-test label route is unavailable while Rust owns presentation");
		}
		long now = Util.getMillis();
		this.markers.entrySet().removeIf(entry -> now > entry.getValue().removeAtTime);
		if (this.markers.isEmpty()) return;
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (!camera.isInitialized()) return;
		Vec3 cameraPos = camera.getPosition();
		SubmitNodeCollection geometry = geometryStorage.order(0);
		SubmitNodeCollection text = textStorage.order(1);
		Font font = Minecraft.getInstance().font;
		for (Map.Entry<BlockPos, Marker> entry : this.markers.entrySet()) {
			BlockPos pos = entry.getKey();
			Marker marker = entry.getValue();
			float x0 = (float)(pos.getX() - cameraPos.x - PADDING);
			float y0 = (float)(pos.getY() - cameraPos.y - PADDING);
			float z0 = (float)(pos.getZ() - cameraPos.z - PADDING);
			float x1 = x0 + 1.0F + PADDING * 2.0F;
			float y1 = y0 + 1.0F + PADDING * 2.0F;
			float z1 = z0 + 1.0F + PADDING * 2.0F;
			float[] vertices = {
				x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0,
				x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1,
				x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0,
				x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1,
				x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1,
				x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0
			};
			float[] uvs = new float[48];
			int[] colors = new int[6];
			java.util.Arrays.fill(colors, marker.color);
			if (!geometry.submitColoredQuadsSemantic(poseStack, RenderType.debugFilledBox(), vertices, uvs, colors, 15728880)) {
				throw new IllegalStateException("Rust whole-frame game-test highlight route rejected semantic box");
			}
			if (!marker.text.isEmpty()) {
				poseStack.pushPose();
				poseStack.translate((float)(pos.getX() + 0.5 - cameraPos.x), (float)(pos.getY() + 1.27 - cameraPos.y), (float)(pos.getZ() + 0.5 - cameraPos.z));
				poseStack.mulPose(camera.rotation());
				poseStack.scale(0.02F, -0.02F, 0.02F);
				float left = -font.width(marker.text) * 0.5F;
				text.submitTextSemantic(poseStack, left, 0.0F, Component.literal(marker.text).getVisualOrderText(), false,
					Font.DisplayMode.SEE_THROUGH, 15728880, 0xFFFFFFFF, 0, 0);
				poseStack.popPose();
			}
		}
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
