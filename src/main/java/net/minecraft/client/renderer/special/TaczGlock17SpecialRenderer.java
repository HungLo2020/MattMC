package net.minecraft.client.renderer.special;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.ScissorState;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.blaze3d.vertex.VertexFormat;
import net.math.Axis;
import net.minecraft.Util;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.world.WorldRenderRoutePolicy;
import net.minecraft.client.tacz.TaczGlock17AnimationController;
import net.minecraft.client.tacz.TaczGunRefitScreen;
import net.minecraft.client.tacz.TaczKeyMappings;
import net.minecraft.client.tacz.TaczMuzzleFlashData;
import net.minecraft.client.tacz.TaczRefitTransform;
import net.minecraft.client.tacz.TaczScopeData;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.TaczAttachmentType;
import net.minecraft.world.item.TaczAttachmentItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczRefitGun;
import net.logging.LogUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicCapability;
import net.vulkanic.VulkanicClearBuffer;

@Environment(EnvType.CLIENT)
public class TaczGlock17SpecialRenderer implements NoDataSpecialModelRenderer {
	private static final int MAX_SEMANTIC_BEDROCK_QUADS = 16_384;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<String> FUNCTIONAL_MARKER_NODES = Set.of("lefthand_pos", "righthand_pos", "muzzle_flash", "shell");
	private static final Pattern TACZ_NUMBERED_NODE = Pattern.compile("^(.*?)(?:_(\\d+))?$");
	private static final long MUZZLE_FLASH_TIME_RANGE_MILLIS = 50L;
	private static final Map<String, AttachmentRenderData> ATTACHMENT_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Long> RETICLE_DEBUG_LAST_LOG_NANOS = new ConcurrentHashMap<>();
	private static final long RETICLE_DEBUG_INTERVAL_NANOS = 500_000_000L;
	private static long muzzleFlashShootTimeMillis = -1L;
	private static float muzzleFlashRandomRotate;
	public static final RenderPipeline TACZ_ENTITY_CUTOUT_STENCIL_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
		.withLocation(ResourceLocation.withDefaultNamespace("pipeline/tacz_entity_cutout_stencil"))
		.withShaderDefine("ALPHA_CUTOUT", 0.1F)
		.withSampler("Sampler1")
		.withColorWrite(false)
		.withDepthWrite(false)
		.build();
	private static final Function<ResourceLocation, RenderType> TACZ_ENTITY_CUTOUT_STENCIL = Util.memoize(
		texture -> RenderType.create(
			"tacz_entity_cutout_stencil",
			1536,
			TACZ_ENTITY_CUTOUT_STENCIL_PIPELINE,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(texture, false))
				.setLightmapState(RenderStateShard.LIGHTMAP)
				.setOverlayState(RenderStateShard.OVERLAY)
				.createCompositeState(false)
		)
	);
	public static final RenderPipeline TACZ_ENTITY_CUTOUT_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
		.withLocation(ResourceLocation.withDefaultNamespace("pipeline/tacz_entity_cutout_no_depth"))
		.withShaderDefine("ALPHA_CUTOUT", 0.1F)
		.withSampler("Sampler1")
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withDepthWrite(false)
		.build();
	private static final Function<ResourceLocation, RenderType> TACZ_ENTITY_CUTOUT_NO_DEPTH = Util.memoize(
		texture -> RenderType.create(
			"tacz_entity_cutout_no_depth",
			1536,
			TACZ_ENTITY_CUTOUT_NO_DEPTH_PIPELINE,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(texture, false))
				.setLightmapState(RenderStateShard.LIGHTMAP)
				.setOverlayState(RenderStateShard.OVERLAY)
				.createCompositeState(false)
		)
	);
	public static final RenderPipeline TACZ_DEBUG_TRIANGLE_FAN_STENCIL_PIPELINE =
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(ResourceLocation.withDefaultNamespace("pipeline/tacz_debug_triangle_fan_stencil"))
			.withCull(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withColorWrite(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
			.build();
	private static final RenderType TACZ_DEBUG_TRIANGLE_FAN_STENCIL = RenderType.create(
		"tacz_debug_triangle_fan_stencil",
		1536,
		false,
		true,
		TACZ_DEBUG_TRIANGLE_FAN_STENCIL_PIPELINE,
		RenderType.CompositeState.builder().createCompositeState(false)
	);
	private final String gunId;
	private final ResourceLocation texture;
	private final BedrockGunGeometry geometry;
	private final BedrockAnimationSet animations;

	public TaczGlock17SpecialRenderer() {
		this("glock_17");
	}

	public TaczGlock17SpecialRenderer(String gunId) {
		this.gunId = gunId;
		this.texture = ResourceLocation.withDefaultNamespace("textures/gun/uv/" + gunId + ".png");
		this.geometry = BedrockGunGeometry.load(ResourceLocation.withDefaultNamespace("geo_models/gun/" + gunId + "_geo.json"));
		this.animations = BedrockAnimationSet.load(ResourceLocation.withDefaultNamespace("animations/" + gunId + ".animation.json"));
	}

	public static void triggerMuzzleFlash() {
		muzzleFlashShootTimeMillis = System.currentTimeMillis();
		muzzleFlashRandomRotate = (float)(Math.random() * 360.0);
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		this.submitAnimated(itemDisplayContext, poseStack, submitNodeCollector, i, j, ItemStack.EMPTY);
	}

	public void submitFirstPerson(ItemStack itemStack, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, int overlay) {
		this.submitAnimated(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, poseStack, submitNodeCollector, light, overlay, itemStack);
	}

	private void submitAnimated(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, ItemStack itemStack) {
		poseStack.pushPose();
		AnimationPose animationPose = this.animations.sample(TaczGlock17AnimationController.snapshot(itemStack));
		GunRenderContext gunRenderContext = GunRenderContext.from(itemStack);
		this.applyTaczTransform(itemDisplayContext, poseStack, animationPose, itemStack);
		RenderType gunRenderType = RenderType.entityCutoutNoCull(this.texture);
		ScopedAttachment scopedAttachment = this.scopedAttachment(itemStack, itemDisplayContext, animationPose);
		boolean rustWholeFrame = VulkanicAPI.isVulkanBackendSelected()
			&& WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan()
			&& !submitNodeCollector.isSemanticCoverageOnly();
		if (!submitNodeCollector.isSemanticCoverageOnly()
			&& VulkanicAPI.isVulkanBackendSelected()
			&& net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan()) {
			throw new IllegalStateException("Rust whole-frame TACZ route is unavailable; Java custom gun geometry is not a fallback");
		}
		if (rustWholeFrame) {
			if (!this.submitSemanticBedrockRoots(poseStack, itemDisplayContext, animationPose, gunRenderContext, this.geometry.roots(), this.texture, gunRenderType, submitNodeCollector, i, j)) {
				throw new IllegalStateException("Rust whole-frame TACZ route rejected semantic Bedrock gun mesh");
			}
			this.submitAttachments(itemStack, itemDisplayContext, poseStack, submitNodeCollector, i, j, animationPose, false);
		} else if (scopedAttachment != null) {
			submitNodeCollector.submitCustomGeometry(
				poseStack,
				gunRenderType,
				new TaczScopedGunRenderer(this.geometry, itemDisplayContext, i, j, animationPose, gunRenderContext, scopedAttachment)
			);
			this.submitAttachments(itemStack, itemDisplayContext, poseStack, submitNodeCollector, i, j, animationPose, true);
		} else {
			submitNodeCollector.submitCustomGeometry(poseStack, gunRenderType, (pose, vertexConsumer) -> {
				PoseStack modelPoseStack = new PoseStack();
				modelPoseStack.last().set(pose);
				for (BedrockNode root : this.geometry.roots()) {
					root.render(modelPoseStack, itemDisplayContext, vertexConsumer, i, j, animationPose, null, gunRenderContext);
				}
			});
			this.submitAttachments(itemStack, itemDisplayContext, poseStack, submitNodeCollector, i, j, animationPose, false);
		}
		if (this.shouldRenderFirstPersonArms(itemDisplayContext)) {
			this.submitFirstPersonArms(itemDisplayContext, poseStack, submitNodeCollector, i, animationPose);
		}
		this.submitMuzzleFlash(itemStack, itemDisplayContext, poseStack, submitNodeCollector, i, j, animationPose);
		poseStack.popPose();
	}

	/**
	 * Scope/sight metadata alone does not require a stencil pass. Attachments
	 * without ocular aperture or division nodes are ordinary animated Bedrock
	 * roots and can use the copied semantic quad ABI; retain fail-closed behavior
	 * only for the node relationships whose masking semantics are not represented
	 * by the current explicit first-person GAL stream.
	 */
	private static boolean requiresOpticalStencil(AttachmentRenderData data) {
		return data != null
			&& (data.scope() || data.sight())
			&& (!data.ocularNodes().isEmpty() || !data.geometry().divisionNodeGroups().isEmpty());
	}

	private boolean submitSemanticBedrockRoots(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		AnimationPose animationPose,
		GunRenderContext gunRenderContext,
		List<BedrockNode> roots,
		ResourceLocation textureIdentity,
		RenderType renderType,
		SubmitNodeCollector submitNodeCollector,
		int light,
		int overlay
	) {
		Map<Integer, SemanticBedrockBatch> batches = new HashMap<>();
		for (BedrockNode root : roots) {
			collectSemanticBedrockNode(poseStack, itemDisplayContext, animationPose, null, gunRenderContext, root, light, batches);
		}
		if (batches.isEmpty()) return false;
		PoseStack identityPoseStack = new PoseStack();
		for (Map.Entry<Integer, SemanticBedrockBatch> entry : batches.entrySet()) {
			SemanticBedrockBatch batch = entry.getValue();
			if (!submitNodeCollector.submitTexturedQuads(identityPoseStack, renderType, textureIdentity,
				batch.vertices(), batch.uvs(), batch.colors(), entry.getKey())) return false;
		}
		return true;
	}

	/**
	 * Emits a bounded optical attachment in two semantic batches: ocular
	 * geometry writes stencil value one, then the copied attachment roots test
	 * that value. The Rust hand target owns the actual depth/stencil pass and
	 * color copy boundary; Java contributes only transformed mesh data.
	 */
	private boolean submitSemanticOpticalAttachment(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		AnimationPose animationPose,
		AttachmentRenderData attachmentData,
		SubmitNodeCollector submitNodeCollector,
		int light,
		int overlay
	) {
		List<OcularNode> ocularNodes = attachmentData.ocularNodes();
		if (ocularNodes.isEmpty()) return false;
		for (OcularNode ocularNode : ocularNodes) {
			BedrockNode node = attachmentData.geometry().nodes().get(ocularNode.name());
			if (node == null) return false;
			PoseStack maskPoseStack = new PoseStack();
			maskPoseStack.last().set(poseStack.last());
			if (!attachmentData.geometry().applyAnimatedNodePath(ocularNode.name(), maskPoseStack, animationPose)) {
				return false;
			}
			if (!this.submitSemanticBedrockRootsWithMode(maskPoseStack, itemDisplayContext, animationPose,
				GunRenderContext.EMPTY, List.of(node), attachmentData.texture(), submitNodeCollector, light, overlay,
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.MATERIAL_MODE_OPTICAL_STENCIL_WRITE, attachmentData)) {
				return false;
			}
		}
		return this.submitSemanticBedrockRootsWithMode(poseStack, itemDisplayContext, animationPose,
			GunRenderContext.EMPTY, attachmentData.geometry().roots(), attachmentData.texture(), submitNodeCollector,
			light, overlay, net.vulkanic.world.RustGalWorldPrimitiveRenderer.MATERIAL_MODE_OPTICAL_STENCIL_TEST,
			attachmentData.withSpecialNodesVisible());
	}

	private boolean submitSemanticBedrockRootsWithMode(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		AnimationPose animationPose,
		GunRenderContext gunRenderContext,
		List<BedrockNode> roots,
		ResourceLocation textureIdentity,
		SubmitNodeCollector submitNodeCollector,
		int light,
		int overlay,
		int materialMode,
		AttachmentRenderData attachmentData
	) {
		Map<Integer, SemanticBedrockBatch> batches = new HashMap<>();
		for (BedrockNode root : roots) {
			collectSemanticBedrockNode(poseStack, itemDisplayContext, animationPose, attachmentData, gunRenderContext, root, light, batches);
		}
		if (batches.isEmpty()) return false;
		PoseStack identityPoseStack = new PoseStack();
		for (Map.Entry<Integer, SemanticBedrockBatch> entry : batches.entrySet()) {
			SemanticBedrockBatch batch = entry.getValue();
			if (!submitNodeCollector.submitOpticalTexturedQuads(identityPoseStack, RenderType.entityCutout(textureIdentity),
				textureIdentity, batch.vertices(), batch.uvs(), batch.colors(), entry.getKey(), materialMode)) return false;
		}
		return true;
	}

	private static void collectSemanticBedrockNode(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		AnimationPose animationPose,
		AttachmentRenderData attachmentRenderData,
		GunRenderContext gunRenderContext,
		BedrockNode node,
		int baseLight,
		Map<Integer, SemanticBedrockBatch> batches
	) {
		if (node.cubes.isEmpty() && node.children.isEmpty()) return;
		poseStack.pushPose();
		NodePose nodePose = animationPose.node(node.name);
		node.translateAndRotate(poseStack, nodePose);
		if (node.hiddenByScopedFirstPerson(itemDisplayContext, attachmentRenderData, animationPose) || !gunRenderContext.visible(node)) {
			poseStack.popPose();
			return;
		}
		int childLight = baseLight;
		if (!node.hiddenMarker && nodePose.visible()) {
			int light = node.name != null && node.name.endsWith("_illuminated") ? LightTexture.pack(15, 15) : baseLight;
			childLight = light;
			SemanticBedrockBatch batch = batches.computeIfAbsent(light, ignored -> new SemanticBedrockBatch());
			for (BedrockCube cube : node.cubes) {
				for (BedrockPolygon polygon : cube.polygons) {
					if (!polygon.empty) batch.append(poseStack.last().pose(), polygon);
				}
			}
		}
		for (BedrockNode child : node.children) collectSemanticBedrockNode(poseStack, itemDisplayContext, animationPose, attachmentRenderData, gunRenderContext, child, childLight, batches);
		poseStack.popPose();
	}

	private static final class SemanticBedrockBatch {
		private final List<Float> vertexList = new ArrayList<>();
		private final List<Float> uvList = new ArrayList<>();
		private final List<Integer> colorList = new ArrayList<>();

		private void append(org.joml.Matrix4f transform, BedrockPolygon polygon) {
			if (colorList.size() >= MAX_SEMANTIC_BEDROCK_QUADS) {
				throw new IllegalStateException("Rust TACZ semantic Bedrock mesh exceeds bounded quad budget");
			}
			for (BedrockVertex vertex : polygon.vertices) {
				Vector3f position = transform.transformPosition(vertex.x / 16.0F, vertex.y / 16.0F, vertex.z / 16.0F, new Vector3f());
				vertexList.add(position.x()); vertexList.add(position.y()); vertexList.add(position.z());
				uvList.add(vertex.u); uvList.add(vertex.v);
			}
			// The semantic GUI transport carries one color per vertex, matching
			// the four Bedrock polygon vertices copied above.
			for (int vertex = 0; vertex < 4; vertex++) colorList.add(0xFFFFFFFF);
		}

		private float[] vertices() { float[] values = new float[vertexList.size()]; for (int i = 0; i < values.length; i++) values[i] = vertexList.get(i); return values; }
		private float[] uvs() { float[] values = new float[uvList.size()]; for (int i = 0; i < values.length; i++) values[i] = uvList.get(i); return values; }
		private int[] colors() { int[] values = new int[colorList.size()]; for (int i = 0; i < values.length; i++) values[i] = colorList.get(i); return values; }
	}

	private void submitMuzzleFlash(
		ItemStack gunStack,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		int overlay,
		AnimationPose animationPose
	) {
		if (gunStack.isEmpty() || !itemDisplayContext.firstPerson() || !this.geometry.hasNode("muzzle_flash") || this.isSilenced(gunStack)) {
			return;
		}
		long ageMillis = System.currentTimeMillis() - muzzleFlashShootTimeMillis;
		if (ageMillis < 0L || ageMillis > MUZZLE_FLASH_TIME_RANGE_MILLIS) {
			return;
		}
		TaczMuzzleFlashData muzzleFlash = TaczMuzzleFlashData.get(this.gunId);
		if (muzzleFlash == null) {
			return;
		}

		float scale = 0.5F * muzzleFlash.scale();
		float scaleTime = MUZZLE_FLASH_TIME_RANGE_MILLIS / 2.0F;
		if (ageMillis < scaleTime) {
			scale *= ageMillis / scaleTime;
		}
		float alpha = 1.0F - Mth.clamp(ageMillis / (float)MUZZLE_FLASH_TIME_RANGE_MILLIS, 0.0F, 1.0F);
		float renderScale = scale;
		float renderAlpha = alpha;
		RenderType renderType = RenderType.entityTranslucent(muzzleFlash.texture());
		if (VulkanicAPI.isVulkanBackendSelected()
			&& WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan()) {
			PoseStack flashPoseStack = new PoseStack();
			flashPoseStack.last().set(poseStack.last());
			if (!this.geometry.applyAnimatedNodePath("muzzle_flash", flashPoseStack, animationPose)) {
				return;
			}
			flashPoseStack.mulPose(Axis.ZP.rotationDegrees(muzzleFlashRandomRotate));
			float half = 0.4F * renderScale;
			float[] vertices = {-half, -half, 0.0F, half, -half, 0.0F, half, half, 0.0F, -half, half, 0.0F};
			float[] uvs = {0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F};
			int color = (Mth.clamp((int)(renderAlpha * 255.0F), 0, 255) << 24) | 0xFFFFFF;
			if (!submitNodeCollector.submitTranslucentTexturedQuad(flashPoseStack, renderType, muzzleFlash.texture(), vertices, uvs, color, LightTexture.FULL_BRIGHT)) {
				throw new IllegalStateException("Rust whole-frame muzzle flash route rejected semantic translucent quad");
			}
			return;
		}
		submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
			PoseStack flashPoseStack = new PoseStack();
			flashPoseStack.last().set(pose);
			if (!this.geometry.applyAnimatedNodePath("muzzle_flash", flashPoseStack, animationPose)) {
				return;
			}
			flashPoseStack.mulPose(Axis.ZP.rotationDegrees(muzzleFlashRandomRotate));
			renderMuzzleFlashQuad(flashPoseStack.last(), vertexConsumer, renderScale, renderAlpha, overlay);
		});
	}

	private boolean isSilenced(ItemStack gunStack) {
		ItemStack muzzleStack = TaczRefitGun.getStoredAttachment(gunStack, TaczAttachmentType.MUZZLE);
		return muzzleStack.getItem() instanceof TaczAttachmentItem attachment && attachment.getAttachmentId().contains("silencer");
	}

	private static void renderMuzzleFlashQuad(PoseStack.Pose pose, VertexConsumer vertexConsumer, float scale, float alpha, int overlay) {
		float half = 0.4F * scale;
		int color = (Mth.clamp((int)(alpha * 255.0F), 0, 255) << 24) | 0xFFFFFF;
		vertexConsumer.addVertex(pose, -half, -half, 0.0F).setColor(color).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, half, -half, 0.0F).setColor(color).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, half, half, 0.0F).setColor(color).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, -half, half, 0.0F).setColor(color).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	private boolean shouldRenderFirstPersonArms(ItemDisplayContext itemDisplayContext) {
		if (!itemDisplayContext.firstPerson()) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		return !(minecraft.screen instanceof TaczGunRefitScreen) && TaczRefitTransform.openingProgress() <= 0.0F;
	}

	private ScopedAttachment scopedAttachment(ItemStack gunStack, ItemDisplayContext itemDisplayContext, AnimationPose animationPose) {
		if (gunStack.isEmpty() || !itemDisplayContext.firstPerson() || !this.geometry.hasNode("scope_pos")) {
			return null;
		}

		ItemStack scopeStack = TaczRefitGun.getStoredAttachment(gunStack, TaczAttachmentType.SCOPE);
		if (!(scopeStack.getItem() instanceof TaczAttachmentItem attachment)) {
			return null;
		}

		AttachmentRenderData attachmentData = attachmentData(attachment.getAttachmentId());
		if (attachmentData == null || !attachmentData.scope()) {
			return null;
		}

		return new ScopedAttachment("scope_pos", attachmentData);
	}

	private void submitAttachments(
		ItemStack gunStack,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		int overlay,
		AnimationPose animationPose,
		boolean skipScope
	) {
		if (gunStack.isEmpty()) {
			return;
		}

		for (TaczAttachmentType type : TaczAttachmentType.values()) {
			if (type == TaczAttachmentType.NONE || type == TaczAttachmentType.AMMO_MOD) {
				continue;
			}
			if (skipScope && type == TaczAttachmentType.SCOPE) {
				continue;
			}

			ItemStack attachmentStack = TaczRefitGun.getStoredAttachment(gunStack, type);
			if (!(attachmentStack.getItem() instanceof TaczAttachmentItem attachment)) {
				continue;
			}

			AttachmentRenderData attachmentData = attachmentData(attachment.getAttachmentId());
			if (attachmentData == null) {
				continue;
			}

			String marker = attachmentMarker(type);
			if (!this.geometry.hasNode(marker)) {
				continue;
			}
			if (VulkanicAPI.isVulkanBackendSelected()
				&& WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan()) {
				PoseStack attachmentPoseStack = new PoseStack();
				attachmentPoseStack.last().set(poseStack.last());
				if (!this.geometry.applyAnimatedNodePath(marker, attachmentPoseStack, animationPose)) {
					continue;
				}
				attachmentPoseStack.translate(0.0F, -1.5F, 0.0F);
				boolean optical = itemDisplayContext.firstPerson() && requiresOpticalStencil(attachmentData);
				boolean submitted = optical
					? this.submitSemanticOpticalAttachment(attachmentPoseStack, itemDisplayContext, animationPose, attachmentData,
						submitNodeCollector, light, overlay)
					: this.submitSemanticBedrockRoots(attachmentPoseStack, itemDisplayContext, animationPose, GunRenderContext.EMPTY,
						attachmentData.geometry().roots(), attachmentData.texture(), RenderType.entityCutout(attachmentData.texture()), submitNodeCollector, light, overlay);
				if (!submitted) {
					if (optical) {
						throw new IllegalStateException("Rust whole-frame TACZ optical attachment route rejected semantic stencil geometry");
					}
					throw new IllegalStateException("Rust whole-frame TACZ route rejected semantic attachment mesh");
				}
				continue;
			}

			RenderType renderType = RenderType.entityCutout(attachmentData.texture());
			if (itemDisplayContext.firstPerson() && (attachmentData.scope() || attachmentData.sight())) {
				submitNodeCollector.submitCustomGeometry(
					poseStack,
					renderType,
					new TaczScopedAttachmentRenderer(this.geometry, marker, itemDisplayContext, light, overlay, animationPose, attachmentData, null)
				);
				continue;
			}

			submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
				PoseStack attachmentPoseStack = new PoseStack();
				attachmentPoseStack.last().set(pose);
				if (this.geometry.applyAnimatedNodePath(marker, attachmentPoseStack, animationPose)) {
					attachmentPoseStack.translate(0.0F, -1.5F, 0.0F);
					for (BedrockNode root : attachmentData.geometry().roots()) {
						root.render(attachmentPoseStack, itemDisplayContext, vertexConsumer, light, overlay, animationPose, attachmentData);
					}
				}
			});
		}
	}

	private static String attachmentMarker(TaczAttachmentType type) {
		return switch (type) {
			case SCOPE -> "scope_pos";
			case MUZZLE -> "muzzle_pos";
			case LASER -> "laser_pos";
			case GRIP -> "grip_pos";
			case STOCK -> "stock_pos";
			case EXTENDED_MAG -> "magazine";
			default -> type.getSerializedName() + "_pos";
		};
	}

	private static AttachmentRenderData attachmentData(String attachmentId) {
		if (attachmentId == null || attachmentId.isEmpty()) {
			return null;
		}
		return ATTACHMENT_CACHE.computeIfAbsent(attachmentId, TaczGlock17SpecialRenderer::loadAttachmentData);
	}

	private static AttachmentRenderData loadAttachmentData(String attachmentId) {
		TaczScopeData.AttachmentDisplay display = TaczScopeData.display(attachmentId);
		if (display == null) {
			return null;
		}

		try {
			BedrockGunGeometry geometry = BedrockGunGeometry.load(display.geometryLocation());
			return new AttachmentRenderData(
				geometry,
				display.textureLocation(),
				display.scope(),
				display.sight(),
				geometry.ocularNodes(),
				true
			);
		} catch (Exception exception) {
			LOGGER.warn("Failed to load TACZ attachment display {}", attachmentId, exception);
			return null;
		}
	}

	private void submitFirstPersonArms(
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		AnimationPose animationPose
	) {
		Minecraft minecraft = Minecraft.getInstance();
		AbstractClientPlayer player = minecraft.player;
		if (!itemDisplayContext.firstPerson() || player == null || player.isInvisible()) {
			return;
		}

		AvatarRenderer<AbstractClientPlayer> renderer = minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
		ResourceLocation skin = player.getSkin().body().texturePath();
		this.submitFirstPersonArm(
			"righthand_pos",
			HumanoidArm.RIGHT,
			player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE),
			renderer,
			skin,
			poseStack,
			submitNodeCollector,
			light,
			animationPose
		);
		this.submitFirstPersonArm(
			"lefthand_pos",
			HumanoidArm.LEFT,
			player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE),
			renderer,
			skin,
			poseStack,
			submitNodeCollector,
			light,
			animationPose
		);
	}

	private void submitFirstPersonArm(
		String handNode,
		HumanoidArm arm,
		boolean sleeveVisible,
		AvatarRenderer<AbstractClientPlayer> renderer,
		ResourceLocation skin,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		AnimationPose animationPose
	) {
		poseStack.pushPose();
		if (this.geometry.applyAnimatedNodePath(handNode, poseStack, animationPose)) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			if (arm == HumanoidArm.RIGHT) {
				renderer.renderRightHand(poseStack, submitNodeCollector, light, skin, sleeveVisible);
			} else {
				renderer.renderLeftHand(poseStack, submitNodeCollector, light, skin, sleeveVisible);
			}
		}
		poseStack.popPose();
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		set.add(new Vector3f(-1.0F, -1.0F, -1.0F));
		set.add(new Vector3f(1.0F, 1.0F, 1.0F));
	}

	private void applyTaczTransform(ItemDisplayContext itemDisplayContext, PoseStack poseStack, AnimationPose animationPose, ItemStack itemStack) {
		if (itemDisplayContext.firstPerson()) {
			float aimProgress = effectiveAimProgress(animationPose);
			float refitProgress = TaczRefitTransform.openingProgress();
			this.applyCameraAnimation(poseStack, animationPose);
			poseStack.translate(0.0F, 1.5F, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			this.geometry.applyFirstPersonPositioning(
				poseStack,
				aimProgress,
				this.scopeViewMatrix(itemStack),
				refitProgress,
				TaczRefitTransform.previousType(),
				TaczRefitTransform.currentType(),
				TaczRefitTransform.viewProgress()
			);
			this.geometry.applyAnimationConstraintTransform(poseStack, animationPose, aimProgress * (1.0F - refitProgress));
			return;
		}

		poseStack.translate(0.5F, 2.0F, 0.5F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		switch (itemDisplayContext) {
			case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
				this.geometry.applyPositioningNode("thirdperson_hand", poseStack, 0.6F, 0.6F, 0.6F);
				poseStack.scale(0.6F, 0.6F, 0.6F);
			}
			case GROUND -> {
				this.geometry.applyPositioningNode("ground", poseStack, 0.6F, 0.6F, 0.6F);
				poseStack.scale(0.6F, 0.6F, 0.6F);
			}
			case FIXED -> {
				this.geometry.applyPositioningNode("fixed", poseStack, 1.2F, 1.2F, 1.2F);
				poseStack.scale(1.2F, 1.2F, 1.2F);
			}
			case GUI -> {
				this.geometry.applyPositioningNode("fixed", poseStack, 1.2F, 1.2F, 1.2F);
				poseStack.scale(0.82F, 0.82F, 0.82F);
			}
			default -> poseStack.scale(0.6F, 0.6F, 0.6F);
		}
	}

	private Matrix4f scopeViewMatrix(ItemStack itemStack) {
		TaczScopeData.AttachmentDisplay scope = TaczScopeData.scope(itemStack);
		if (scope == null || !this.geometry.hasNode("scope_pos")) {
			return null;
		}

		AttachmentRenderData attachmentData = attachmentData(scope.id());
		String scopeViewNode = scope.scopeViewNodeName();
		if (attachmentData == null || !attachmentData.geometry().hasNode(scopeViewNode)) {
			return null;
		}

		return new Matrix4f(attachmentData.geometry().positioningMatrix(scopeViewNode)).mul(this.geometry.positioningMatrix("scope_pos"));
	}

	private void applyCameraAnimation(PoseStack poseStack, AnimationPose animationPose) {
		NodePose camera = animationPose.node("camera");
		if (camera.rotation.x() != 0.0F) {
			poseStack.mulPose(Axis.XP.rotation(camera.rotation.x()));
		}
		if (camera.rotation.y() != 0.0F) {
			poseStack.mulPose(Axis.YP.rotation(camera.rotation.y()));
		}
		if (camera.rotation.z() != 0.0F) {
			poseStack.mulPose(Axis.ZP.rotation(-camera.rotation.z()));
		}
	}

	private record BedrockGunGeometry(List<BedrockNode> roots, Map<String, BedrockNode> nodes) {
		static BedrockGunGeometry load(ResourceLocation location) {
			try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
				JsonObject root = GsonHelper.parse(reader);
				JsonObject geometry = GsonHelper.getAsJsonArray(root, "minecraft:geometry").get(0).getAsJsonObject();
				JsonObject description = GsonHelper.getAsJsonObject(geometry, "description");
				int textureWidth = GsonHelper.getAsInt(description, "texture_width");
				int textureHeight = GsonHelper.getAsInt(description, "texture_height");
				JsonArray bones = GsonHelper.getAsJsonArray(geometry, "bones");
				Map<String, BoneData> boneData = new HashMap<>();
				Map<String, BedrockNode> nodes = new HashMap<>();
				List<BedrockNode> roots = new ArrayList<>();

				for (JsonElement element : bones) {
					JsonObject bone = element.getAsJsonObject();
					String name = GsonHelper.getAsString(bone, "name");
					BoneData data = BoneData.read(bone);
					boneData.put(name, data);
					nodes.put(name, new BedrockNode(name));
				}

				for (JsonElement element : bones) {
					JsonObject bone = element.getAsJsonObject();
					String name = GsonHelper.getAsString(bone, "name");
					BoneData data = boneData.get(name);
					BedrockNode node = nodes.get(name);
					node.hiddenMarker = FUNCTIONAL_MARKER_NODES.contains(name);
					node.x = convertPivot(data, boneData, 0);
					node.y = convertPivot(data, boneData, 1);
					node.z = convertPivot(data, boneData, 2);
					if (data.rotation != null) {
						node.xRot = degreesToRadians(data.rotation[0]);
						node.yRot = degreesToRadians(data.rotation[1]);
						node.zRot = degreesToRadians(data.rotation[2]);
					}

					if (data.parent != null) {
						BedrockNode parent = nodes.get(data.parent);
						node.parent = parent;
						parent.children.add(node);
					} else {
						roots.add(node);
					}

					JsonArray cubes = GsonHelper.getAsJsonArray(bone, "cubes", null);
					if (cubes == null) {
						continue;
					}

					for (JsonElement cubeElement : cubes) {
						JsonObject cube = cubeElement.getAsJsonObject();
						float[] size = readFloatArray(cube, "size", 3);
						float inflate = GsonHelper.getAsFloat(cube, "inflate", 0.0F);
						boolean mirror = GsonHelper.getAsBoolean(cube, "mirror", false);
						float x;
						float y;
						float z;
						JsonArray cubeRotation = GsonHelper.getAsJsonArray(cube, "rotation", null);
						if (cubeRotation == null) {
							x = convertOrigin(data, cube, 0);
							y = convertOrigin(data, cube, 1);
							z = convertOrigin(data, cube, 2);
							node.cubes.add(BedrockCube.create(cube, x, y, z, size, inflate, mirror, textureWidth, textureHeight));
						} else {
							BedrockNode cubeNode = new BedrockNode(null);
							cubeNode.x = convertCubePivot(data, cube, 0);
							cubeNode.y = convertCubePivot(data, cube, 1);
							cubeNode.z = convertCubePivot(data, cube, 2);
							float[] rotation = readFloatArray(cube, "rotation", 3);
							cubeNode.xRot = degreesToRadians(rotation[0]);
							cubeNode.yRot = degreesToRadians(rotation[1]);
							cubeNode.zRot = degreesToRadians(rotation[2]);
							x = convertOrigin(cube, 0);
							y = convertOrigin(cube, 1);
							z = convertOrigin(cube, 2);
							cubeNode.cubes.add(BedrockCube.create(cube, x, y, z, size, inflate, mirror, textureWidth, textureHeight));
							cubeNode.parent = node;
							node.children.add(cubeNode);
						}
					}
				}

				return new BedrockGunGeometry(roots, nodes);
			} catch (Exception exception) {
				LOGGER.error("Failed to load TACZ Glock 17 Bedrock geometry {}", location, exception);
				return new BedrockGunGeometry(List.of(), Map.of());
			}
		}

		void applyFirstPersonPositioning(
			PoseStack poseStack,
			float aimProgress,
			Matrix4f scopedAim,
			float refitProgress,
			TaczAttachmentType previousRefitType,
			TaczAttachmentType currentRefitType,
			float refitViewProgress
		) {
			Matrix4f idle = this.positioningMatrix("idle_view");
			Matrix4f aim = scopedAim == null ? this.positioningMatrix("iron_view") : scopedAim;
			Matrix4f matrix = idle;
			if (aim != null && aimProgress > 0.0F) {
				matrix = interpolateMatrix(idle, aim, aimProgress * (1.0F - refitProgress));
			}
			if (refitProgress > 0.0F) {
				Matrix4f previousRefit = this.refitMatrix(previousRefitType);
				Matrix4f currentRefit = this.refitMatrix(currentRefitType);
				Matrix4f refit = interpolateMatrix(previousRefit, currentRefit, refitViewProgress);
				matrix = interpolateMatrix(matrix, refit, refitProgress);
			}

			poseStack.translate(0.0F, 1.5F, 0.0F);
			poseStack.mulPose(matrix);
			poseStack.translate(0.0F, -1.5F, 0.0F);
		}

		void applyAnimationConstraintTransform(PoseStack poseStack, AnimationPose animationPose, float weight) {
			if (weight <= 0.0F) {
				return;
			}

			List<BedrockNode> nodePath = this.pathTo("constraint");
			if (nodePath == null || nodePath.isEmpty()) {
				return;
			}

			ConstraintTransform constraintTransform = this.constraintTransform(nodePath, animationPose);
			NodePose constraintPose = animationPose.node("constraint");
			Vector3f translationConstraint = new Vector3f(constraintPose.position).mul(16.0F);
			Vector3f rotationConstraint = new Vector3f(
				positiveDegrees(constraintPose.rotation.x()),
				positiveDegrees(constraintPose.rotation.y()),
				positiveDegrees(constraintPose.rotation.z())
			);

			Vector3f inverseTranslation = new Vector3f(constraintTransform.originTranslation);
			inverseTranslation.sub(constraintTransform.animatedTranslation);
			inverseTranslation.mulDirection(poseStack.last().pose());
			inverseTranslation.mul(
				translationConstraint.x() - 1.0F,
				translationConstraint.y() - 1.0F,
				1.0F - translationConstraint.z()
			);

			Vector3f inverseRotation = new Vector3f(constraintTransform.rotation);
			inverseRotation.mul(
				rotationConstraint.x() - 1.0F,
				rotationConstraint.y() - 1.0F,
				rotationConstraint.z() - 1.0F
			);

			Vector3f animatedTranslation = constraintTransform.animatedTranslation;
			poseStack.translate(animatedTranslation.x(), animatedTranslation.y() + 1.5F, animatedTranslation.z());
			poseStack.mulPose(Axis.XP.rotation(inverseRotation.x() * weight));
			poseStack.mulPose(Axis.YP.rotation(inverseRotation.y() * weight));
			poseStack.mulPose(Axis.ZP.rotation(inverseRotation.z() * weight));
			poseStack.translate(-animatedTranslation.x(), -animatedTranslation.y() - 1.5F, -animatedTranslation.z());

			Matrix4f poseMatrix = poseStack.last().pose();
			poseMatrix.m30(poseMatrix.m30() - inverseTranslation.x() * weight);
			poseMatrix.m31(poseMatrix.m31() - inverseTranslation.y() * weight);
			poseMatrix.m32(poseMatrix.m32() + inverseTranslation.z() * weight);
		}

		private ConstraintTransform constraintTransform(List<BedrockNode> nodePath, AnimationPose animationPose) {
			Matrix4f animatedMatrix = new Matrix4f().identity();
			Matrix4f originMatrix = new Matrix4f().identity();
			BedrockNode constraintNode = nodePath.get(nodePath.size() - 1);

			for (BedrockNode part : nodePath) {
				NodePose nodePose = animationPose.node(part.name);
				if (part != constraintNode) {
					animatedMatrix.translate(nodePose.position.x(), -nodePose.position.y(), nodePose.position.z());
				}

				if (part.parent != null) {
					animatedMatrix.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
					originMatrix.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
				} else {
					animatedMatrix.translate(part.x / 16.0F, part.y / 16.0F - 1.5F, part.z / 16.0F);
					originMatrix.translate(part.x / 16.0F, part.y / 16.0F - 1.5F, part.z / 16.0F);
				}

				if (part != constraintNode) {
					animatedMatrix.rotate(Axis.ZP.rotation(nodePose.rotation.z()));
					animatedMatrix.rotate(Axis.YP.rotation(nodePose.rotation.y()));
					animatedMatrix.rotate(Axis.XP.rotation(nodePose.rotation.x()));
				}

				animatedMatrix.rotate(Axis.ZP.rotation(part.zRot));
				animatedMatrix.rotate(Axis.YP.rotation(part.yRot));
				animatedMatrix.rotate(Axis.XP.rotation(part.xRot));
				originMatrix.rotate(Axis.ZP.rotation(part.zRot));
				originMatrix.rotate(Axis.YP.rotation(part.yRot));
				originMatrix.rotate(Axis.XP.rotation(part.xRot));
			}

			Vector3f animatedTranslation = animatedMatrix.getTranslation(new Vector3f());
			Vector3f originTranslation = originMatrix.getTranslation(new Vector3f());
			Vector3f animatedRotation = animatedMatrix.getEulerAnglesZYX(new Vector3f());
			Vector3f originRotation = originMatrix.getEulerAnglesZYX(new Vector3f());
			animatedRotation.sub(originRotation);
			return new ConstraintTransform(originTranslation, animatedTranslation, animatedRotation);
		}

		private Matrix4f refitMatrix(TaczAttachmentType type) {
			if (type != TaczAttachmentType.NONE) {
				String typedView = "refit_" + type.getSerializedName() + "_view";
				if (this.pathTo(typedView) != null) {
					return this.positioningMatrix(typedView);
				}
			}
			return this.positioningMatrix("refit_view");
		}

		private Matrix4f positioningMatrix(String name) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return new Matrix4f().identity();
			}

			Matrix4f matrix = new Matrix4f().identity();
			for (int i = nodePath.size() - 1; i >= 0; i--) {
				BedrockNode part = nodePath.get(i);
				matrix.rotate(Axis.XN.rotation(part.xRot));
				matrix.rotate(Axis.YN.rotation(part.yRot));
				matrix.rotate(Axis.ZN.rotation(part.zRot));
				if (part.parent != null) {
					matrix.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
				} else {
					matrix.translate(-part.x / 16.0F, 1.5F - part.y / 16.0F, -part.z / 16.0F);
				}
			}
			return matrix;
		}

		void applyPositioningNode(String name, PoseStack poseStack, float xScale, float yScale, float zScale) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return;
			}

			poseStack.translate(0.0F, 1.5F, 0.0F);
			for (int i = nodePath.size() - 1; i >= 0; i--) {
				BedrockNode part = nodePath.get(i);
				poseStack.mulPose(Axis.XN.rotation(part.xRot));
				poseStack.mulPose(Axis.YN.rotation(part.yRot));
				poseStack.mulPose(Axis.ZN.rotation(part.zRot));
				if (part.parent != null) {
					poseStack.translate(-part.x * xScale / 16.0F, -part.y * yScale / 16.0F, -part.z * zScale / 16.0F);
				} else {
					poseStack.translate(-part.x * xScale / 16.0F, (1.5F - part.y / 16.0F) * yScale, -part.z * zScale / 16.0F);
				}
			}
			poseStack.translate(0.0F, -1.5F, 0.0F);
		}

		boolean applyAnimatedNodePath(String name, PoseStack poseStack, AnimationPose animationPose) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return false;
			}

			for (BedrockNode node : nodePath) {
				node.translateAndRotate(poseStack, animationPose.node(node.name));
			}
			return true;
		}

		boolean renderNodePath(
			String name,
			PoseStack poseStack,
			ItemDisplayContext itemDisplayContext,
			VertexConsumer consumer,
			int light,
			int overlay,
			AnimationPose animationPose,
			AttachmentRenderData attachmentRenderData
		) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return false;
			}

			poseStack.pushPose();
			for (int index = 0; index < nodePath.size() - 1; index++) {
				BedrockNode node = nodePath.get(index);
				node.translateAndRotate(poseStack, animationPose.node(node.name));
			}

			nodePath.get(nodePath.size() - 1).render(poseStack, itemDisplayContext, consumer, light, overlay, animationPose, attachmentRenderData);
			poseStack.popPose();
			return true;
		}

		Vector3f nodeCenter(String name, PoseStack poseStack, AnimationPose animationPose) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return new Vector3f();
			}

			poseStack.pushPose();
			for (BedrockNode node : nodePath) {
				node.translateAndRotate(poseStack, animationPose.node(node.name));
			}
			Vector3f result = new Vector3f(poseStack.last().pose().m30(), poseStack.last().pose().m31(), poseStack.last().pose().m32());
			poseStack.popPose();
			return result;
		}

		List<String> nodesMatching(String baseName) {
			return this.nodes.keySet()
				.stream()
				.filter(name -> isTaczNumberedNode(name, baseName))
				.sorted(Comparator.comparingInt(TaczGlock17SpecialRenderer::taczNodeSortIndex))
				.toList();
		}

		List<OcularNode> ocularNodes() {
			TreeMap<Integer, OcularNode> ocularNodes = new TreeMap<>();
			for (String name : this.nodes.keySet()) {
				OcularNode.match(name).ifPresent(ocularNode -> ocularNodes.put(ocularNode.index(), ocularNode));
			}
			return List.copyOf(ocularNodes.values());
		}

		List<String> nodesContaining(String firstFragment, String secondFragment) {
			return this.nodes.keySet()
				.stream()
				.filter(name -> name.contains(firstFragment) && name.contains(secondFragment))
				.sorted()
				.toList();
		}

		List<List<String>> divisionNodeGroups() {
			List<List<String>> groups = new ArrayList<>();
			for (String divisionNode : this.nodesMatching("division")) {
				groups.add(List.of(divisionNode));
			}
			return groups;
		}

		boolean hasNode(String name) {
			return this.nodes.containsKey(name);
		}

		private List<BedrockNode> pathTo(String name) {
			BedrockNode node = this.nodes.get(name);
			if (node == null) {
				return null;
			}

			List<BedrockNode> path = new ArrayList<>();
			while (node != null) {
				path.add(0, node);
				node = node.parent;
			}
			return path;
		}
	}

	private static boolean isTaczNumberedNode(String name, String baseName) {
		if (name.equals(baseName)) {
			return true;
		}
		return name.startsWith(baseName + "_") && taczNodeSortIndex(name) > 1;
	}

	private static int taczNodeSortIndex(String name) {
		Matcher matcher = TACZ_NUMBERED_NODE.matcher(name);
		if (!matcher.matches() || matcher.group(2) == null) {
			return 1;
		}
		return Integer.parseInt(matcher.group(2));
	}

	private static float effectiveAimProgress(AnimationPose animationPose) {
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		float controllerAim = TaczGlock17AnimationController.aimProgress(partialTick);
		float keyAim = TaczKeyMappings.AIM.isDown() ? 1.0F : 0.0F;
		return Math.max(Math.max(animationPose.aimProgress, controllerAim), keyAim);
	}

	private static RenderTargetBinding renderTargetBinding(RenderType renderType) {
		RenderTarget renderTarget = renderType.iris$getRenderTarget();
		if (renderTarget == null) {
			renderTarget = Minecraft.getInstance().getMainRenderTarget();
		}
		GpuTextureView colorView = VulkanicAPI.getOutputColorTextureOverride() != null
			? VulkanicAPI.getOutputColorTextureOverride()
			: renderTarget.getColorTextureView();
		GpuTextureView depthView = renderTarget.useDepth
			? (VulkanicAPI.getOutputDepthTextureOverride() != null ? VulkanicAPI.getOutputDepthTextureOverride() : renderTarget.getDepthTextureView())
			: null;
		int framebuffer = VulkanicAPI.resolveFramebufferForTextures(colorView.texture(), depthView == null ? null : depthView.texture());
		return new RenderTargetBinding(framebuffer, depthView != null);
	}

	private static void bindRenderTarget(RenderType renderType) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindFramebuffer(ctx, VulkanicAPI.GL_FRAMEBUFFER, renderTargetBinding(renderType).framebuffer());
	}

	private static void clearStencilForRenderType(RenderType renderType) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		bindRenderTarget(renderType);
		VulkanicAPI.setClearStencil(ctx, 0);
		VulkanicAPI.setStencilWriteMask(ctx, 0xFF);
		VulkanicAPI.clearBuffers(ctx, VulkanicClearBuffer.STENCIL);
	}

	private static void logReticleDebug(String phase, RenderType drawRenderType, RenderType renderTargetRenderType, String nodeName, MeshData meshData, String details) {
		long now = System.nanoTime();
		boolean shaderPack = net.irisshaders.iris.Iris.isPackInUseQuick();
		String key = phase + "|" + shaderPack + "|" + nodeName;
		Long previous = RETICLE_DEBUG_LAST_LOG_NANOS.get(key);
		if (previous != null && now - previous < RETICLE_DEBUG_INTERVAL_NANOS) {
			return;
		}
		RETICLE_DEBUG_LAST_LOG_NANOS.put(key, now);

		try {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			boolean stencilTest = VulkanicAPI.isEnabled(ctx, VulkanicCapability.STENCIL_TEST);
			int stencilFunc = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_STENCIL_FUNC);
			int stencilRef = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_STENCIL_REF);
			int stencilValueMask = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_STENCIL_VALUE_MASK);
			int stencilWriteMask = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_STENCIL_WRITEMASK);
			boolean depthTest = VulkanicAPI.isEnabled(ctx, VulkanicCapability.DEPTH_TEST);
			boolean blend = VulkanicAPI.isEnabled(ctx, VulkanicCapability.BLEND);
			boolean cull = VulkanicAPI.isEnabled(ctx, VulkanicCapability.CULL_FACE);
			int depthFunc = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_DEPTH_FUNC);
			int depthWriteMask = VulkanicAPI.getInteger(ctx, VulkanicAPI.GL_DEPTH_WRITEMASK);
			LOGGER.info(
				"TACZ_RETICLE_DEBUG phase={} shaderPack={} irisHandActive={} irisHandSolid={} irisPhase={} renderedItem={} node={} drawRenderType={} drawPipeline={} drawMode={} vertices={} indices={} targetRenderType={} targetPipeline={} stencilTest={} stencilFunc=0x{} stencilRef={} stencilValueMask=0x{} stencilWriteMask=0x{} depthTest={} depthFunc=0x{} depthWriteMask={} blend={} cull={} outputColorOverride={} outputDepthOverride={} details={}",
				phase,
				shaderPack,
				net.irisshaders.iris.pathways.HandRenderer.INSTANCE.isActive(),
				net.irisshaders.iris.pathways.HandRenderer.INSTANCE.isRenderingSolid(),
				net.irisshaders.iris.layer.GbufferPrograms.getCurrentPhase(),
				net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.getCurrentRenderedItem(),
				nodeName,
				drawRenderType.getName(),
				drawRenderType.pipeline().getLocation(),
				meshData.drawState().mode(),
				meshData.drawState().vertexCount(),
				meshData.drawState().indexCount(),
				renderTargetRenderType.getName(),
				renderTargetRenderType.pipeline().getLocation(),
				stencilTest,
				Integer.toHexString(stencilFunc),
				stencilRef,
				Integer.toHexString(stencilValueMask),
				Integer.toHexString(stencilWriteMask),
				depthTest,
				Integer.toHexString(depthFunc),
				depthWriteMask,
				blend,
				cull,
				VulkanicAPI.getOutputColorTextureOverride() != null,
				VulkanicAPI.getOutputDepthTextureOverride() != null,
				details
			);
		} catch (Exception exception) {
			LOGGER.warn("TACZ_RETICLE_DEBUG phase={} node={} failed to collect render state details={}", phase, nodeName, details, exception);
		}
	}

	private static void drawMeshImmediate(RenderType renderType, MeshData meshData, Runnable beforeDraw) {
		drawMeshImmediate(renderType, meshData, beforeDraw, renderType);
	}

	private static void drawMeshImmediate(RenderType renderType, MeshData meshData, Runnable beforeDraw, RenderType renderTargetRenderType) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java TACZ immediate mesh rendering is unavailable while Rust owns whole-frame presentation");
		}
		ensureImmediatePipelineReady(renderType.pipeline());
		renderType.setupRenderState();
		try {
			GpuBufferSlice dynamicTransforms = VulkanicAPI.getDynamicUniforms()
				.writeTransform(
					VulkanicAPI.getModelViewMatrix(),
					new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
					new Vector3f(),
					VulkanicAPI.getTextureMatrix(),
					VulkanicAPI.getShaderLineWidth()
			);
			GpuBuffer vertexBuffer = renderType.pipeline().getVertexFormat().uploadImmediateVertexBuffer(meshData.vertexBuffer());
			GpuBuffer indexBuffer;
			VertexFormat.IndexType indexType;
			boolean drawIndexed = true;
			if (meshData.indexBuffer() == null && meshData.drawState().mode() == VertexFormat.Mode.TRIANGLE_FAN) {
				indexBuffer = null;
				indexType = null;
				drawIndexed = false;
			} else if (meshData.indexBuffer() == null) {
				VulkanicAPI.AutoStorageIndexBuffer sequentialBuffer = VulkanicAPI.getSequentialBuffer(meshData.drawState().mode());
				indexBuffer = sequentialBuffer.getBuffer(meshData.drawState().indexCount());
				indexType = sequentialBuffer.type();
			} else {
				indexBuffer = renderType.pipeline().getVertexFormat().uploadImmediateIndexBuffer(meshData.indexBuffer());
				indexType = meshData.drawState().indexType();
			}

			RenderTargetBinding renderTargetBinding = renderTargetBinding(renderTargetRenderType);

			try (RenderPass renderPass = VulkanicAPI.createRenderPass(
				() -> "TACZ immediate draw for " + renderType.getName(),
				renderTargetBinding.framebuffer(),
				renderTargetBinding.hasDepth()
			)) {
				renderPass.setPipeline(renderType.pipeline());
				ScissorState scissorState = VulkanicAPI.getScissorStateForRenderTypeDraws();
				if (scissorState.enabled()) {
					renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
				}

				VulkanicAPI.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", dynamicTransforms);
				renderPass.setVertexBuffer(0, vertexBuffer);
				for (int sampler = 0; sampler < 12; sampler++) {
					GpuTextureView textureView = net.irisshaders.iris.pbr.TextureTracker.INSTANCE.getShaderTexture(sampler);
					int textureId = net.irisshaders.iris.gl.IrisRenderSystem.getTextureBinding(sampler);
					if (textureView != null && textureId > 0 && net.vulkanic.VulkanicCoreAPI.textureId(textureView) != textureId) {
						textureView = null;
					}
					if (textureView == null) {
						if (textureId > 0) {
							textureView = net.irisshaders.iris.pbr.TextureTracker.INSTANCE.getTextureView(textureId);
						}
						if (textureView == null && sampler == 2) {
							textureView = Minecraft.getInstance().gameRenderer.lightTexture().getTextureView();
						}
					}
					if (textureView != null) {
						renderPass.bindSampler("Sampler" + sampler, textureView);
					}
				}
				if (drawIndexed) {
					renderPass.setIndexBuffer(indexBuffer, indexType);
				}
				beforeDraw.run();
				if (drawIndexed) {
					renderPass.drawIndexed(0, 0, meshData.drawState().indexCount(), 1);
				} else {
					renderPass.draw(0, meshData.drawState().vertexCount());
				}
			}
		} finally {
			renderType.clearRenderState();
		}
	}

	private static void ensureImmediatePipelineReady(RenderPipeline pipeline) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			return;
		}
		if (VulkanicAPI.isVulkanBackendSelected()) {
			VulkanicAPI.precompileRenderPipeline(pipeline, Minecraft.getInstance().getShaderManager()::getShader);
		}
	}

	private record RenderTargetBinding(int framebuffer, boolean hasDepth) {
	}

	private record ScopedAttachment(String marker, AttachmentRenderData attachmentData) {
	}

	private record TaczScopedGunRenderer(
		BedrockGunGeometry gunGeometry,
		ItemDisplayContext itemDisplayContext,
		int light,
		int overlay,
		AnimationPose animationPose,
		GunRenderContext gunRenderContext,
		ScopedAttachment scopedAttachment
	) implements SubmitNodeCollector.ImmediateCustomGeometryRenderer {
		@Override
		public void render(PoseStack.Pose pose, RenderType gunRenderType, MultiBufferSource.BufferSource bufferSource) {
			RenderType attachmentRenderType = RenderType.entityCutout(this.scopedAttachment.attachmentData().texture());
			try {
				new TaczScopedAttachmentRenderer(
					this.gunGeometry,
					this.scopedAttachment.marker(),
					this.itemDisplayContext,
					this.light,
					this.overlay,
					this.animationPose,
					this.scopedAttachment.attachmentData(),
					gunRenderType
				).render(pose, attachmentRenderType, bufferSource);
				this.enableGunBodyStencil();
				this.renderGunBody(pose, gunRenderType, bufferSource);
			} finally {
				this.disableGunBodyStencil(gunRenderType);
			}
		}

		private void renderGunBody(PoseStack.Pose pose, RenderType gunRenderType, MultiBufferSource.BufferSource bufferSource) {
			PoseStack modelPoseStack = new PoseStack();
			modelPoseStack.last().set(pose);
			try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(gunRenderType.bufferSize())) {
				BufferBuilder builder = new BufferBuilder(byteBufferBuilder, gunRenderType.mode(), gunRenderType.format());
				for (BedrockNode root : this.gunGeometry.roots()) {
					root.render(modelPoseStack, this.itemDisplayContext, builder, this.light, this.overlay, this.animationPose, null, this.gunRenderContext);
				}
				MeshData meshData = builder.build();
				if (meshData == null) {
					return;
				}
				try {
					drawMeshImmediate(gunRenderType, meshData, this::configureGunBodyStencil);
				} finally {
					meshData.close();
				}
			}
		}

		private void enableGunBodyStencil() {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.STENCIL_TEST, true);
			this.configureGunBodyStencil();
		}

		private void configureGunBodyStencil() {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setColorMask(ctx, true, true, true, true);
			VulkanicAPI.setDepthWriteMask(ctx, true);
			VulkanicAPI.setStencilWriteMask(ctx, 0x00);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
			if (this.scopedAttachment.attachmentData().scope() && this.scopedAttachment.attachmentData().sight()) {
				VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_GREATER, 127, 0xFF);
			} else {
				VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_EQUAL, 0, 0xFF);
			}
		}

		private void disableGunBodyStencil(RenderType gunRenderType) {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_ALWAYS, 0, 0xFF);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
			VulkanicAPI.setStencilWriteMask(ctx, 0xFF);
			VulkanicAPI.setColorMask(ctx, true, true, true, true);
			VulkanicAPI.setDepthWriteMask(ctx, true);
			clearStencilForRenderType(gunRenderType);
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.STENCIL_TEST, false);
		}
	}

	private record TaczScopedAttachmentRenderer(
		BedrockGunGeometry gunGeometry,
		String marker,
		ItemDisplayContext itemDisplayContext,
		int light,
		int overlay,
		AnimationPose animationPose,
		AttachmentRenderData attachmentData,
		RenderType renderTargetRenderType
	) implements SubmitNodeCollector.ImmediateCustomGeometryRenderer {
		@Override
		public void render(PoseStack.Pose pose, RenderType renderType, MultiBufferSource.BufferSource bufferSource) {
			PoseStack attachmentPoseStack = new PoseStack();
			attachmentPoseStack.last().set(pose);
			if (!this.gunGeometry.applyAnimatedNodePath(this.marker, attachmentPoseStack, this.animationPose)) {
				return;
			}

			attachmentPoseStack.translate(0.0F, -1.5F, 0.0F);
			RenderType outputRenderType = this.renderTargetRenderType == null ? renderType : this.renderTargetRenderType;
			this.renderTaczScopePasses(attachmentPoseStack, renderType, outputRenderType, bufferSource);
		}

		private void renderTaczScopePasses(
			PoseStack poseStack,
			RenderType renderType,
			RenderType renderTargetRenderType,
			MultiBufferSource.BufferSource bufferSource
		) {
			boolean stencilEnabled = false;
			try {
				this.enableStencil(renderTargetRenderType);
				stencilEnabled = true;
				if (this.attachmentData.scope() && this.attachmentData.sight()) {
					this.renderBoth(poseStack, renderType, renderTargetRenderType, bufferSource);
				} else if (this.attachmentData.scope()) {
					this.renderScope(poseStack, renderType, renderTargetRenderType, bufferSource);
				} else if (this.attachmentData.sight()) {
					this.renderSight(poseStack, renderType, renderTargetRenderType, bufferSource);
					stencilEnabled = false;
				}
			} finally {
				if (stencilEnabled) {
					this.disableStencil();
				}
			}

			this.renderNormalAttachment(poseStack, renderType, renderTargetRenderType, bufferSource);
		}

		private void renderBoth(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			this.renderOcularRing(poseStack, renderType, renderTargetRenderType, bufferSource);
			this.renderOcularStencil(poseStack, renderType, renderTargetRenderType, bufferSource, true);
			this.renderScopeBody(poseStack, renderType, renderTargetRenderType, bufferSource);
			this.renderOcularStencil(poseStack, renderType, renderTargetRenderType, bufferSource, false);
			this.renderOcularAndDivision(poseStack, renderType, renderTargetRenderType, bufferSource, true);
		}

		private void renderScope(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			this.renderOcularRing(poseStack, renderType, renderTargetRenderType, bufferSource);
			this.renderOcularStencil(poseStack, renderType, renderTargetRenderType, bufferSource, false);
			this.renderScopeBody(poseStack, renderType, renderTargetRenderType, bufferSource);
			this.renderOcularAndDivision(poseStack, renderType, renderTargetRenderType, bufferSource, false);
		}

		private void renderSight(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			this.renderOcularStencil(poseStack, renderType, renderTargetRenderType, bufferSource, false);
			this.renderDivisionOnly(poseStack, renderType, renderTargetRenderType, bufferSource);
			this.disableStencil();
			this.renderScopeBodyUnstenciled(poseStack, renderType, renderTargetRenderType, bufferSource);
		}

		private void renderOcularRing(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			this.renderNodeIfPresent(
				"ocular_ring",
				poseStack,
				renderType,
				renderTargetRenderType,
				bufferSource,
				null,
				this::configureVisibleStencilWrite
			);
		}

		private void renderNormalAttachment(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(renderType.bufferSize())) {
				BufferBuilder builder = new BufferBuilder(byteBufferBuilder, renderType.mode(), renderType.format());
				for (BedrockNode root : this.attachmentData.geometry().roots()) {
					root.render(poseStack, this.itemDisplayContext, builder, this.light, this.overlay, this.animationPose, this.attachmentData);
				}
				MeshData meshData = builder.build();
				if (meshData == null) {
					return;
				}
				try {
					drawMeshImmediate(renderType, meshData, () -> {
					}, renderTargetRenderType);
				} finally {
					meshData.close();
				}
			}
		}

		private RenderType stencilRenderType() {
			return TACZ_ENTITY_CUTOUT_STENCIL.apply(this.attachmentData.texture());
		}

		private RenderType noDepthRenderType() {
			return TACZ_ENTITY_CUTOUT_NO_DEPTH.apply(this.attachmentData.texture());
		}

		private void renderOcularStencil(
			PoseStack poseStack,
			RenderType renderType,
			RenderType renderTargetRenderType,
			MultiBufferSource.BufferSource bufferSource,
			boolean scopeOcular
		) {
			List<OcularNode> ocularNodes = this.attachmentData.ocularNodes();
			if (ocularNodes.isEmpty()) {
				return;
			}

			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setColorMask(ctx, false, false, false, false);
			VulkanicAPI.setDepthWriteMask(ctx, false);
			VulkanicAPI.setStencilWriteMask(ctx, 0xFF);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_REPLACE);
			for (int index = ocularNodes.size() - 1; index >= 0; index--) {
				OcularNode ocularNode = ocularNodes.get(index);
				if (scopeOcular != ocularNode.scope()) {
					continue;
				}
				int stencilValue = index + 1;
				this.renderNodeIfPresent(ocularNode.name(), poseStack, this.stencilRenderType(), renderTargetRenderType, bufferSource, null, () -> {
					this.configureHiddenStencilWrite();
					VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_GREATER, stencilValue, 0xFF);
				});
			}
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
			VulkanicAPI.setDepthWriteMask(ctx, true);
			VulkanicAPI.setColorMask(ctx, true, true, true, true);
		}

		private void renderScopeBody(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			this.renderNodeIfPresent("scope_body", poseStack, renderType, renderTargetRenderType, bufferSource, this.attachmentData.withSpecialNodesVisible(), () -> {
				CommandContext ctx = VulkanicAPI.getCommandContext();
				VulkanicAPI.setColorMask(ctx, true, true, true, true);
				VulkanicAPI.setDepthWriteMask(ctx, true);
				VulkanicAPI.setStencilWriteMask(ctx, 0x00);
				VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
				VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_EQUAL, 0, 0xFF);
			});
		}

		private void renderScopeBodyUnstenciled(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			this.renderNodeIfPresent("scope_body", poseStack, renderType, renderTargetRenderType, bufferSource, this.attachmentData.withSpecialNodesVisible(), () -> {
			});
		}

		private void renderDivisionOnly(PoseStack poseStack, RenderType renderType, RenderType renderTargetRenderType, MultiBufferSource.BufferSource bufferSource) {
			List<List<String>> divisionNodeGroups = this.attachmentData.geometry().divisionNodeGroups();
			if (divisionNodeGroups.isEmpty()) {
				return;
			}

			CommandContext ctx = VulkanicAPI.getCommandContext();
			RenderType divisionRenderType = this.noDepthRenderType();
			for (int index = 0; index < divisionNodeGroups.size(); index++) {
				int stencilValue = Math.min(index + 1, 0xFF);
				for (String divisionNode : divisionNodeGroups.get(index)) {
					this.renderNodeIfPresent(divisionNode, poseStack, divisionRenderType, renderTargetRenderType, bufferSource, null, () -> {
						VulkanicAPI.setColorMask(ctx, true, true, true, true);
						VulkanicAPI.setDepthWriteMask(ctx, false);
						VulkanicAPI.setStencilWriteMask(ctx, 0x00);
						VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
						VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_EQUAL, stencilValue, 0xFF);
					});
				}
			}
		}

		private void renderOcularAndDivision(
			PoseStack poseStack,
			RenderType renderType,
			RenderType renderTargetRenderType,
			MultiBufferSource.BufferSource bufferSource,
			boolean selective
		) {
			List<OcularNode> ocularNodes = this.attachmentData.ocularNodes();
			if (ocularNodes.isEmpty()) {
				return;
			}

			List<List<String>> divisionNodeGroups = this.attachmentData.geometry().divisionNodeGroups();
			if (divisionNodeGroups.isEmpty()) {
				return;
			}

			this.renderScopeViewStencilAperture(ocularNodes, poseStack, renderTargetRenderType, bufferSource, selective);
			RenderType divisionRenderType = this.noDepthRenderType();
			for (int index = 0; index < ocularNodes.size() && index < divisionNodeGroups.size(); index++) {
				OcularNode ocularNode = ocularNodes.get(index);
				int stencilValue = Math.min(index + 1, 0xFF);
				CommandContext ctx = VulkanicAPI.getCommandContext();
				if (selective && !ocularNode.scope()) {
					for (String divisionNode : divisionNodeGroups.get(index)) {
						this.renderNodeIfPresent(divisionNode, poseStack, divisionRenderType, renderTargetRenderType, bufferSource, null, () -> {
							VulkanicAPI.setColorMask(ctx, true, true, true, true);
							VulkanicAPI.setDepthWriteMask(ctx, false);
							VulkanicAPI.setStencilWriteMask(ctx, 0x00);
							VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
							VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_EQUAL, stencilValue, 0xFF);
						});
					}
				} else {
					this.renderNodeIfPresent(ocularNode.name(), poseStack, renderType, renderTargetRenderType, bufferSource, null, () -> {
						VulkanicAPI.setColorMask(ctx, true, true, true, true);
						VulkanicAPI.setDepthWriteMask(ctx, true);
						VulkanicAPI.setStencilWriteMask(ctx, 0x00);
						VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
						VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_EQUAL, stencilValue, 0xFF);
					});
					int invertedStencilValue = ~stencilValue & 0xFF;
					for (String divisionNode : divisionNodeGroups.get(index)) {
						this.renderNodeIfPresent(divisionNode, poseStack, divisionRenderType, renderTargetRenderType, bufferSource, null, () -> {
							VulkanicAPI.setColorMask(ctx, true, true, true, true);
							VulkanicAPI.setDepthWriteMask(ctx, false);
							VulkanicAPI.setStencilWriteMask(ctx, 0x00);
							VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
							VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_EQUAL, invertedStencilValue, 0xFF);
						});
					}
				}
			}
		}

		private void renderScopeViewStencilAperture(
			List<OcularNode> ocularNodes,
			PoseStack poseStack,
			RenderType renderType,
			MultiBufferSource.BufferSource bufferSource,
			boolean selective
		) {
			if (ocularNodes.isEmpty()) {
				return;
			}

			RenderType fanRenderType = TACZ_DEBUG_TRIANGLE_FAN_STENCIL;
			for (int index = 0; index < ocularNodes.size(); index++) {
				OcularNode ocularNode = ocularNodes.get(index);
				if (selective && !ocularNode.scope()) {
					continue;
				}
				int stencilValue = Math.min(index + 1, 0xFF);
				this.writeScopeViewFan(ocularNode, poseStack, fanRenderType, renderType, bufferSource, stencilValue);
			}

			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setDepthWriteMask(ctx, true);
			VulkanicAPI.setColorMask(ctx, true, true, true, true);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
		}

		private void writeScopeViewFan(
			OcularNode ocularNode,
			PoseStack poseStack,
			RenderType fanRenderType,
			RenderType renderTargetRenderType,
			MultiBufferSource.BufferSource bufferSource,
			int stencilValue
		) {
			Vector3f ocularCenter = this.attachmentData.geometry().nodeCenter(ocularNode.name(), poseStack, this.animationPose);
			float centerX = ocularCenter.x() * 16.0F * 90.0F;
			float centerY = ocularCenter.y() * 16.0F * 90.0F;
			float aimProgress = effectiveAimProgress(this.animationPose);
			float radius = 80.0F * aimProgress;
			try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 90 * 3)) {
				BufferBuilder builder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
				for (int segment = 0; segment < 90; segment++) {
					float angle = segment * ((float)Math.PI * 2.0F) / 90.0F;
					float nextAngle = (segment + 1) * ((float)Math.PI * 2.0F) / 90.0F;
					builder.addVertex(centerX, centerY, -90.0F).setColor(255, 255, 255, 255);
					builder.addVertex(centerX + Mth.cos(angle) * radius, centerY + Mth.sin(angle) * radius, -90.0F).setColor(255, 255, 255, 255);
					builder.addVertex(centerX + Mth.cos(nextAngle) * radius, centerY + Mth.sin(nextAngle) * radius, -90.0F).setColor(255, 255, 255, 255);
				}
				MeshData meshData = builder.buildOrThrow();
				try {
					drawMeshImmediate(fanRenderType, meshData, () -> {
						CommandContext ctx = VulkanicAPI.getCommandContext();
						VulkanicAPI.setStencilWriteMask(ctx, 0xFF);
						VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_INVERT);
						VulkanicAPI.setColorMask(ctx, false, false, false, false);
						VulkanicAPI.setDepthWriteMask(ctx, false);
						VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_EQUAL, stencilValue, 0xFF);
					}, renderTargetRenderType);
				} finally {
					meshData.close();
				}
			}
		}

		private void renderNodeIfPresent(
			String nodeName,
			PoseStack poseStack,
			RenderType renderType,
			MultiBufferSource.BufferSource bufferSource,
			AttachmentRenderData attachmentRenderData,
			Runnable beforeFlush
		) {
			try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(renderType.bufferSize())) {
				BufferBuilder builder = new BufferBuilder(byteBufferBuilder, renderType.mode(), renderType.format());
				if (!this.attachmentData.geometry()
					.renderNodePath(nodeName, poseStack, this.itemDisplayContext, builder, this.light, this.overlay, this.animationPose, attachmentRenderData)) {
					return;
				}
				MeshData meshData = builder.build();
				if (meshData == null) {
					return;
				}
				try {
					drawMeshImmediate(renderType, meshData, beforeFlush);
				} finally {
					meshData.close();
				}
			}
		}

		private void renderNodeIfPresent(
			String nodeName,
			PoseStack poseStack,
			RenderType renderType,
			RenderType renderTargetRenderType,
			MultiBufferSource.BufferSource bufferSource,
			AttachmentRenderData attachmentRenderData,
			Runnable beforeFlush
		) {
			try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(renderType.bufferSize())) {
				BufferBuilder builder = new BufferBuilder(byteBufferBuilder, renderType.mode(), renderType.format());
				if (!this.attachmentData.geometry()
					.renderNodePath(nodeName, poseStack, this.itemDisplayContext, builder, this.light, this.overlay, this.animationPose, attachmentRenderData)) {
					return;
				}
				MeshData meshData = builder.build();
				if (meshData == null) {
					return;
				}
				try {
					drawMeshImmediate(renderType, meshData, () -> {
						beforeFlush.run();
						this.logReticleNodeIfNeeded(nodeName, renderType, renderTargetRenderType, meshData, attachmentRenderData);
					}, renderTargetRenderType);
				} finally {
					meshData.close();
				}
			}
		}

		private void logReticleNodeIfNeeded(
			String nodeName,
			RenderType drawRenderType,
			RenderType renderTargetRenderType,
			MeshData meshData,
			AttachmentRenderData attachmentRenderData
		) {
			if (!isReticleDebugNode(nodeName)) {
				return;
			}
			float aimProgress = effectiveAimProgress(this.animationPose);
			String phase = nodeName.startsWith("division") ? "reticle-division-draw" : "reticle-ocular-draw";
			logReticleDebug(
				phase,
				drawRenderType,
				renderTargetRenderType,
				nodeName,
				meshData,
				"texture=" + this.attachmentData.texture()
					+ " attachmentScope=" + this.attachmentData.scope()
					+ " attachmentSight=" + this.attachmentData.sight()
					+ " specialNodesVisible=" + (attachmentRenderData != null)
					+ " aimProgress=" + aimProgress
					+ " itemDisplayContext=" + this.itemDisplayContext
			);
		}

		private static boolean isReticleDebugNode(String nodeName) {
			return nodeName.startsWith("ocular") || nodeName.startsWith("division") || isTaczNumberedNode(nodeName, "division");
		}

		private void configureHiddenStencilWrite() {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setColorMask(ctx, false, false, false, false);
			VulkanicAPI.setDepthWriteMask(ctx, false);
			VulkanicAPI.setStencilWriteMask(ctx, 0xFF);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_REPLACE);
		}

		private void configureVisibleStencilWrite() {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setColorMask(ctx, true, true, true, true);
			VulkanicAPI.setDepthWriteMask(ctx, true);
			VulkanicAPI.setStencilWriteMask(ctx, 0x00);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
			VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_ALWAYS, 0, 0xFF);
		}

		private void enableStencil(RenderType renderType) {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.STENCIL_TEST, true);
			clearStencilForRenderType(renderType);
			VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_ALWAYS, 0, 0xFF);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
		}

		private void disableStencil() {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setStencilFunc(ctx, VulkanicAPI.GL_ALWAYS, 0, 0xFF);
			VulkanicAPI.setStencilOp(ctx, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_KEEP);
			VulkanicAPI.setStencilWriteMask(ctx, 0xFF);
			VulkanicAPI.setColorMask(ctx, true, true, true, true);
			VulkanicAPI.setDepthWriteMask(ctx, true);
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.DEPTH_TEST, true);
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.STENCIL_TEST, false);
		}
	}

	private record GunRenderContext(
		Map<TaczAttachmentType, AttachmentState> attachments,
		int extendedMagLevel,
		boolean scopeInstalled,
		boolean renderMount,
		Set<String> adapterNodes
	) {
		private static final GunRenderContext EMPTY = new GunRenderContext(Map.of(), 0, false, true, Set.of());

		private static GunRenderContext from(ItemStack gunStack) {
			if (gunStack.isEmpty()) {
				return EMPTY;
			}

			Map<TaczAttachmentType, AttachmentState> attachments = new EnumMap<>(TaczAttachmentType.class);
			Set<String> adapterNodes = ConcurrentHashMap.newKeySet();
			int extendedMagLevel = 0;
			boolean scopeInstalled = false;
			boolean renderMount = true;

			for (TaczAttachmentType type : TaczAttachmentType.values()) {
				if (type == TaczAttachmentType.NONE || type == TaczAttachmentType.AMMO_MOD) {
					continue;
				}

				ItemStack attachmentStack = TaczRefitGun.getStoredAttachment(gunStack, type);
				if (!(attachmentStack.getItem() instanceof TaczAttachmentItem attachment)) {
					continue;
				}

				TaczScopeData.AttachmentDisplay display = TaczScopeData.display(attachment.getAttachmentId());
				attachments.put(type, new AttachmentState(display, attachment.getAttachmentLevel()));
				if (type == TaczAttachmentType.EXTENDED_MAG) {
					extendedMagLevel = attachment.getAttachmentLevel();
				}
				if (type == TaczAttachmentType.SCOPE) {
					scopeInstalled = true;
					renderMount = display == null || display.showMount();
				}
				if (display != null && !display.adapter().isEmpty()) {
					adapterNodes.add(display.adapter());
				}
			}

			return new GunRenderContext(Map.copyOf(attachments), extendedMagLevel, scopeInstalled, renderMount, Set.copyOf(adapterNodes));
		}

		private boolean visible(BedrockNode node) {
			if (node.name == null) {
				return true;
			}
			if (node.parent != null && "attachment_adapter".equals(node.parent.name)) {
				return this.adapterNodes.contains(node.name);
			}
			return switch (node.name) {
				case "carry", "sight" -> !this.scopeInstalled;
				case "sight_folded" -> this.scopeInstalled;
				case "mount" -> this.scopeInstalled && this.renderMount;
				case "mag_standard" -> this.extendedMagLevel == 0;
				case "mag_extended_1" -> this.extendedMagLevel == 1;
				case "mag_extended_2" -> this.extendedMagLevel == 2;
				case "mag_extended_3" -> this.extendedMagLevel == 3;
				case "handguard_default" -> !this.has(TaczAttachmentType.LASER) && !this.has(TaczAttachmentType.GRIP);
				case "handguard_tactical" -> this.has(TaczAttachmentType.LASER) || this.has(TaczAttachmentType.GRIP);
				case "muzzle_default" -> this.defaultAttachmentVisible(TaczAttachmentType.MUZZLE, true);
				case "stock_default" -> this.defaultAttachmentVisible(TaczAttachmentType.STOCK, false);
				case "grip_default" -> this.defaultAttachmentVisible(TaczAttachmentType.GRIP, false);
				case "laser_default" -> this.defaultAttachmentVisible(TaczAttachmentType.LASER, false);
				default -> true;
			};
		}

		private boolean has(TaczAttachmentType type) {
			return this.attachments.containsKey(type);
		}

		private boolean defaultAttachmentVisible(TaczAttachmentType type, boolean canForceVisible) {
			AttachmentState attachment = this.attachments.get(type);
			if (attachment == null) {
				return true;
			}
			return canForceVisible && attachment.display() != null && attachment.display().showMuzzle();
		}
	}

	private record AttachmentState(TaczScopeData.AttachmentDisplay display, int level) {
	}

	private static final class BedrockNode {
		private final String name;
		private final List<BedrockCube> cubes = new ArrayList<>();
		private final List<BedrockNode> children = new ArrayList<>();
		private BedrockNode parent;
		private float x;
		private float y;
		private float z;
		private float xRot;
		private float yRot;
		private float zRot;
		private boolean hiddenMarker;

		private BedrockNode(String name) {
			this.name = name;
		}

		private void render(
			PoseStack poseStack,
			ItemDisplayContext itemDisplayContext,
			VertexConsumer consumer,
			int light,
			int overlay,
			AnimationPose animationPose
		) {
			this.render(poseStack, itemDisplayContext, consumer, light, overlay, animationPose, null, GunRenderContext.EMPTY);
		}

		private void render(
			PoseStack poseStack,
			ItemDisplayContext itemDisplayContext,
			VertexConsumer consumer,
			int light,
			int overlay,
			AnimationPose animationPose,
			AttachmentRenderData attachmentRenderData
		) {
			this.render(poseStack, itemDisplayContext, consumer, light, overlay, animationPose, attachmentRenderData, GunRenderContext.EMPTY);
		}

		private void render(
			PoseStack poseStack,
			ItemDisplayContext itemDisplayContext,
			VertexConsumer consumer,
			int light,
			int overlay,
			AnimationPose animationPose,
			AttachmentRenderData attachmentRenderData,
			GunRenderContext gunRenderContext
		) {
			if (this.cubes.isEmpty() && this.children.isEmpty()) {
				return;
			}

			poseStack.pushPose();
			this.translateAndRotate(poseStack, animationPose.node(this.name));
			if (this.hiddenByScopedFirstPerson(itemDisplayContext, attachmentRenderData, animationPose) || !gunRenderContext.visible(this)) {
				poseStack.popPose();
				return;
			}

			int cubeLight = this.name != null && this.name.endsWith("_illuminated") ? LightTexture.pack(15, 15) : light;
			NodePose nodePose = animationPose.node(this.name);
			if (!this.hiddenMarker && nodePose.visible()) {
				for (BedrockCube cube : this.cubes) {
					cube.compile(poseStack.last(), consumer, cubeLight, overlay);
				}
			}

			for (BedrockNode child : this.children) {
				child.render(poseStack, itemDisplayContext, consumer, cubeLight, overlay, animationPose, attachmentRenderData, gunRenderContext);
			}
			poseStack.popPose();
		}

		private boolean hiddenByScopedFirstPerson(ItemDisplayContext itemDisplayContext, AttachmentRenderData attachmentRenderData, AnimationPose animationPose) {
			return attachmentRenderData != null
				&& itemDisplayContext.firstPerson()
				&& attachmentRenderData.hidesFirstPersonNode(this.name, effectiveAimProgress(animationPose));
		}

		private void translateAndRotate(PoseStack poseStack, NodePose nodePose) {
			poseStack.translate(nodePose.position.x(), -nodePose.position.y(), nodePose.position.z());
			poseStack.translate(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
			float zRotation = this.zRot + nodePose.rotation.z();
			float yRotation = this.yRot + nodePose.rotation.y();
			float xRotation = this.xRot + nodePose.rotation.x();
			if (zRotation != 0.0F) {
				poseStack.mulPose(Axis.ZP.rotation(zRotation));
			}
			if (yRotation != 0.0F) {
				poseStack.mulPose(Axis.YP.rotation(yRotation));
			}
			if (xRotation != 0.0F) {
				poseStack.mulPose(Axis.XP.rotation(xRotation));
			}
			poseStack.scale(nodePose.scale.x(), nodePose.scale.y(), nodePose.scale.z());
		}
	}

	private static final class BedrockAnimationSet {
		private final Map<String, BedrockAnimation> animations;

		private BedrockAnimationSet(Map<String, BedrockAnimation> animations) {
			this.animations = animations;
		}

		static BedrockAnimationSet load(ResourceLocation location) {
			try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
				JsonObject root = GsonHelper.parse(reader);
				JsonObject animationsObject = GsonHelper.getAsJsonObject(root, "animations");
				Map<String, BedrockAnimation> animations = new HashMap<>();
				for (Map.Entry<String, JsonElement> entry : animationsObject.entrySet()) {
					animations.put(entry.getKey(), BedrockAnimation.read(entry.getValue().getAsJsonObject()));
				}
				return new BedrockAnimationSet(animations);
			} catch (Exception exception) {
				LOGGER.error("Failed to load TACZ Glock 17 animation {}", location, exception);
				return new BedrockAnimationSet(Map.of());
			}
		}

		AnimationPose sample(TaczGlock17AnimationController.Snapshot snapshot) {
			AnimationPose pose = new AnimationPose(snapshot.aimProgress());
			for (TaczGlock17AnimationController.ActiveAnimation layer : snapshot.animations()) {
				BedrockAnimation animation = this.animations.get(layer.name());
				if (animation == null) {
					continue;
				}

				float time = layer.startNanos() == 0L ? animation.length : layer.ageSeconds();
				if (layer.startNanos() != 0L && time > animation.length && !animation.holdOnLastFrame) {
					continue;
				}

				animation.apply(pose, Math.min(time, animation.length), layer.additive());
			}
			return pose;
		}
	}

	private static final class BedrockAnimation {
		private final float length;
		private final boolean holdOnLastFrame;
		private final Map<String, NodeAnimation> nodes;

		private BedrockAnimation(float length, boolean holdOnLastFrame, Map<String, NodeAnimation> nodes) {
			this.length = length;
			this.holdOnLastFrame = holdOnLastFrame;
			this.nodes = nodes;
		}

		static BedrockAnimation read(JsonObject object) {
			float length = GsonHelper.getAsFloat(object, "animation_length", 0.0F);
			boolean holdOnLastFrame = false;
			if (object.has("loop")) {
				JsonElement loop = object.get("loop");
				holdOnLastFrame = loop.isJsonPrimitive() && "hold_on_last_frame".equals(loop.getAsString());
			}

			Map<String, NodeAnimation> nodes = new HashMap<>();
			JsonObject bones = GsonHelper.getAsJsonObject(object, "bones", new JsonObject());
			for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
				nodes.put(entry.getKey(), NodeAnimation.read(entry.getValue().getAsJsonObject()));
			}
			return new BedrockAnimation(length, holdOnLastFrame, nodes);
		}

		void apply(AnimationPose pose, float time, boolean additive) {
			for (Map.Entry<String, NodeAnimation> entry : this.nodes.entrySet()) {
				entry.getValue().apply(pose.node(entry.getKey()), time, additive);
			}
		}
	}

	private static final class NodeAnimation {
		private final Channel position;
		private final Channel rotation;
		private final Channel scale;

		private NodeAnimation(Channel position, Channel rotation, Channel scale) {
			this.position = position;
			this.rotation = rotation;
			this.scale = scale;
		}

		static NodeAnimation read(JsonObject object) {
			return new NodeAnimation(
				Channel.read(object.get("position"), ChannelKind.POSITION),
				Channel.read(object.get("rotation"), ChannelKind.ROTATION),
				Channel.read(object.get("scale"), ChannelKind.SCALE)
			);
		}

		void apply(NodePose pose, float time, boolean additive) {
			if (this.position != null) {
				Vector3f value = this.position.sample(time);
				if (additive) {
					pose.position.add(value);
				} else {
					pose.position.set(value);
				}
			}
			if (this.rotation != null) {
				Vector3f value = this.rotation.sample(time);
				if (additive) {
					pose.rotation.add(value);
				} else {
					pose.rotation.set(value);
				}
			}
			if (this.scale != null) {
				Vector3f value = this.scale.sample(time);
				if (additive) {
					pose.scale.mul(value);
				} else {
					pose.scale.set(value);
				}
			}
		}
	}

	private static final class Channel {
		private final List<Keyframe> keyframes;
		private final ChannelKind kind;

		private Channel(List<Keyframe> keyframes, ChannelKind kind) {
			this.keyframes = keyframes;
			this.kind = kind;
		}

		static Channel read(JsonElement element, ChannelKind kind) {
			if (element == null || element.isJsonNull()) {
				return null;
			}

			if (element.isJsonArray() || element.isJsonPrimitive()) {
				return new Channel(List.of(Keyframe.single(0.0F, convertAnimationValue(readVector(element), kind))), kind);
			}

			JsonObject object = element.getAsJsonObject();
			List<Keyframe> keyframes = new ArrayList<>();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				keyframes.add(Keyframe.read(Float.parseFloat(entry.getKey()), entry.getValue(), kind));
			}
			keyframes.sort(Comparator.comparing(Keyframe::time));
			return new Channel(keyframes, kind);
		}

		Vector3f sample(float time) {
			if (this.keyframes.isEmpty()) {
				return new Vector3f();
			}
			if (this.keyframes.size() == 1 || time <= this.keyframes.get(0).time) {
				return this.keyframes.get(0).value(false);
			}

			Keyframe last = this.keyframes.get(this.keyframes.size() - 1);
			if (time >= last.time) {
				return last.value(true);
			}

			for (int i = 0; i < this.keyframes.size() - 1; i++) {
				Keyframe from = this.keyframes.get(i);
				Keyframe to = this.keyframes.get(i + 1);
				if (time >= from.time && time <= to.time) {
					float alpha = (time - from.time) / (to.time - from.time);
					if (from.lerpMode == LerpMode.CATMULLROM || to.lerpMode == LerpMode.CATMULLROM) {
						return this.catmullrom(i, i + 1, alpha);
					}
					return from.value(true).lerp(to.value(false), alpha);
				}
			}
			return last.value(true);
		}

		private Vector3f catmullrom(int indexFrom, int indexTo, float alpha) {
			int previous = indexFrom == 0 ? 0 : indexFrom - 1;
			int next = indexTo == this.keyframes.size() - 1 ? this.keyframes.size() - 1 : indexTo + 1;
			Vector3f valuePrevious = this.keyframes.get(previous).value(true);
			Vector3f valueFrom = this.keyframes.get(indexFrom).value(false);
			Vector3f valueTo = this.keyframes.get(indexTo).value(false);
			Vector3f valueNext = this.keyframes.get(next).value(false);
			return new Vector3f(
				splineCurve(valuePrevious.x(), valueFrom.x(), valueTo.x(), valueNext.x(), alpha),
				splineCurve(valuePrevious.y(), valueFrom.y(), valueTo.y(), valueNext.y(), alpha),
				splineCurve(valuePrevious.z(), valueFrom.z(), valueTo.z(), valueNext.z(), alpha)
			);
		}
	}

	private record Keyframe(float time, Vector3f pre, Vector3f post, LerpMode lerpMode) {
		private static Keyframe single(float time, Vector3f value) {
			return new Keyframe(time, value, value, LerpMode.LINEAR);
		}

		private static Keyframe read(float time, JsonElement element, ChannelKind kind) {
			if (!element.isJsonObject()) {
				return single(time, convertAnimationValue(readVector(element), kind));
			}

			JsonObject object = element.getAsJsonObject();
			Vector3f pre = object.has("pre") ? convertAnimationValue(readVector(object.get("pre")), kind) : null;
			Vector3f post = object.has("post") ? convertAnimationValue(readVector(object.get("post")), kind) : null;
			if (pre == null && post == null) {
				pre = convertAnimationValue(readVector(element), kind);
				post = pre;
			} else if (pre == null) {
				pre = new Vector3f(post);
			} else if (post == null) {
				post = new Vector3f(pre);
			}
			return new Keyframe(time, pre, post, readLerpMode(object));
		}

		private Vector3f value(boolean usePost) {
			return new Vector3f(usePost ? this.post : this.pre);
		}
	}

	private enum ChannelKind {
		POSITION,
		ROTATION,
		SCALE
	}

	private enum LerpMode {
		LINEAR,
		CATMULLROM
	}

	private static final class AnimationPose {
		private final Map<String, NodePose> nodes = new HashMap<>();
		private final float aimProgress;

		private AnimationPose(float aimProgress) {
			this.aimProgress = aimProgress;
		}

		private NodePose node(String name) {
			return this.nodes.computeIfAbsent(name == null ? "" : name, key -> new NodePose());
		}
	}

	private static final class NodePose {
		private final Vector3f position = new Vector3f();
		private final Vector3f rotation = new Vector3f();
		private final Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);

		private boolean visible() {
			return this.scale.x() != 0.0F && this.scale.y() != 0.0F && this.scale.z() != 0.0F;
		}
	}

	private record BedrockCube(BedrockPolygon[] polygons) {
		private static BedrockCube create(JsonObject cube, float x, float y, float z, float[] size, float inflate, boolean mirror, int textureWidth, int textureHeight) {
			JsonObject faceUv = GsonHelper.getAsJsonObject(cube, "uv", null);
			return faceUv == null || faceUv.entrySet().isEmpty()
				? box(cube, x, y, z, size, inflate, mirror, textureWidth, textureHeight)
				: perFace(faceUv, x, y, z, size, inflate, textureWidth, textureHeight);
		}

		private static BedrockCube box(JsonObject cube, float x, float y, float z, float[] size, float inflate, boolean mirror, int textureWidth, int textureHeight) {
			float[] uv = readFloatArray(cube, "uv", 2);
			float width = size[0];
			float height = size[1];
			float depth = size[2];
			float xEnd = x + width + inflate;
			float yEnd = y + height + inflate;
			float zEnd = z + depth + inflate;
			x -= inflate;
			y -= inflate;
			z -= inflate;
			if (mirror) {
				float tmp = xEnd;
				xEnd = x;
				x = tmp;
			}

			BedrockVertex v1 = new BedrockVertex(x, y, z);
			BedrockVertex v2 = new BedrockVertex(xEnd, y, z);
			BedrockVertex v3 = new BedrockVertex(xEnd, yEnd, z);
			BedrockVertex v4 = new BedrockVertex(x, yEnd, z);
			BedrockVertex v5 = new BedrockVertex(x, y, zEnd);
			BedrockVertex v6 = new BedrockVertex(xEnd, y, zEnd);
			BedrockVertex v7 = new BedrockVertex(xEnd, yEnd, zEnd);
			BedrockVertex v8 = new BedrockVertex(x, yEnd, zEnd);
			int dx = (int)width;
			int dy = (int)height;
			int dz = (int)depth;
			float p1 = uv[0] + dz;
			float p2 = uv[0] + dz + dx;
			float p3 = uv[0] + dz + dx + dx;
			float p4 = uv[0] + dz + dx + dz;
			float p5 = uv[0] + dz + dx + dz + dx;
			float p6 = uv[1] + dz;
			float p7 = uv[1] + dz + dy;
			float p8 = uv[1];
			float p9 = uv[0];
			BedrockPolygon[] polygons = new BedrockPolygon[6];
			polygons[2] = new BedrockPolygon(new BedrockVertex[]{v6, v5, v1, v2}, p1, p8, p2, p6, textureWidth, textureHeight, mirror, Direction.DOWN);
			polygons[3] = new BedrockPolygon(new BedrockVertex[]{v3, v4, v8, v7}, p2, p6, p3, p8, textureWidth, textureHeight, mirror, Direction.UP);
			polygons[1] = new BedrockPolygon(new BedrockVertex[]{v1, v5, v8, v4}, p9, p6, p1, p7, textureWidth, textureHeight, mirror, Direction.WEST);
			polygons[4] = new BedrockPolygon(new BedrockVertex[]{v2, v1, v4, v3}, p1, p6, p2, p7, textureWidth, textureHeight, mirror, Direction.NORTH);
			polygons[0] = new BedrockPolygon(new BedrockVertex[]{v6, v2, v3, v7}, p2, p6, p4, p7, textureWidth, textureHeight, mirror, Direction.EAST);
			polygons[5] = new BedrockPolygon(new BedrockVertex[]{v5, v6, v7, v8}, p4, p6, p5, p7, textureWidth, textureHeight, mirror, Direction.SOUTH);
			return new BedrockCube(polygons);
		}

		private static BedrockCube perFace(JsonObject faces, float x, float y, float z, float[] size, float inflate, int textureWidth, int textureHeight) {
			float width = size[0];
			float height = size[1];
			float depth = size[2];
			float xEnd = x + width + inflate;
			float yEnd = y + height + inflate;
			float zEnd = z + depth + inflate;
			x -= inflate;
			y -= inflate;
			z -= inflate;
			BedrockVertex v1 = new BedrockVertex(x, y, z);
			BedrockVertex v2 = new BedrockVertex(xEnd, y, z);
			BedrockVertex v3 = new BedrockVertex(xEnd, yEnd, z);
			BedrockVertex v4 = new BedrockVertex(x, yEnd, z);
			BedrockVertex v5 = new BedrockVertex(x, y, zEnd);
			BedrockVertex v6 = new BedrockVertex(xEnd, y, zEnd);
			BedrockVertex v7 = new BedrockVertex(xEnd, yEnd, zEnd);
			BedrockVertex v8 = new BedrockVertex(x, yEnd, zEnd);
			BedrockPolygon[] polygons = new BedrockPolygon[6];
			polygons[2] = perFacePolygon(faces, "up", new BedrockVertex[]{v6, v5, v1, v2}, textureWidth, textureHeight, Direction.DOWN);
			polygons[3] = perFacePolygon(faces, "down", new BedrockVertex[]{v3, v4, v8, v7}, textureWidth, textureHeight, Direction.UP);
			polygons[1] = perFacePolygon(faces, "east", new BedrockVertex[]{v1, v5, v8, v4}, textureWidth, textureHeight, Direction.WEST);
			polygons[4] = perFacePolygon(faces, "north", new BedrockVertex[]{v2, v1, v4, v3}, textureWidth, textureHeight, Direction.NORTH);
			polygons[0] = perFacePolygon(faces, "west", new BedrockVertex[]{v6, v2, v3, v7}, textureWidth, textureHeight, Direction.EAST);
			polygons[5] = perFacePolygon(faces, "south", new BedrockVertex[]{v5, v6, v7, v8}, textureWidth, textureHeight, Direction.SOUTH);
			return new BedrockCube(polygons);
		}

		private static BedrockPolygon perFacePolygon(
			JsonObject faces, String faceName, BedrockVertex[] vertices, int textureWidth, int textureHeight, Direction direction
		) {
			JsonObject face = GsonHelper.getAsJsonObject(faces, faceName, null);
			if (face == null) {
				return BedrockPolygon.empty(direction);
			}

			float[] uv = readFloatArray(face, "uv", 2);
			float[] uvSize = readFloatArray(face, "uv_size", 2);
			if (uvSize[0] == 0.0F && uvSize[1] == 0.0F) {
				return BedrockPolygon.empty(direction);
			}

			return new BedrockPolygon(vertices, uv[0], uv[1], uv[0] + uvSize[0], uv[1] + uvSize[1], textureWidth, textureHeight, false, direction);
		}

		private void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay) {
			Matrix4f poseMatrix = pose.pose();
			Matrix3f normalMatrix = pose.normal();
			for (BedrockPolygon polygon : this.polygons) {
				if (polygon.empty) {
					continue;
				}

				Vector3f normal = new Vector3f(polygon.normal);
				normal.mul(normalMatrix);
				for (BedrockVertex vertex : polygon.vertices) {
					Vector4f position = new Vector4f(vertex.x / 16.0F, vertex.y / 16.0F, vertex.z / 16.0F, 1.0F);
					position.mul(poseMatrix);
					consumer.addVertex(position.x(), position.y(), position.z())
						.setColor(-1)
						.setUv(vertex.u, vertex.v)
						.setOverlay(overlay)
						.setLight(light)
						.setNormal(normal.x(), normal.y(), normal.z());
				}
			}
		}
	}

	private static final class BedrockPolygon {
		private final BedrockVertex[] vertices;
		private final Vector3f normal;
		private final boolean empty;

		private BedrockPolygon(BedrockVertex[] vertices, float u1, float v1, float u2, float v2, int textureWidth, int textureHeight, boolean mirror, Direction direction) {
			this.vertices = vertices;
			vertices[0] = vertices[0].remap(u2 / textureWidth, v1 / textureHeight);
			vertices[1] = vertices[1].remap(u1 / textureWidth, v1 / textureHeight);
			vertices[2] = vertices[2].remap(u1 / textureWidth, v2 / textureHeight);
			vertices[3] = vertices[3].remap(u2 / textureWidth, v2 / textureHeight);
			if (mirror) {
				for (int i = 0; i < vertices.length / 2; i++) {
					BedrockVertex vertex = vertices[i];
					vertices[i] = vertices[vertices.length - 1 - i];
					vertices[vertices.length - 1 - i] = vertex;
				}
			}

			this.normal = direction.step();
			if (mirror) {
				this.normal.mul(-1.0F, 1.0F, 1.0F);
			}
			this.empty = false;
		}

		private BedrockPolygon(Direction direction) {
			this.vertices = new BedrockVertex[0];
			this.normal = direction.step();
			this.empty = true;
		}

		private static BedrockPolygon empty(Direction direction) {
			return new BedrockPolygon(direction);
		}
	}

	private record BedrockVertex(float x, float y, float z, float u, float v) {
		private BedrockVertex(float x, float y, float z) {
			this(x, y, z, 0.0F, 0.0F);
		}

		private BedrockVertex remap(float u, float v) {
			return new BedrockVertex(this.x, this.y, this.z, u, v);
		}
	}

	private record BoneData(String name, String parent, float[] pivot, float[] rotation) {
		private static BoneData read(JsonObject bone) {
			String name = GsonHelper.getAsString(bone, "name");
			String parent = GsonHelper.getAsString(bone, "parent", null);
			float[] pivot = readFloatArray(bone, "pivot", 3);
			JsonArray rotationArray = GsonHelper.getAsJsonArray(bone, "rotation", null);
			float[] rotation = rotationArray == null ? null : readFloatArray(rotationArray, 3);
			return new BoneData(name, parent, pivot, rotation);
		}
	}

	private static float convertPivot(BoneData bone, Map<String, BoneData> bones, int index) {
		if (bone.parent != null) {
			BoneData parent = bones.get(bone.parent);
			return index == 1 ? parent.pivot[index] - bone.pivot[index] : bone.pivot[index] - parent.pivot[index];
		}

		return index == 1 ? 24.0F - bone.pivot[index] : bone.pivot[index];
	}

	private static float convertCubePivot(BoneData parent, JsonObject cube, int index) {
		float[] pivot = readFloatArray(cube, "pivot", 3);
		return index == 1 ? parent.pivot[index] - pivot[index] : pivot[index] - parent.pivot[index];
	}

	private static float convertOrigin(BoneData bone, JsonObject cube, int index) {
		float[] origin = readFloatArray(cube, "origin", 3);
		float[] size = readFloatArray(cube, "size", 3);
		return index == 1 ? bone.pivot[index] - origin[index] - size[index] : origin[index] - bone.pivot[index];
	}

	private static float convertOrigin(JsonObject cube, int index) {
		float[] pivot = readFloatArray(cube, "pivot", 3);
		float[] origin = readFloatArray(cube, "origin", 3);
		float[] size = readFloatArray(cube, "size", 3);
		return index == 1 ? pivot[index] - origin[index] - size[index] : origin[index] - pivot[index];
	}

	private static float degreesToRadians(float degree) {
		return degree * (float)Math.PI / 180.0F;
	}

	private static float[] readFloatArray(JsonObject object, String key, int length) {
		return readFloatArray(GsonHelper.getAsJsonArray(object, key), length);
	}

	private static float[] readFloatArray(JsonArray array, int length) {
		float[] values = new float[length];
		for (int i = 0; i < length; i++) {
			values[i] = GsonHelper.convertToFloat(array.get(i), "[" + i + "]");
		}
		return values;
	}

	private static Vector3f readVector(JsonElement element) {
		if (element.isJsonPrimitive()) {
			float value = element.getAsFloat();
			return new Vector3f(value, value, value);
		}

		JsonArray array = element.getAsJsonArray();
		float x = array.size() > 0 ? GsonHelper.convertToFloat(array.get(0), "[0]") : 0.0F;
		float y = array.size() > 1 ? GsonHelper.convertToFloat(array.get(1), "[1]") : x;
		float z = array.size() > 2 ? GsonHelper.convertToFloat(array.get(2), "[2]") : x;
		return new Vector3f(x, y, z);
	}

	private static Vector3f convertAnimationValue(Vector3f value, ChannelKind kind) {
		return switch (kind) {
			case POSITION -> value.mul(1.0F / 16.0F);
			case ROTATION -> value.mul((float)Math.PI / 180.0F);
			case SCALE -> value;
		};
	}

	private static LerpMode readLerpMode(JsonObject object) {
		String lerpMode = GsonHelper.getAsString(object, "lerp_mode", "linear");
		return "catmullrom".equals(lerpMode) ? LerpMode.CATMULLROM : LerpMode.LINEAR;
	}

	private static float splineCurve(float y0, float y1, float y2, float y3, float alpha) {
		float v0 = (y2 - y0) * 0.5F;
		float v1 = (y3 - y1) * 0.5F;
		float t2 = alpha * alpha;
		float t3 = alpha * t2;
		float h1 = 2.0F * t3 - 3.0F * t2 + 1.0F;
		float h2 = -2.0F * t3 + 3.0F * t2;
		float h3 = t3 - 2.0F * t2 + alpha;
		float h4 = t3 - t2;
		return h1 * y1 + h2 * y2 + h3 * v0 + h4 * v1;
	}

	private static Matrix4f interpolateMatrix(Matrix4f from, Matrix4f to, float alpha) {
		Vector3f fromTranslation = from.getTranslation(new Vector3f());
		Vector3f toTranslation = to.getTranslation(new Vector3f());
		Vector3f translation = fromTranslation.lerp(toTranslation, alpha);
		Quaternionf rotation = from.getNormalizedRotation(new Quaternionf()).slerp(to.getNormalizedRotation(new Quaternionf()), alpha);
		return new Matrix4f().translationRotate(translation, rotation);
	}

	private static float positiveDegrees(float radians) {
		float degrees = radians * 180.0F / (float)Math.PI;
		return degrees < 0.0F ? degrees + 360.0F : degrees;
	}

	private record ConstraintTransform(Vector3f originTranslation, Vector3f animatedTranslation, Vector3f rotation) {
	}

	private record OcularNode(String name, int index, boolean scope) {
		private static final Pattern PATTERN = Pattern.compile("^(ocular|ocular_sight|ocular_scope)(?:_(\\d+))?$");

		private static java.util.Optional<OcularNode> match(String name) {
			Matcher matcher = PATTERN.matcher(name);
			if (!matcher.matches()) {
				return java.util.Optional.empty();
			}

			int index = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
			return java.util.Optional.of(new OcularNode(name, index, "ocular_scope".equals(matcher.group(1))));
		}
	}

	private record AttachmentRenderData(
		BedrockGunGeometry geometry,
		ResourceLocation texture,
		boolean scope,
		boolean sight,
		List<OcularNode> ocularNodes,
		boolean hideSpecialNodes
	) {
		private AttachmentRenderData withSpecialNodesVisible() {
			return new AttachmentRenderData(this.geometry, this.texture, this.scope, this.sight, this.ocularNodes, false);
		}

		private boolean hidesFirstPersonNode(String nodeName, float aimProgress) {
			if ((!this.scope && !this.sight) || nodeName == null) {
				return false;
			}

			return this.hideSpecialNodes
				&& (nodeName.contains("division")
					|| nodeName.startsWith("ocular")
					|| isTaczNumberedNode(nodeName, "scope_view")
					|| (this.scope || this.sight) && nodeName.equals("scope_body")
					|| this.scope && (nodeName.equals("lens") || nodeName.equals("red_illuminated")));
		}
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(String gun) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<TaczGlock17SpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codec.STRING.optionalFieldOf("gun", "glock_17").forGetter(TaczGlock17SpecialRenderer.Unbaked::gun))
				.apply(instance, TaczGlock17SpecialRenderer.Unbaked::new)
		);

		@Override
		public MapCodec<TaczGlock17SpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new TaczGlock17SpecialRenderer(this.gun);
		}
	}
}
