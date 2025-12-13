package net.irisshaders.iris.mixin.fantastic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.NeoLambdas;
import net.irisshaders.iris.fantastic.ParticleRenderingPhase;
import net.irisshaders.iris.fantastic.PhasedParticleEngine;
import net.irisshaders.iris.mixinterface.ParticleRenderStateExtension;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
	@Shadow
	@Final
	private ParticlesRenderState particlesRenderState;

	@Shadow
	@Final
	private SubmitNodeStorage submitNodeStorage;

	@Shadow
	@Final
	private FeatureRenderDispatcher featureRenderDispatcher;

	@Shadow
	@Final
	private LevelTargetBundle targets;

	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	@Unique
	private FramePass iris$currentFramePass;

	/**
	 * Capture the FramePass when it's stored to use later.
	 * This avoids @Local capture issues on MC 1.21.10.
	 */
	@ModifyVariable(method = "addMainPass", at = @At(value = "STORE"), ordinal = 0)
	private FramePass iris$captureFramePass(FramePass framePass) {
		this.iris$currentFramePass = framePass;
		return framePass;
	}

	@Inject(method = "addMainPass", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/LevelRenderState;haveGlowingEntities:Z"))
	private void iris$addParticleTarget(FrameGraphBuilder frameGraphBuilder, Frustum frustum, Matrix4f matrix4f, GpuBufferSlice gpuBufferSlice, boolean bl, LevelRenderState levelRenderState, DeltaTracker deltaTracker, ProfilerFiller profilerFiller, CallbackInfo ci) {
		ParticleRenderingSettings settings = getRenderingSettings();

		if (settings == ParticleRenderingSettings.BEFORE) {
			if (this.targets.particles != null && this.iris$currentFramePass != null) {
				this.targets.particles = this.iris$currentFramePass.readsAndWrites(this.targets.particles);
			}
		}
	}

	@WrapOperation(method = NeoLambdas.NEO_RENDER_ALL_FEATURES_MAIN, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V"), require = 1)
	private void iris$renderMainParticles(FeatureRenderDispatcher instance, Operation<Void> original) {
		ParticleRenderingSettings settings = getRenderingSettings();

		if (settings == ParticleRenderingSettings.AFTER) {
			original.call(instance);
			return;
		} else {
			this.particlesRenderState.submit(submitNodeStorage, this.levelRenderState.cameraRenderState);
			((PhasedParticleEngine) ((FeatureRenderDispatcherAccessor) this.featureRenderDispatcher).getParticleFeatureRenderer()).setParticleRenderingPhase(settings == ParticleRenderingSettings.BEFORE ? ParticleRenderingPhase.EVERYTHING : ParticleRenderingPhase.OPAQUE);
			original.call(instance);
			((PhasedParticleEngine) ((FeatureRenderDispatcherAccessor) this.featureRenderDispatcher).getParticleFeatureRenderer()).setParticleRenderingPhase(ParticleRenderingPhase.EVERYTHING);

			if (settings == ParticleRenderingSettings.BEFORE) {
				particlesRenderState.reset();
			}
		}
	}

	@Unique
	private ParticleRenderingSettings getRenderingSettings() {
		return Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::getParticleRenderingSettings).orElse(ParticleRenderingSettings.MIXED);
	}

	@Inject(method = "addParticlesPass", at = @At("HEAD"), cancellable = true)
	private void iris$disablePassIfNeeded(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
		if (getRenderingSettings() == ParticleRenderingSettings.BEFORE) {
			ci.cancel();
		}
	}

	@Redirect(method = NeoLambdas.NEO_SUBMIT_PARTICLES, require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeStorage;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"))
	private void iris$redirectToAvoidItemPickupParticles(ParticlesRenderState instance, SubmitNodeStorage submitNodeStorage, CameraRenderState cameraRenderState) {
		ParticleRenderingSettings settings = getRenderingSettings();

		if (settings == ParticleRenderingSettings.MIXED) {
			((ParticleRenderStateExtension) instance).submitWithoutItems(submitNodeStorage, cameraRenderState);
		} else {
			instance.submit(submitNodeStorage, cameraRenderState);
		}
	}

	@WrapOperation(method = NeoLambdas.NEO_RENDER_ALL_FEATURES_PARTICLES, require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V"))
	private void iris$renderTranslucentParticles(FeatureRenderDispatcher instance, Operation<Void> original) {
		ParticleRenderingSettings settings = getRenderingSettings();

		((PhasedParticleEngine) ((FeatureRenderDispatcherAccessor) this.featureRenderDispatcher).getParticleFeatureRenderer()).setParticleRenderingPhase(settings == ParticleRenderingSettings.AFTER ? ParticleRenderingPhase.EVERYTHING : ParticleRenderingPhase.TRANSLUCENT);
		original.call(instance);
		((PhasedParticleEngine) ((FeatureRenderDispatcherAccessor) this.featureRenderDispatcher).getParticleFeatureRenderer()).setParticleRenderingPhase(ParticleRenderingPhase.EVERYTHING);
	}
}
