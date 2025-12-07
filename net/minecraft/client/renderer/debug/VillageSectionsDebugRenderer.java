package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
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
}
