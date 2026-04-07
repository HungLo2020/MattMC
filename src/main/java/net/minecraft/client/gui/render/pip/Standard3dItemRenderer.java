package net.minecraft.client.gui.render.pip;

import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.platform.Lighting;
import net.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
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
public class Standard3dItemRenderer extends PictureInPictureRenderer<OversizedItemRenderState> {
	private static final int STANDARD_3D_ITEM_GUARD_PIXELS = 1;
	private static final AtomicBoolean STANDARD_3D_ITEM_DEBUG_DUMPED = new AtomicBoolean();
	private static final AtomicBoolean FORCED_STANDARD_3D_ITEM_DEBUG_DUMPED = new AtomicBoolean();
	private final CachedOrthoProjectionMatrixBuffer vulkanProjectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer(
		"PIP - Standard3dItemRenderer Vulkan Depth", -1000.0F, 1000.0F, true, true
	);
	private boolean usedOnThisFrame;
	@Nullable
	private Object modelOnTextureIdentity;

	public Standard3dItemRenderer(MultiBufferSource.BufferSource bufferSource) {
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
		if (STANDARD_3D_ITEM_DEBUG_DUMPED.get()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.getModelManager().hasLoadedModels() || minecraft.level == null || minecraft.player == null) {
			return;
		}

		TrackingItemStackRenderState trackingItemStackRenderState = new TrackingItemStackRenderState();
		minecraft
			.getItemModelResolver()
			.updateForTopItem(trackingItemStackRenderState, new ItemStack(Blocks.GRASS_BLOCK), ItemDisplayContext.GUI, minecraft.level, minecraft.player, 0);
		GuiItemRenderState guiItemRenderState = new GuiItemRenderState(
			"debug_standard_3d_grass_block", new Matrix3x2f(), trackingItemStackRenderState, -32, -32, null
		);
		this.invalidateTexture();
		this.prepare(new OversizedItemRenderState(guiItemRenderState, -32, -32, -16, -16), guiRenderState, i);
	}

	@Override
	public Class<OversizedItemRenderState> getRenderStateClass() {
		return OversizedItemRenderState.class;
	}

	@Override
	protected void renderToTexture(OversizedItemRenderState oversizedItemRenderState, PoseStack poseStack) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
		poseStack.scale(1.0F, 1.0F, -1.0F);
		if (this.usesExpandedStandardItemTexture(guiItemRenderState)) {
			AABB aABB = trackingItemStackRenderState.getModelBoundingBox();
			float f = (float)(-(aABB.minX + aABB.maxX) / 2.0);
			float g = (float)(-(aABB.minY + aABB.maxY) / 2.0);
			poseStack.translate(f, g, 0.0F);
		}
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);

		FeatureRenderDispatcher featureRenderDispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
		SubmitNodeStorage submitNodeStorage = featureRenderDispatcher.getSubmitNodeStorage();
		trackingItemStackRenderState.submit(poseStack, submitNodeStorage, 15728880, OverlayTexture.NO_OVERLAY, 0);
		featureRenderDispatcher.renderAllFeatures();
		this.modelOnTextureIdentity = trackingItemStackRenderState.getModelIdentity();
	}

	@Override
	protected void blitTexture(OversizedItemRenderState oversizedItemRenderState, GuiRenderState guiRenderState) {
		super.blitTexture(oversizedItemRenderState, guiRenderState);
		this.usedOnThisFrame = true;
	}

	@Override
	protected String getDebugDumpName(OversizedItemRenderState oversizedItemRenderState, GuiRenderState guiRenderState, int i) {
		if (oversizedItemRenderState.x0() < 0 && oversizedItemRenderState.y0() < 0) {
			return FORCED_STANDARD_3D_ITEM_DEBUG_DUMPED.compareAndSet(false, true) ? "gui_forced_standard_3d_grass_block_pip_debug" : null;
		}

		if (STANDARD_3D_ITEM_DEBUG_DUMPED.compareAndSet(false, true)) {
			return "gui_standard_3d_item_pip_debug";
		}

		return null;
	}

	@Override
	protected int getRenderTextureWidth(OversizedItemRenderState oversizedItemRenderState, int i) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		return (this.usesExpandedStandardItemTexture(guiItemRenderState)
			? this.getExpandedStandardItemWidth(guiItemRenderState)
			: oversizedItemRenderState.x1() - oversizedItemRenderState.x0()) * i + STANDARD_3D_ITEM_GUARD_PIXELS * 2;
	}

	@Override
	protected int getRenderTextureHeight(OversizedItemRenderState oversizedItemRenderState, int i) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		return (this.usesExpandedStandardItemTexture(guiItemRenderState)
			? this.getExpandedStandardItemHeight(guiItemRenderState)
			: oversizedItemRenderState.y1() - oversizedItemRenderState.y0()) * i + STANDARD_3D_ITEM_GUARD_PIXELS * 2;
	}

	@Override
	protected boolean textureIsReadyToBlit(OversizedItemRenderState oversizedItemRenderState) {
		TrackingItemStackRenderState trackingItemStackRenderState = oversizedItemRenderState.guiItemRenderState().itemStackRenderState();
		return !trackingItemStackRenderState.isAnimated() && trackingItemStackRenderState.getModelIdentity().equals(this.modelOnTextureIdentity);
	}

	@Override
	protected GpuBufferSlice getProjectionMatrixBuffer(int i, int j) {
		return net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			? this.vulkanProjectionMatrixBuffer.getBuffer(i, j)
			: super.getProjectionMatrixBuffer(i, j);
	}

	private boolean usesExpandedStandardItemTexture(GuiItemRenderState guiItemRenderState) {
		return this.getExpandedStandardItemWidth(guiItemRenderState) > 16 || this.getExpandedStandardItemHeight(guiItemRenderState) > 16;
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
		return i / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "standard_3d_item";
	}

	@Override
	public void close() {
		super.close();
		this.vulkanProjectionMatrixBuffer.close();
	}
}