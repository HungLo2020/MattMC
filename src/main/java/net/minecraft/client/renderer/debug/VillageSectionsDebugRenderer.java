package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;

@Environment(EnvType.CLIENT)
public class VillageSectionsDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		debugValueAccess.forEachBlock(DebugSubscriptions.VILLAGE_SECTIONS, (blockPos, unit) -> {
			SectionPos sectionPos = SectionPos.of(blockPos);
			DebugRenderer.renderFilledUnitCube(poseStack, multiBufferSource, sectionPos.center(), 0.2F, 1.0F, 0.2F, 0.15F);
		});
	}

	/** Copies subscribed village-section markers into Rust-owned semantic quads. */
	public void collectRustSemantics(Minecraft minecraft, Camera camera, SubmitNodeStorage geometry) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) return;
		DebugValueAccess access = minecraft.getConnection().createDebugValueAccess();
		PoseStack transform = new PoseStack();
		transform.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		float[] uvs = {0,0,1,0,1,1,0,1};
		access.forEachBlock(DebugSubscriptions.VILLAGE_SECTIONS, (blockPos, unit) -> {
			SectionPos section = SectionPos.of(blockPos);
			float x0 = section.minBlockX() - 0.2F, y0 = section.minBlockY() - 0.2F, z0 = section.minBlockZ() - 0.2F;
			float x1 = x0 + 1.4F, y1 = y0 + 1.4F, z1 = z0 + 1.4F;
			float[][] faces = {{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1},{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}};
			for (float[] face : faces) if (!geometry.submitColoredQuadsSemantic(transform, RenderType.debugFilledBox(), face, uvs, new int[]{0x2600FF00}, 15728880)) {
				throw new IllegalStateException("Rust whole-frame village-section route rejected semantic marker");
			}
		});
	}
}
