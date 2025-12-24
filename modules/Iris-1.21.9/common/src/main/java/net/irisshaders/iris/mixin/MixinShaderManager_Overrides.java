package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.vertices.ImmediateState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(GlDevice.class)
public abstract class MixinShaderManager_Overrides {
	@Unique
	private Set<RenderPipeline> missingShaders = new HashSet<>();

	@Inject(method = "getOrCompilePipeline", at = @At(value = "HEAD"), cancellable = true)
	private void redirectIrisProgram(RenderPipeline renderPipeline, CallbackInfoReturnable<GlRenderPipeline> cir) {
		if (renderPipeline == CompositeRenderer.COMPOSITE_PIPELINE) return;

		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof IrisRenderingPipeline irisPipeline && irisPipeline.shouldOverrideShaders() && !ImmediateState.bypass) {
			RenderPipeline newProgram = renderPipeline;

			GlProgram program = override(irisPipeline, newProgram);

			if (program != null) {
				cir.setReturnValue(new GlRenderPipeline(renderPipeline, program));
			} else if (missingShaders.add(renderPipeline)) {
				if (renderPipeline.getLocation().getNamespace().equals("minecraft")) {
					Iris.logger.fatal("Missing program " + renderPipeline.getLocation() + " in override list. This is likely an Iris bug!!!", new Throwable());
				} else {
					Iris.logger.error("Missing program " + renderPipeline.getLocation() + " in override list. This is not a critical problem, but it could lead to weird rendering.", new Throwable());
				}
			}
		}
	}

	/*@Inject(method = "compilePipeline", at = @At("RETURN"))
	private static void iris$setSkip(ShaderProgram shaderProgram, ShaderProgramConfig shaderProgramConfig, CompiledShader compiledShader, CompiledShader compiledShader2, CallbackInfoReturnable<CompiledShaderProgram> cir) {
		CompiledShaderProgram p = cir.getReturnValue();
		MethodHandle shouldSkip = shouldSkipList.computeIfAbsent(p.getClass(), x -> {
			try {
				MethodHandle iris$skipDraw = MethodHandles.lookup().findVirtual(x, "iris$skipDraw", MethodType.methodType(boolean.class));
				Iris.logger.warn("Class " + x.getName() + " has opted out of being rendered with shaders.");
				return iris$skipDraw;
			} catch (NoSuchMethodException | IllegalAccessException e) {
				return NONE;
			}
		});


		if (Iris.getIrisConfig().shouldSkip(shaderProgram.configId())) {
			shouldSkip = ALWAYS;
		}

		((ShaderInstanceInterface) p).setShouldSkip(shouldSkip);
	}*/

	private static GlProgram override(IrisRenderingPipeline pipeline, RenderPipeline shaderProgram) {
		ShaderKey shaderKey = IrisPipelines.getPipeline(pipeline, shaderProgram);

		return shaderKey == null ? null : pipeline.getShaderMap().getShader(shaderKey);
	}
}
