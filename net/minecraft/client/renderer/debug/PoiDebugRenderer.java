package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.util.debug.DebugPoiInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;

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
