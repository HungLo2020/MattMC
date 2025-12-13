package net.irisshaders.iris.mixin.entity_render_context;

import net.irisshaders.iris.layer.GbufferPrograms;
import net.irisshaders.iris.mixinterface.ModelStorage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextFeatureRenderer.class)
public class MixinTextFeatureRenderer {
	@Unique
	private boolean hasBE = false;

	/**
	 * Capture and process each textSubmit as the loop iterates.
	 * ModifyVariable captures the variable when it's assigned (at the start of each loop iteration).
	 */
	@ModifyVariable(method = "render", at = @At(value = "STORE"), ordinal = 0)
	private SubmitNodeStorage.TextSubmit iris$captureTextSubmit(SubmitNodeStorage.TextSubmit modelSubmit) {
		((ModelStorage) (Object) modelSubmit).iris$set();
		if (((ModelStorage) (Object) modelSubmit).iris$wasBE()) {
			hasBE = true;
			ImmediateState.isRenderingBEs = true;
		} else if (hasBE) {
			hasBE = false;
			ImmediateState.isRenderingBEs = false;
		}
		return modelSubmit;
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void iris$clear(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
		CapturedRenderingState.INSTANCE.setCurrentEntity(0);
		CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
		hasBE = false;
		ImmediateState.isRenderingBEs = false;
	}
}
