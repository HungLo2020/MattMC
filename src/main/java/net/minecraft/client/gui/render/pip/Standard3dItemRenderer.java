package net.minecraft.client.gui.render.pip;

import net.blaze3d.platform.Lighting;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class Standard3dItemRenderer extends PictureInPictureRenderer<OversizedItemRenderState> {
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

	@Override
	public Class<OversizedItemRenderState> getRenderStateClass() {
		return OversizedItemRenderState.class;
	}

	@Override
	protected void renderToTexture(OversizedItemRenderState oversizedItemRenderState, PoseStack poseStack) {
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
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
	protected boolean textureIsReadyToBlit(OversizedItemRenderState oversizedItemRenderState) {
		TrackingItemStackRenderState trackingItemStackRenderState = oversizedItemRenderState.guiItemRenderState().itemStackRenderState();
		return !trackingItemStackRenderState.isAnimated() && trackingItemStackRenderState.getModelIdentity().equals(this.modelOnTextureIdentity);
	}

	@Override
	protected float getTranslateY(OversizedItemRenderState oversizedItemRenderState, int i, int j) {
		return i / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "standard_3d_item";
	}
}