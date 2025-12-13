package net.irisshaders.iris.mixin.entity_render_context;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.iris.mixinterface.ModelStorage;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModelFeatureRenderer.class)
public abstract class MixinModelFeatureRenderer {
	/**
	 * Wrap renderModel calls in renderTranslucents to capture the modelSubmit.
	 * This avoids @Local capture issues on MC 1.21.10.
	 */
	@WrapOperation(method = "renderTranslucents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;renderModel(Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"))
	private void iris$wrapRenderModelTranslucents(ModelFeatureRenderer instance, SubmitNodeStorage.ModelSubmit<?> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer, OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource, Operation<Void> original) {
		((ModelStorage) (Object) modelSubmit).iris$set();
		original.call(instance, modelSubmit, renderType, vertexConsumer, outlineBufferSource, bufferSource);
	}

	/**
	 * Wrap renderModel calls in renderBatch to capture the modelSubmit.
	 * This avoids @Local capture issues on MC 1.21.10.
	 */
	@WrapOperation(method = "renderBatch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;renderModel(Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"))
	private void iris$wrapRenderModelBatch(ModelFeatureRenderer instance, SubmitNodeStorage.ModelSubmit<?> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer, OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource, Operation<Void> original) {
		((ModelStorage) (Object) modelSubmit).iris$set();
		original.call(instance, modelSubmit, renderType, vertexConsumer, outlineBufferSource, bufferSource);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void iris$clear(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource2, CallbackInfo ci) {
		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
		CapturedRenderingState.INSTANCE.setCurrentEntity(0);
		CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
	}
}
