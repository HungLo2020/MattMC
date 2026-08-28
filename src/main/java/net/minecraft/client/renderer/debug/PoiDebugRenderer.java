package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.util.debug.DebugPoiInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class PoiDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int MAX_RENDER_DIST_FOR_POI_INFO = 30;
	private static final float TEXT_SCALE = 0.02F;
	private static final int ORANGE = -23296;
	private final BrainDebugRenderer brainRenderer;

	public PoiDebugRenderer(BrainDebugRenderer brainDebugRenderer) {
		this.brainRenderer = brainDebugRenderer;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		BlockPos blockPos = BlockPos.containing(d, e, f);
		debugValueAccess.forEachBlock(DebugSubscriptions.POIS, (blockPos2, debugPoiInfo) -> {
			if (blockPos.closerThan(blockPos2, 30.0)) {
				highlightPoi(poseStack, multiBufferSource, blockPos2);
				this.renderPoiInfo(poseStack, multiBufferSource, debugPoiInfo, debugValueAccess);
			}
		});
		this.brainRenderer.getGhostPois(debugValueAccess).forEach((blockPos2, list) -> {
			if (debugValueAccess.getBlockValue(DebugSubscriptions.POIS, blockPos2) == null) {
				if (blockPos.closerThan(blockPos2, 30.0)) {
					this.renderGhostPoi(poseStack, multiBufferSource, blockPos2, list);
				}
			}
		});
	}

	/** Copies live and ghost POI diagnostics into Rust-owned semantic streams. */
	public void collectRustSemantics(Minecraft minecraft, Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) return;
		DebugValueAccess access = minecraft.getConnection().createDebugValueAccess();
		BlockPos center = camera.getBlockPosition();
		PoseStack transform = new PoseStack();
		transform.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		float[] uvs = {0,0,1,0,1,1,0,1};
		access.forEachBlock(DebugSubscriptions.POIS, (pos, info) -> {
			if (!center.closerThan(pos, MAX_RENDER_DIST_FOR_POI_INFO)) return;
			submitBox(geometry, transform, uvs, pos, 0x4D3333FF);
			int line = 0;
			if (SharedConstants.DEBUG_BRAIN) {
				List<String> owners = getTicketHolderNames(info, false, access);
				submitLabel(text, camera, pos, line++, owners.size() < 4 ? "Owners: " + owners : owners.size() + " ticket holders", -256);
				List<String> candidates = getTicketHolderNames(info, true, access);
				submitLabel(text, camera, pos, line++, candidates.size() < 4 ? "Candidates: " + candidates : candidates.size() + " potential owners", ORANGE);
			}
			submitLabel(text, camera, pos, line++, "Free tickets: " + info.freeTicketCount(), -256);
			submitLabel(text, camera, pos, line, info.poiType().getRegisteredName(), -1);
		});
		this.brainRenderer.getGhostPois(access).forEach((pos, names) -> {
			if (access.getBlockValue(DebugSubscriptions.POIS, pos) != null || !center.closerThan(pos, MAX_RENDER_DIST_FOR_POI_INFO)) return;
			submitBox(geometry, transform, uvs, pos, 0x4D3333FF);
			submitLabel(text, camera, pos, 0, names.toString(), -256);
			submitLabel(text, camera, pos, 1, "Ghost POI", -65536);
		});
	}

	private static void submitBox(SubmitNodeStorage geometry, PoseStack transform, float[] uvs, BlockPos pos, int color) {
		float p = 0.05F, x0 = pos.getX() - p, y0 = pos.getY() - p, z0 = pos.getZ() - p, x1 = pos.getX() + 1 + p, y1 = pos.getY() + 1 + p, z1 = pos.getZ() + 1 + p;
		float[][] faces = {{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1},{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}};
		for (float[] face : faces) if (!geometry.submitColoredQuadsSemantic(transform, RenderType.debugFilledBox(), face, uvs, new int[]{color}, 15728880)) throw new IllegalStateException("Rust whole-frame POI route rejected semantic marker");
	}

	private static void submitLabel(SubmitNodeStorage text, Camera camera, BlockPos pos, int line, String value, int color) {
		PoseStack pose = new PoseStack();
		pose.translate(pos.getX() + 0.5 - camera.getPosition().x, pos.getY() + 1.3 + line * 0.2 - camera.getPosition().y, pos.getZ() + 0.5 - camera.getPosition().z);
		pose.mulPose(camera.rotation()); pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
		text.submitTextSemantic(0, pose, 0, 0, Component.literal(value).getVisualOrderText(), true, Font.DisplayMode.SEE_THROUGH, color, -1, 0, 0);
	}

	private static void highlightPoi(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos) {
		float f = 0.05F;
		DebugRenderer.renderFilledBox(poseStack, multiBufferSource, blockPos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
	}

	private void renderGhostPoi(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos, List<String> list) {
		float f = 0.05F;
		DebugRenderer.renderFilledBox(poseStack, multiBufferSource, blockPos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
		DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, list.toString(), blockPos, 0, -256, 0.02F);
		DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, "Ghost POI", blockPos, 1, -65536, 0.02F);
	}

	private void renderPoiInfo(PoseStack poseStack, MultiBufferSource multiBufferSource, DebugPoiInfo debugPoiInfo, DebugValueAccess debugValueAccess) {
		int i = 0;
		if (SharedConstants.DEBUG_BRAIN) {
			List<String> list = this.getTicketHolderNames(debugPoiInfo, false, debugValueAccess);
			if (list.size() < 4) {
				renderTextOverPoi(poseStack, multiBufferSource, "Owners: " + list, debugPoiInfo, i, -256);
			} else {
				renderTextOverPoi(poseStack, multiBufferSource, list.size() + " ticket holders", debugPoiInfo, i, -256);
			}

			i++;
			List<String> list2 = this.getTicketHolderNames(debugPoiInfo, true, debugValueAccess);
			if (list2.size() < 4) {
				renderTextOverPoi(poseStack, multiBufferSource, "Candidates: " + list2, debugPoiInfo, i, -23296);
			} else {
				renderTextOverPoi(poseStack, multiBufferSource, list2.size() + " potential owners", debugPoiInfo, i, -23296);
			}

			i++;
		}

		renderTextOverPoi(poseStack, multiBufferSource, "Free tickets: " + debugPoiInfo.freeTicketCount(), debugPoiInfo, i, -256);
		renderTextOverPoi(poseStack, multiBufferSource, debugPoiInfo.poiType().getRegisteredName(), debugPoiInfo, ++i, -1);
	}

	private static void renderTextOverPoi(PoseStack poseStack, MultiBufferSource multiBufferSource, String string, DebugPoiInfo debugPoiInfo, int i, int j) {
		DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, string, debugPoiInfo.pos(), i, j, 0.02F);
	}

	private List<String> getTicketHolderNames(DebugPoiInfo debugPoiInfo, boolean bl, DebugValueAccess debugValueAccess) {
		List<String> list = new ArrayList();
		debugValueAccess.forEachEntity(DebugSubscriptions.BRAINS, (entity, debugBrainDump) -> {
			boolean bl2 = bl ? debugBrainDump.hasPotentialPoi(debugPoiInfo.pos()) : debugBrainDump.hasPoi(debugPoiInfo.pos());
			if (bl2) {
				list.add(DebugEntityNameGenerator.getEntityName(entity.getUUID()));
			}
		});
		return list;
	}
}
