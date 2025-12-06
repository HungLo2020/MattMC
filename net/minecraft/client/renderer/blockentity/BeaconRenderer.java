package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BeaconRenderer<T extends BlockEntity & BeaconBeamOwner> implements BlockEntityRenderer<T, BeaconRenderState> {
	public static final ResourceLocation BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
	public static final int MAX_RENDER_Y = 2048;
	private static final float BEAM_SCALE_THRESHOLD = 96.0F;
	public static final float SOLID_BEAM_RADIUS = 0.2F;
	public static final float BEAM_GLOW_RADIUS = 0.25F;

	public BeaconRenderState createRenderState() {
		return new BeaconRenderState();
	}

	public void extractRenderState(
		T blockEntity, BeaconRenderState beaconRenderState, float f, Vec3 vec3, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, beaconRenderState, f, vec3, crumblingOverlay);
		extract(blockEntity, beaconRenderState, f, vec3);
	}

	public static <T extends BlockEntity & BeaconBeamOwner> void extract(T blockEntity, BeaconRenderState beaconRenderState, float f, Vec3 vec3) {
		beaconRenderState.animationTime = blockEntity.getLevel() != null ? Math.floorMod(blockEntity.getLevel().getGameTime(), 40) + f : 0.0F;
		beaconRenderState.sections = blockEntity.getBeamSections()
			.stream()
			.map(section -> new BeaconRenderState.Section(section.getColor(), section.getHeight()))
			.toList();
		float g = (float)vec3.subtract(beaconRenderState.blockPos.getCenter()).horizontalDistance();
		LocalPlayer localPlayer = Minecraft.getInstance().player;
		beaconRenderState.beamRadiusScale = localPlayer != null && localPlayer.isScoping() ? 1.0F : Math.max(1.0F, g / 96.0F);
	}

	public void submit(BeaconRenderState beaconRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		int i = 0;

		for (int j = 0; j < beaconRenderState.sections.size(); j++) {
			BeaconRenderState.Section section = (BeaconRenderState.Section)beaconRenderState.sections.get(j);
			submitBeaconBeam(
				poseStack,
				submitNodeCollector,
				beaconRenderState.beamRadiusScale,
				beaconRenderState.animationTime,
				i,
				j == beaconRenderState.sections.size() - 1 ? 2048 : section.height(),
				section.color()
			);
			i += section.height();
		}
	}

	private static void submitBeaconBeam(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, float f, float g, int i, int j, int k) {
		submitBeaconBeam(poseStack, submitNodeCollector, BEAM_LOCATION, 1.0F, g, i, j, k, 0.2F * f, 0.25F * f);
	}

	public static void submitBeaconBeam(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ResourceLocation resourceLocation, float f, float g, int i, int j, int k, float h, float l
	) {
		final int iFinal = i;
		final int jFinal = j;
		final int kFinal = k;
		final float fFinal = f;
		final float hFinal = h;
		final float lFinal = l;
		int m = iFinal + jFinal;
		final int mFinal = m;
		poseStack.pushPose();
		poseStack.translate(0.5, 0.0, 0.5);
		float n = jFinal < 0 ? g : -g;
		float o = Mth.frac(n * 0.2F - Mth.floor(n * 0.1F));
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(g * 2.25F - 45.0F));
		final float t1 = -hFinal;
		final float w1 = -hFinal;
		final float z1 = -1.0F + o;
		final float aa1 = jFinal * fFinal * (0.5F / hFinal) + z1;
		submitNodeCollector.submitCustomGeometry(
			poseStack,
			RenderType.beaconBeam(resourceLocation, false),
			(pose, vertexConsumer) -> renderPart(pose, vertexConsumer, kFinal, iFinal, mFinal, 0.0F, hFinal, hFinal, 0.0F, t1, 0.0F, 0.0F, w1, 0.0F, 1.0F, aa1, z1)
		);
		poseStack.popPose();
		final float p2 = -lFinal;
		final float q2 = -lFinal;
		final float s2 = -lFinal;
		final float t2 = -lFinal;
		final float z2 = -1.0F + o;
		final float aa2 = jFinal * fFinal + z2;
		submitNodeCollector.submitCustomGeometry(
			poseStack,
			RenderType.beaconBeam(resourceLocation, true),
			(pose, vertexConsumer) -> renderPart(pose, vertexConsumer, ARGB.color(32, kFinal), iFinal, mFinal, p2, q2, lFinal, s2, t2, lFinal, lFinal, lFinal, 0.0F, 1.0F, aa2, z2)
		);
		poseStack.popPose();
	}

	private static void renderPart(
		PoseStack.Pose pose,
		VertexConsumer vertexConsumer,
		int i,
		int j,
		int k,
		float f,
		float g,
		float h,
		float l,
		float m,
		float n,
		float o,
		float p,
		float q,
		float r,
		float s,
		float t
	) {
		renderQuad(pose, vertexConsumer, i, j, k, f, g, h, l, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, o, p, m, n, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, h, l, o, p, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, m, n, f, g, q, r, s, t);
	}

	private static void renderQuad(
		PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, int j, int k, float f, float g, float h, float l, float m, float n, float o, float p
	) {
		addVertex(pose, vertexConsumer, i, k, f, g, n, o);
		addVertex(pose, vertexConsumer, i, j, f, g, n, p);
		addVertex(pose, vertexConsumer, i, j, h, l, m, p);
		addVertex(pose, vertexConsumer, i, k, h, l, m, o);
	}

	private static void addVertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, int j, float f, float g, float h, float k) {
		vertexConsumer.addVertex(pose, f, (float)j, g)
			.setColor(i)
			.setUv(h, k)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(15728880)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
	}

	@Override
	public boolean shouldRender(T blockEntity, Vec3 vec3) {
		return Vec3.atCenterOf(blockEntity.getBlockPos()).multiply(1.0, 0.0, 1.0).closerThan(vec3.multiply(1.0, 0.0, 1.0), this.getViewDistance());
	}
}
