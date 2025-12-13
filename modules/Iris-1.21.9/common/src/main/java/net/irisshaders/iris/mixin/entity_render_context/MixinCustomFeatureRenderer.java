package net.irisshaders.iris.mixin.entity_render_context;

import net.irisshaders.iris.mixinterface.ModelStorage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomFeatureRenderer.class)
public class MixinCustomFeatureRenderer {
	/**
	 * Capture and process each customGeometrySubmit as the loop iterates.
	 * ModifyVariable captures the variable when it's assigned (at the start of each loop iteration).
	 */
	@ModifyVariable(method = "render", at = @At(value = "STORE"), ordinal = 0)
	private SubmitNodeStorage.CustomGeometrySubmit iris$captureCustomGeometrySubmit(SubmitNodeStorage.CustomGeometrySubmit modelSubmit) {
		((ModelStorage) (Object) modelSubmit).iris$set();
		return modelSubmit;
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void iris$clear(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
		CapturedRenderingState.INSTANCE.setCurrentEntity(0);
		CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
	}
}
