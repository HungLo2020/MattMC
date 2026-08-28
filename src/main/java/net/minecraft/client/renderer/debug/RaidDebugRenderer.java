package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class RaidDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int MAX_RENDER_DIST = 160;
	private static final float TEXT_SCALE = 0.04F;
	private final Minecraft minecraft;

	public RaidDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		BlockPos blockPos = this.getCamera().getBlockPosition();
		debugValueAccess.forEachChunk(DebugSubscriptions.RAIDS, (chunkPos, list) -> {
			for (BlockPos blockPos2 : list) {
				if (blockPos.closerThan(blockPos2, 160.0)) {
					highlightRaidCenter(poseStack, multiBufferSource, blockPos2);
				}
			}
		});
	}

	/** Copies subscribed nearby raid centers into Rust-owned semantic boxes and text. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) return;
		DebugValueAccess access = this.minecraft.getConnection().createDebugValueAccess();
		PoseStack transform = new PoseStack();
		transform.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		float[] uvs = {0,0,1,0,1,1,0,1};
		BlockPos center = camera.getBlockPosition();
		access.forEachChunk(DebugSubscriptions.RAIDS, (chunk, centers) -> {
			for (BlockPos pos : centers) {
				if (!center.closerThan(pos, MAX_RENDER_DIST)) continue;
				float x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ(), x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
				float[][] faces = {{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1},{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}};
				for (float[] face : faces) if (!geometry.submitColoredQuadsSemantic(transform, RenderType.debugFilledBox(), face, uvs, new int[]{0x2600FF00}, 15728880)) {
					throw new IllegalStateException("Rust whole-frame raid route rejected semantic center box");
				}
				PoseStack label = new PoseStack();
				label.translate(pos.getX() + 0.5 - camera.getPosition().x, pos.getY() + 1.3 - camera.getPosition().y, pos.getZ() + 0.5 - camera.getPosition().z);
				label.mulPose(camera.rotation()); label.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
				text.submitTextSemantic(0, label, 0, 0, Component.literal("Raid center").getVisualOrderText(), true,
					Font.DisplayMode.SEE_THROUGH, -65536, -1, 0, 0);
			}
		});
	}

	private static void highlightRaidCenter(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos) {
		DebugRenderer.renderFilledUnitCube(poseStack, multiBufferSource, blockPos, 1.0F, 0.0F, 0.0F, 0.15F);
		renderTextOverBlock(poseStack, multiBufferSource, "Raid center", blockPos, -65536);
	}

	private static void renderTextOverBlock(PoseStack poseStack, MultiBufferSource multiBufferSource, String string, BlockPos blockPos, int i) {
		double d = blockPos.getX() + 0.5;
		double e = blockPos.getY() + 1.3;
		double f = blockPos.getZ() + 0.5;
		DebugRenderer.renderFloatingText(poseStack, multiBufferSource, string, d, e, f, i, 0.04F, true, 0.0F, true);
	}

	private Camera getCamera() {
		return this.minecraft.gameRenderer.getMainCamera();
	}
}
