package net.minecraft.client.renderer;

import net.blaze3d.platform.Lighting;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class ScreenEffectRenderer {
	private static final ResourceLocation UNDERWATER_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");
	private final Minecraft minecraft;
	private final MaterialSet materials;
	private final MultiBufferSource bufferSource;
	public static final int ITEM_ACTIVATION_ANIMATION_LENGTH = 40;
	@Nullable
	private ItemStack itemActivationItem;
	private int itemActivationTicks;
	private float itemActivationOffX;
	private float itemActivationOffY;

	public ScreenEffectRenderer(Minecraft minecraft, MaterialSet materialSet, MultiBufferSource multiBufferSource) {
		this.minecraft = minecraft;
		this.materials = materialSet;
		this.bufferSource = multiBufferSource;
	}

	public void tick() {
		if (this.itemActivationTicks > 0) {
			this.itemActivationTicks--;
			if (this.itemActivationTicks == 0) {
				this.itemActivationItem = null;
			}
		}
	}

	public void renderScreenEffect(boolean bl, float f, SubmitNodeCollector submitNodeCollector) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException("Java screen-effect rendering is unavailable on selected Vulkan");
		}
		PoseStack poseStack = new PoseStack();
		Player player = this.minecraft.player;
		if (this.minecraft.options.getCameraType().isFirstPerson() && !bl) {
			if (!player.noPhysics) {
				BlockState blockState = getViewBlockingState(player);
				if (blockState != null) {
					renderTex(this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(blockState), poseStack, this.bufferSource);
				}
			}

			if (!this.minecraft.player.isSpectator()) {
				if (this.minecraft.player.isEyeInFluid(FluidTags.WATER)) {
					renderWater(this.minecraft, poseStack, this.bufferSource);
				}

				if (this.minecraft.player.isOnFire()) {
					TextureAtlasSprite textureAtlasSprite = this.materials.get(ModelBakery.FIRE_1);
					renderFire(poseStack, this.bufferSource, textureAtlasSprite);
				}
			}
		}

		if (!this.minecraft.options.hideGui) {
			this.renderItemActivationAnimation(poseStack, f, submitNodeCollector);
		}
	}

	/**
	 * Extracts only the item-activation semantic draw for the Rust whole-frame
	 * route. The legacy screen-effect method also writes directly to a
	 * MultiBufferSource for underwater/fire overlays, so it must not be called
	 * from the Rust presenter. Item activation already has an explicit
	 * SubmitNodeCollector contract and can therefore be admitted independently.
	 */
	public void renderRustVulkanItemActivation(float partialTick, SubmitNodeCollector submitNodeCollector) {
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Rust Vulkan item-activation extraction requires an active whole-frame shell");
		}
		if (this.minecraft.options.hideGui) {
			return;
		}
		// The Rust shell does not run FeatureRenderDispatcher after GUI extraction.
		// Scope this semantic producer through the copied indexed-item family so
		// ordinary activation layers become Rust-owned mesh instances immediately.
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.beginItemEntitySubmission();
		try {
			this.renderItemActivationAnimation(new PoseStack(), partialTick, submitNodeCollector);
		} finally {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.endItemEntitySubmission();
		}
	}

	/** Extracts the resource-pack-backed underwater overlay through Rust GUI tiling. */
	public void renderRustVulkanScreenEffects(net.minecraft.client.gui.GuiGraphics guiGraphics, float itemFov) {
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Rust Vulkan screen-effect extraction requires an active whole-frame shell");
		}
		if (guiGraphics == null || this.minecraft.player == null
			|| !this.minecraft.options.getCameraType().isFirstPerson()
			|| this.minecraft.player.isSpectator()) {
			return;
		}
		Player player = this.minecraft.player;
		// Vanilla order: view-blocking block, then water, then fire.
		// Match vanilla's no-physics behavior: spectator/no-clip cameras do not
		// acquire a block-screen overlay even when their eye intersects a solid
		// block.  Keep this decision in the semantic producer so Rust receives
		// the same gameplay-visible overlay eligibility as OpenGL.
		BlockState blockingState = player.noPhysics ? null : getViewBlockingState(player);
		if (blockingState != null) {
			submitRustViewBlockingOverlay(guiGraphics,
				this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(blockingState), itemFov);
		}
		// Iris: the pack may disable the underwater overlay (`underwaterOverlay`).
		if (player.isEyeInFluid(FluidTags.WATER)
			&& net.vulkanic.gui.RustGalFrameCoordinator.copiedShaderPackUnderwaterOverlayEnabled()) {
			BlockPos blockPos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
			float brightness = LightTexture.getBrightness(player.level().dimensionType(), player.level().getMaxLocalRawBrightness(blockPos));
			int color = ARGB.colorFromFloat(0.1F, brightness, brightness, brightness);
			// Preserve vanilla's camera-scrolled four-repeat water pattern as
			// semantic UV data. Rust splits wrapped unit intervals before upload;
			// no Java texture or GPU state is consulted on the Vulkan route.
			float uOffset = -player.getYRot() / 64.0F;
			float vOffset = player.getXRot() / 64.0F;
			int width = guiGraphics.guiWidth();
			int height = guiGraphics.guiHeight();
			// Vanilla spans four texture repeats in each screen axis. Tiled semantic
			// blits preserve that repeat without requiring a Java atlas or GPU view.
			guiGraphics.submitRustSemanticTiledBlit(RenderPipelines.BLOCK_SCREEN_EFFECT,
				UNDERWATER_LOCATION,
				0, 0, width, height,
				Math.max(1, width / 4), Math.max(1, height / 4),
				uOffset, vOffset, uOffset + 4.0F, vOffset + 4.0F, color
			);
		}
		if (player.isOnFire()) {
			submitRustFireOverlay(guiGraphics, this.materials.get(ModelBakery.FIRE_1), itemFov);
		}
	}

	/**
	 * Vanilla draws two fire quads in view space ([-0.5, 0.5]^2 at z = -0.5,
	 * translated by (-/+0.24, -0.3) and yawed -/+10 degrees) under the hand
	 * projection. The GUI path is affine, so each quad is split into vertical
	 * strips whose corners are projected exactly.
	 */
	private static void submitRustFireOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics,
			TextureAtlasSprite sprite, float itemFov) {
		int width = guiGraphics.guiWidth();
		int height = guiGraphics.guiHeight();
		float tanHalf = (float) Math.tan(Math.toRadians(itemFov) * 0.5);
		float aspect = width / (float) height;
		float uMid = (sprite.getU0() + sprite.getU1()) * 0.5F;
		float vMid = (sprite.getV0() + sprite.getV1()) * 0.5F;
		float shrink = sprite.uvShrinkRatio();
		float uA = Mth.lerp(shrink, sprite.getU1(), uMid); // x = -0.5
		float uB = Mth.lerp(shrink, sprite.getU0(), uMid); // x = +0.5
		float vTop = Mth.lerp(shrink, sprite.getV0(), vMid); // y = +0.5
		float vBottom = Mth.lerp(shrink, sprite.getV1(), vMid); // y = -0.5
		int color = ARGB.colorFromFloat(0.9F, 1.0F, 1.0F, 1.0F);
		for (int side = 0; side < 2; side++) {
			float sign = side * 2 - 1;
			float yaw = (float) Math.toRadians(sign * 10.0F);
			float cos = Mth.cos(yaw);
			float sin = Mth.sin(yaw);
			float offsetX = -sign * 0.24F;
			for (int strip = 0; strip < FIRE_OVERLAY_STRIPS; strip++) {
				float s0 = strip / (float) FIRE_OVERLAY_STRIPS;
				float s1 = (strip + 1) / (float) FIRE_OVERLAY_STRIPS;
				// Screen positions of the strip's top-left, top-right and bottom-left.
				float[] topLeft = projectFireVertex(Mth.lerp(s0, -0.5F, 0.5F), 0.5F, cos, sin, offsetX, tanHalf, aspect, width, height);
				float[] topRight = projectFireVertex(Mth.lerp(s1, -0.5F, 0.5F), 0.5F, cos, sin, offsetX, tanHalf, aspect, width, height);
				float[] bottomLeft = projectFireVertex(Mth.lerp(s0, -0.5F, 0.5F), -0.5F, cos, sin, offsetX, tanHalf, aspect, width, height);
				guiGraphics.pose().pushMatrix();
				// Map the unit square onto the parallelogram spanned by the
				// strip's top edge and left edge.
				guiGraphics.pose().mul(new org.joml.Matrix3x2f(
					(topRight[0] - topLeft[0]) / FIRE_OVERLAY_UNIT, (topRight[1] - topLeft[1]) / FIRE_OVERLAY_UNIT,
					(bottomLeft[0] - topLeft[0]) / FIRE_OVERLAY_UNIT, (bottomLeft[1] - topLeft[1]) / FIRE_OVERLAY_UNIT,
					topLeft[0], topLeft[1]));
				guiGraphics.submitRustSemanticBlit(RenderPipelines.FIRE_SCREEN_EFFECT,
					sprite.atlasLocation(), 0, 0, FIRE_OVERLAY_UNIT, FIRE_OVERLAY_UNIT,
					Mth.lerp(s0, uA, uB), vTop, Mth.lerp(s1, uA, uB), vBottom, color
				);
				guiGraphics.pose().popMatrix();
			}
		}
	}

	private static final int FIRE_OVERLAY_STRIPS = 8;
	private static final int FIRE_OVERLAY_UNIT = 1024;

	/** Projects one fire-quad vertex (model x, y at z = -0.5) to GUI coordinates. */
	private static float[] projectFireVertex(float x, float y, float cos, float sin, float offsetX,
			float tanHalf, float aspect, int width, int height) {
		float z = -0.5F;
		float viewX = x * cos + z * sin + offsetX;
		float viewY = y - 0.3F;
		float viewZ = -x * sin + z * cos;
		float depth = -viewZ;
		float ndcX = viewX / (depth * aspect * tanHalf);
		float ndcY = viewY / (depth * tanHalf);
		return new float[] { (ndcX + 1.0F) * 0.5F * width, (1.0F - ndcY) * 0.5F * height };
	}

	/**
	 * Vanilla draws the view-blocking sprite as a view-space quad spanning
	 * [-1, 1] at z = -0.5 under the hand projection (`itemFov`), so the screen
	 * shows only its central region, mirrored horizontally, tinted 0.1 grey
	 * and opaque. Reproduce that exact screen-space UV window.
	 */
	private static void submitRustViewBlockingOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics,
			TextureAtlasSprite sprite, float itemFov) {
		int width = guiGraphics.guiWidth();
		int height = guiGraphics.guiHeight();
		float halfHeight = 0.5F * (float) Math.tan(Math.toRadians(itemFov) * 0.5);
		float halfWidth = halfHeight * width / (float) height;
		// Visible quad extent in its own [-1, 1] units and the screen span it covers.
		float quadX = Math.min(1.0F, halfWidth);
		float quadY = Math.min(1.0F, halfHeight);
		int x0 = Math.round(width * 0.5F * (1.0F - quadX / halfWidth));
		int x1 = width - x0;
		int y0 = Math.round(height * 0.5F * (1.0F - quadY / halfHeight));
		int y1 = height - y0;
		// Quad x = -1 samples u1 and x = +1 samples u0; y = +1 (top) samples v0.
		float uLeft = Mth.lerp((1.0F - quadX) * 0.5F, sprite.getU1(), sprite.getU0());
		float uRight = Mth.lerp((1.0F + quadX) * 0.5F, sprite.getU1(), sprite.getU0());
		float vTop = Mth.lerp((1.0F - quadY) * 0.5F, sprite.getV0(), sprite.getV1());
		float vBottom = Mth.lerp((1.0F + quadY) * 0.5F, sprite.getV0(), sprite.getV1());
		guiGraphics.submitRustSemanticBlit(RenderPipelines.BLOCK_SCREEN_EFFECT,
			sprite.atlasLocation(), x0, y0, x1 - x0, y1 - y0,
			uLeft, vTop, uRight, vBottom, ARGB.colorFromFloat(1.0F, 0.1F, 0.1F, 0.1F)
		);
	}

	private void renderItemActivationAnimation(PoseStack poseStack, float f, SubmitNodeCollector submitNodeCollector) {
		if (this.itemActivationItem != null && this.itemActivationTicks > 0) {
			int i = 40 - this.itemActivationTicks;
			float g = (i + f) / 40.0F;
			float h = g * g;
			float j = g * h;
			float k = 10.25F * j * h - 24.95F * h * h + 25.5F * j - 13.8F * h + 4.0F * g;
			float l = k * (float) Math.PI;
			float m = (float)this.minecraft.getWindow().getWidth() / this.minecraft.getWindow().getHeight();
			float n = this.itemActivationOffX * 0.3F * m;
			float o = this.itemActivationOffY * 0.3F;
			poseStack.pushPose();
			poseStack.translate(n * Mth.abs(Mth.sin(l * 2.0F)), o * Mth.abs(Mth.sin(l * 2.0F)), -10.0F + 9.0F * Mth.sin(l));
			float p = 0.8F;
			poseStack.scale(0.8F, 0.8F, 0.8F);
			poseStack.mulPose(Axis.YP.rotationDegrees(900.0F * Mth.abs(Mth.sin(l))));
			poseStack.mulPose(Axis.XP.rotationDegrees(6.0F * Mth.cos(g * 8.0F)));
			poseStack.mulPose(Axis.ZP.rotationDegrees(6.0F * Mth.cos(g * 8.0F)));
			boolean rustSemanticItem = net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan();
			if (!rustSemanticItem) {
				// OpenGL compatibility owns the implicit lighting state. Rust Vulkan
				// receives copied item/material semantics and must not borrow this Java
				// GPU-side lighting setup while extracting the activation item.
				this.minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
			}
			ItemStackRenderState itemStackRenderState = new ItemStackRenderState();
			this.minecraft
				.getItemModelResolver()
				.updateForTopItem(itemStackRenderState, this.itemActivationItem, ItemDisplayContext.FIXED, this.minecraft.level, null, 0);
			itemStackRenderState.submitSemantic(poseStack, submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	public void resetItemActivation() {
		this.itemActivationItem = null;
	}

	public void displayItemActivation(ItemStack itemStack, RandomSource randomSource) {
		this.itemActivationItem = itemStack;
		this.itemActivationTicks = 40;
		this.itemActivationOffX = randomSource.nextFloat() * 2.0F - 1.0F;
		this.itemActivationOffY = randomSource.nextFloat() * 2.0F - 1.0F;
	}

	@Nullable
	private static BlockState getViewBlockingState(Player player) {
		MutableBlockPos mutableBlockPos = new MutableBlockPos();

		for (int i = 0; i < 8; i++) {
			double d = player.getX() + ((i >> 0) % 2 - 0.5F) * player.getBbWidth() * 0.8F;
			double e = player.getEyeY() + ((i >> 1) % 2 - 0.5F) * 0.1F * player.getScale();
			double f = player.getZ() + ((i >> 2) % 2 - 0.5F) * player.getBbWidth() * 0.8F;
			mutableBlockPos.set(d, e, f);
			BlockState blockState = player.level().getBlockState(mutableBlockPos);
			if (blockState.getRenderShape() != RenderShape.INVISIBLE && blockState.isViewBlocking(player.level(), mutableBlockPos)) {
				return blockState;
			}
		}

		return null;
	}

	private static void renderTex(TextureAtlasSprite textureAtlasSprite, PoseStack poseStack, MultiBufferSource multiBufferSource) {
		ensureJavaCompatibilityRoute();
		float f = 0.1F;
		int i = ARGB.colorFromFloat(1.0F, 0.1F, 0.1F, 0.1F);
		float g = -1.0F;
		float h = 1.0F;
		float j = -1.0F;
		float k = 1.0F;
		float l = -0.5F;
		float m = textureAtlasSprite.getU0();
		float n = textureAtlasSprite.getU1();
		float o = textureAtlasSprite.getV0();
		float p = textureAtlasSprite.getV1();
		Matrix4f matrix4f = poseStack.last().pose();
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.blockScreenEffect(textureAtlasSprite.atlasLocation()));
		vertexConsumer.addVertex(matrix4f, -1.0F, -1.0F, -0.5F).setUv(n, p).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, -1.0F, -0.5F).setUv(m, p).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, 1.0F, -0.5F).setUv(m, o).setColor(i);
		vertexConsumer.addVertex(matrix4f, -1.0F, 1.0F, -0.5F).setUv(n, o).setColor(i);
	}

	private static void renderWater(Minecraft minecraft, PoseStack poseStack, MultiBufferSource multiBufferSource) {
		ensureJavaCompatibilityRoute();
		// Iris: Disable underwater overlay rendering when shader pack requests it
		net.irisshaders.iris.pipeline.WorldRenderingPipeline pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
		if (pipeline != null && !pipeline.shouldRenderUnderwaterOverlay()) {
			return;
		}
		
		BlockPos blockPos = BlockPos.containing(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ());
		float f = LightTexture.getBrightness(minecraft.player.level().dimensionType(), minecraft.player.level().getMaxLocalRawBrightness(blockPos));
		int i = ARGB.colorFromFloat(0.1F, f, f, f);
		float g = 4.0F;
		float h = -1.0F;
		float j = 1.0F;
		float k = -1.0F;
		float l = 1.0F;
		float m = -0.5F;
		float n = -minecraft.player.getYRot() / 64.0F;
		float o = minecraft.player.getXRot() / 64.0F;
		Matrix4f matrix4f = poseStack.last().pose();
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.blockScreenEffect(UNDERWATER_LOCATION));
		vertexConsumer.addVertex(matrix4f, -1.0F, -1.0F, -0.5F).setUv(4.0F + n, 4.0F + o).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, -1.0F, -0.5F).setUv(0.0F + n, 4.0F + o).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, 1.0F, -0.5F).setUv(0.0F + n, 0.0F + o).setColor(i);
		vertexConsumer.addVertex(matrix4f, -1.0F, 1.0F, -0.5F).setUv(4.0F + n, 0.0F + o).setColor(i);
	}

	private static void renderFire(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite) {
		ensureJavaCompatibilityRoute();
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.fireScreenEffect(textureAtlasSprite.atlasLocation()));
		float f = textureAtlasSprite.getU0();
		float g = textureAtlasSprite.getU1();
		float h = (f + g) / 2.0F;
		float i = textureAtlasSprite.getV0();
		float j = textureAtlasSprite.getV1();
		float k = (i + j) / 2.0F;
		float l = textureAtlasSprite.uvShrinkRatio();
		float m = Mth.lerp(l, f, h);
		float n = Mth.lerp(l, g, h);
		float o = Mth.lerp(l, i, k);
		float p = Mth.lerp(l, j, k);
		float q = 1.0F;

		for (int r = 0; r < 2; r++) {
			poseStack.pushPose();
			float s = -0.5F;
			float t = 0.5F;
			float u = -0.5F;
			float v = 0.5F;
			float w = -0.5F;
			poseStack.translate(-(r * 2 - 1) * 0.24F, -0.3F, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees((r * 2 - 1) * 10.0F));
			Matrix4f matrix4f = poseStack.last().pose();
			vertexConsumer.addVertex(matrix4f, -0.5F, -0.5F, -0.5F).setUv(n, p).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			vertexConsumer.addVertex(matrix4f, 0.5F, -0.5F, -0.5F).setUv(m, p).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			vertexConsumer.addVertex(matrix4f, 0.5F, 0.5F, -0.5F).setUv(m, o).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			vertexConsumer.addVertex(matrix4f, -0.5F, 0.5F, -0.5F).setUv(n, o).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			poseStack.popPose();
		}
	}

	/** Keeps every legacy screen overlay behind the OpenGL compatibility boundary. */
	private static void ensureJavaCompatibilityRoute() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException(
				"Java screen-effect rendering is unavailable on selected Vulkan; "
				+ "Java underwater screen effect is unavailable on selected Vulkan "
				+ "(Java underwater screen effect is unavailable while Rust owns whole-frame presentation)"
			);
		}
	}
}
