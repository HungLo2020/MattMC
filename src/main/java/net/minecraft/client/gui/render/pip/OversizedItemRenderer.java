package net.minecraft.client.gui.render.pip;

import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.platform.Lighting;
import net.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

@Environment(EnvType.CLIENT)
public class OversizedItemRenderer extends PictureInPictureRenderer<OversizedItemRenderState> {
	private static final AtomicBoolean STANDARD_BLOCK_ITEM_DEBUG_DUMPED = new AtomicBoolean();
	private final CachedOrthoProjectionMatrixBuffer vulkanProjectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer(
		"PIP - OversizedItemRenderer Vulkan Depth", -1000.0F, 1000.0F, true, true
	);
	private boolean usedOnThisFrame;
	@Nullable
	private Object modelOnTextureIdentity;

	public OversizedItemRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	public boolean usedOnThisFrame() {
		return this.usedOnThisFrame;
	}

	public void resetUsedOnThisFrame() {
		this.usedOnThisFrame = false;
	}

	public void invalidateTexture() {
		this.modelOnTextureIdentity = null;
	}

	public void prepareDebugStandardBlockItemDump(GuiRenderState guiRenderState, int i) {
		if (STANDARD_BLOCK_ITEM_DEBUG_DUMPED.get()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.getModelManager().hasLoadedModels()) {
			return;
		}

		TrackingItemStackRenderState trackingItemStackRenderState = new TrackingItemStackRenderState();
		minecraft
			.getItemModelResolver()
			.updateForTopItem(trackingItemStackRenderState, new ItemStack(Blocks.GRASS_BLOCK), ItemDisplayContext.GUI, minecraft.level, minecraft.player, 0);
		GuiItemRenderState guiItemRenderState = new GuiItemRenderState(
			"debug_grass_block", new Matrix3x2f(), trackingItemStackRenderState, -32, -32, null
		);
		this.invalidateTexture();
		this.prepare(new OversizedItemRenderState(guiItemRenderState, -32, -32, -16, -16), guiRenderState, i);
	}

	@Override
	public Class<OversizedItemRenderState> getRenderStateClass() {
		return OversizedItemRenderState.class;
	}

	protected void renderToTexture(OversizedItemRenderState oversizedItemRenderState, PoseStack poseStack) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
		ScreenRectangle screenRectangle = guiItemRenderState.oversizedItemBounds();
		if (screenRectangle == null) {
			boolean bl = trackingItemStackRenderState.usesBlockLight();
			poseStack.scale(1.0F, -1.0F, -1.0F);
			if (bl && this.usesExpandedStandardItemTexture(guiItemRenderState)) {
				AABB aABB = trackingItemStackRenderState.getModelBoundingBox();
				float f = (float)(-(aABB.minX + aABB.maxX) / 2.0);
				float g = (float)(-(aABB.minY + aABB.maxY) / 2.0);
				poseStack.translate(f, g, 0.0F);
			}
		} else {
			poseStack.scale(1.0F, -1.0F, -1.0F);
			float f = (screenRectangle.left() + screenRectangle.right()) / 2.0F;
			float g = (screenRectangle.top() + screenRectangle.bottom()) / 2.0F;
			float h = guiItemRenderState.x() + 8.0F;
			float i = guiItemRenderState.y() + 8.0F;
			poseStack.translate((h - f) / 16.0F, (g - i) / 16.0F, 0.0F);
		}
		boolean bl = !trackingItemStackRenderState.usesBlockLight();
		if (bl) {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		} else {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
		}

		FeatureRenderDispatcher featureRenderDispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
		SubmitNodeStorage submitNodeStorage = featureRenderDispatcher.getSubmitNodeStorage();
		trackingItemStackRenderState.submit(poseStack, submitNodeStorage, 15728880, OverlayTexture.NO_OVERLAY, 0);
		featureRenderDispatcher.renderAllFeatures();
		this.modelOnTextureIdentity = trackingItemStackRenderState.getModelIdentity();
	}

	@Override
	protected String getDebugDumpName(OversizedItemRenderState oversizedItemRenderState, GuiRenderState guiRenderState, int i) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		if (guiItemRenderState.oversizedItemBounds() == null
			&& guiItemRenderState.itemStackRenderState().usesBlockLight()
			&& STANDARD_BLOCK_ITEM_DEBUG_DUMPED.compareAndSet(false, true)) {
			return oversizedItemRenderState.x0() < 0 && oversizedItemRenderState.y0() < 0
				? "gui_forced_grass_block_pip_debug"
				: "gui_standard_block_item_pip_debug";
		}

		return null;
	}

	public void blitTexture(OversizedItemRenderState oversizedItemRenderState, GuiRenderState guiRenderState) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		if (guiItemRenderState.oversizedItemBounds() == null && !guiItemRenderState.itemStackRenderState().usesBlockLight()) {
			this.submitBlitTexture(guiRenderState, oversizedItemRenderState, 0.0F, 1.0F, 0.0F, 1.0F);
		} else {
			super.blitTexture(oversizedItemRenderState, guiRenderState);
		}
		this.usedOnThisFrame = true;
	}

	@Override
	protected int getRenderTextureWidth(OversizedItemRenderState oversizedItemRenderState, int i) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		return this.usesExpandedStandardItemTexture(guiItemRenderState) ? this.getExpandedStandardItemWidth(guiItemRenderState) * i : super.getRenderTextureWidth(oversizedItemRenderState, i);
	}

	@Override
	protected int getRenderTextureHeight(OversizedItemRenderState oversizedItemRenderState, int i) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		return this.usesExpandedStandardItemTexture(guiItemRenderState) ? this.getExpandedStandardItemHeight(guiItemRenderState) * i : super.getRenderTextureHeight(oversizedItemRenderState, i);
	}

	public boolean textureIsReadyToBlit(OversizedItemRenderState oversizedItemRenderState) {
		TrackingItemStackRenderState trackingItemStackRenderState = oversizedItemRenderState.guiItemRenderState().itemStackRenderState();
		return !trackingItemStackRenderState.isAnimated() && trackingItemStackRenderState.getModelIdentity().equals(this.modelOnTextureIdentity);
	}

	private boolean usesExpandedStandardItemTexture(GuiItemRenderState guiItemRenderState) {
		return guiItemRenderState.oversizedItemBounds() == null
			&& guiItemRenderState.itemStackRenderState().usesBlockLight()
			&& (this.getExpandedStandardItemWidth(guiItemRenderState) > 16 || this.getExpandedStandardItemHeight(guiItemRenderState) > 16);
	}

	private int getExpandedStandardItemWidth(GuiItemRenderState guiItemRenderState) {
		AABB aABB = guiItemRenderState.itemStackRenderState().getModelBoundingBox();
		return Math.max(16, Mth.ceil(aABB.getXsize() * 16.0));
	}

	private int getExpandedStandardItemHeight(GuiItemRenderState guiItemRenderState) {
		AABB aABB = guiItemRenderState.itemStackRenderState().getModelBoundingBox();
		return Math.max(16, Mth.ceil(aABB.getYsize() * 16.0));
	}

	@Override
	protected float getTranslateY(OversizedItemRenderState oversizedItemRenderState, int i, int j) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		return guiItemRenderState.oversizedItemBounds() == null ? i / 2.0F : j / 2.0F;
	}

	@Override
	protected GpuBufferSlice getProjectionMatrixBuffer(int i, int j) {
		return net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			? this.vulkanProjectionMatrixBuffer.getBuffer(i, j)
			: super.getProjectionMatrixBuffer(i, j);
	}

	@Override
	protected String getTextureLabel() {
		return "oversized_item";
	}

	@Override
	public void close() {
		super.close();
		this.vulkanProjectionMatrixBuffer.close();
	}
}
