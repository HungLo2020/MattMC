package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class LightDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private static final int MAX_RENDER_DIST = 10;

	public LightDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Copies nearby light diagnostics into Rust-owned semantic text. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage text) {
		Level level = this.minecraft.level;
		if (level == null) return;
		BlockPos center = BlockPos.containing(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
		LongSet sections = new LongOpenHashSet();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-MAX_RENDER_DIST, -MAX_RENDER_DIST, -MAX_RENDER_DIST), center.offset(MAX_RENDER_DIST, MAX_RENDER_DIST, MAX_RENDER_DIST))) {
			int light = level.getBrightness(LightLayer.SKY, pos);
			long section = SectionPos.blockToSection(pos.asLong());
			if (sections.add(section)) {
				String debug = level.getChunkSource().getLightEngine().getDebugData(LightLayer.SKY, SectionPos.of(section));
				if (debug != null) submitLabel(text, camera, SectionPos.sectionToBlockCoord(SectionPos.x(section), 8), SectionPos.sectionToBlockCoord(SectionPos.y(section), 8), SectionPos.sectionToBlockCoord(SectionPos.z(section), 8), debug, -65536, 0.3F);
			}
			if (light != 15) {
				float hue = (15 - light) / 15.0F * 0.5F + 0.16F;
				submitLabel(text, camera, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, String.valueOf(light), Mth.hsvToRgb(hue, 0.9F, 0.9F) | 0xFF000000, 0.02F);
			}
		}
	}

	private static void submitLabel(SubmitNodeStorage text, Camera camera, double x, double y, double z, String value, int color, float scale) {
		PoseStack pose = new PoseStack();
		pose.translate(x - camera.getPosition().x, y - camera.getPosition().y, z - camera.getPosition().z);
		pose.mulPose(camera.rotation());
		pose.scale(scale, -scale, scale);
		text.submitTextSemantic(0, pose, 0.0F, 0.0F, Component.literal(value).getVisualOrderText(), true,
			Font.DisplayMode.SEE_THROUGH, color, -1, 0, 0);
	}
}
