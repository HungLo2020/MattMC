package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public abstract class EyesLayer<S extends EntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	public EyesLayer(RenderLayerParent<S, M> renderLayerParent) {
		super(renderLayerParent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S entityRenderState, float f, float g) {
		ResourceLocation textureIdentity = this.semanticTexture(entityRenderState);
		if (textureIdentity != null) {
			submitNodeCollector.order(1).submitModelSemanticTexture(
				this.getParentModel(), entityRenderState, poseStack, this.renderType(), i,
				OverlayTexture.NO_OVERLAY, -1, textureIdentity, entityRenderState.outlineColor, null
			);
		} else {
			submitNodeCollector.order(1).submitModel(
				this.getParentModel(), entityRenderState, poseStack, this.renderType(), i,
				OverlayTexture.NO_OVERLAY, -1, null, entityRenderState.outlineColor, null
			);
		}
	}

	/** Optional semantic identity; unported extension layers remain unavailable under Rust Vulkan. */
	protected ResourceLocation semanticTexture() {
		return null;
	}

	/** Allows state-dependent extension-layer textures without crossing the
	 * semantic boundary with renderer or GPU objects. */
	protected ResourceLocation semanticTexture(S entityRenderState) {
		return this.semanticTexture();
	}

	public abstract RenderType renderType();
}
