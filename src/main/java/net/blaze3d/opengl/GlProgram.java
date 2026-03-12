package net.blaze3d.opengl;

import com.google.common.collect.Sets;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.VertexFormat;
import net.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.ShaderManager;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlProgram implements AutoCloseable, net.irisshaders.iris.mixinterface.ShaderInstanceInterface {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static Set<String> BUILT_IN_UNIFORMS = Sets.<String>newHashSet("Projection", "Lighting", "Fog", "Globals");
	public static GlProgram INVALID_PROGRAM = new GlProgram(-1, "invalid");
	/**
	 * Map of uniform names to uniform objects.
	 * 
	 * @apiNote Made public for Iris shader pipeline uniform management.
	 * Originally widened by: iris.accesswidener
	 */
	public final Map<String, Uniform> uniformsByName = new HashMap();
	private final int programId;
	private final String debugLabel;
	
	// Iris: Merged from MixinCompiledShaderProgram
	private static final com.google.common.collect.ImmutableSet<String> ATTRIBUTE_LIST = com.google.common.collect.ImmutableSet.of("Position", "Color", "Normal", "UV0", "UV1", "UV2");
	private static GlProgram lastAppliedShader;
	private java.lang.invoke.MethodHandle shouldSkip;
	
	static {
		net.irisshaders.iris.compat.SkipList.shouldSkipList.put(net.irisshaders.iris.pipeline.programs.ExtendedShader.class, net.irisshaders.iris.compat.SkipList.NONE);
		net.irisshaders.iris.compat.SkipList.shouldSkipList.put(net.irisshaders.iris.pipeline.programs.FallbackShader.class, net.irisshaders.iris.compat.SkipList.NONE);
	}

	public GlProgram(int i, String string) {
		this.programId = i;
		this.debugLabel = string;
	}

	public static GlProgram link(GlShaderModule glShaderModule, GlShaderModule glShaderModule2, VertexFormat vertexFormat, String string) throws ShaderManager.CompilationException {
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
		int i = VulkanicAPI.createShaderProgram(ctx);
		if (i <= 0) {
			throw new ShaderManager.CompilationException("Could not create shader program (returned program ID " + i + ")");
		} else {
			int j = 0;

			for (String string2 : vertexFormat.getElementAttributeNames()) {
				VulkanicAPI.setAttributeLocation(ctx, i, j, string2);
				j++;
			}

			VulkanicAPI.attachShader(ctx, i, glShaderModule.getShaderId());
			VulkanicAPI.attachShader(ctx, i, glShaderModule2.getShaderId());
			VulkanicAPI.linkProgram(ctx, i);
			boolean linked = VulkanicAPI.isProgramLinkSuccessful(ctx, i);
			String string2 = VulkanicAPI.getProgramInfoLog(ctx, i);
			if (linked && !string2.contains("Failed for unknown reason")) {
				if (!string2.isEmpty()) {
					LOGGER.info("Info log when linking program containing VS {} and FS {}. Log output: {}", glShaderModule.getId(), glShaderModule2.getId(), string2);
				}

				return new GlProgram(i, string);
			} else {
				throw new ShaderManager.CompilationException(
					"Error encountered when linking program containing VS " + glShaderModule.getId() + " and FS " + glShaderModule2.getId() + ". Log output: " + string2
				);
			}
		}
	}

	public void setupUniforms(List<RenderPipeline.UniformDescription> list, List<String> list2) {
		int i = 0;
		int j = 0;

		for (RenderPipeline.UniformDescription uniformDescription : list) {
			String string = uniformDescription.name();

			Uniform uniform = switch (uniformDescription.type()) {
				case UNIFORM_BUFFER -> {
					// Iris: Change uniform block index for IrisProgram (merged from MixinCompiledShaderProgram)
					int k;
					if (this instanceof net.irisshaders.iris.pipeline.programs.IrisProgram is) {
						k = is.iris$getBlockIndex(this.programId, string);
					} else {
						k = VulkanicAPI.getUniformBlockIndex(VulkanicAPI.getCommandContext(), this.programId, string);
					}
					if (k == -1) {
						yield null;
					} else {
						int l = i++;
						VulkanicAPI.uniformBlockBinding(VulkanicAPI.getCommandContext(), this.programId, k, l);
						yield new Uniform.Ubo(l);
					}
				}
				case TEXEL_BUFFER -> {
					int k = VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getCommandContext(), this.programId, string);
					if (k == -1) {
						// Iris: Silence warnings for known shaders (merged from MixinCompiledShaderProgram)
						if (!isKnownShader()) {
							LOGGER.warn("{} shader program does not use utb {} defined in the pipeline. This might be a bug.", this.debugLabel, string);
						}
						yield null;
					} else {
						int l = j++;
						yield new Uniform.Utb(k, l, (TextureFormat)Objects.requireNonNull(uniformDescription.textureFormat()));
					}
				}
			};

			if (uniform != null) {
				this.uniformsByName.put(string, uniform);
			}
		}

		for (String string2 : list2) {
			int m = VulkanicAPI.getUniformLocationWithLegacySamplerFallback(VulkanicAPI.getCommandContext(), this.programId, string2);
			if (m == -1) {
				// Iris: Silence warnings for known shaders (merged from MixinCompiledShaderProgram)
				if (!isKnownShader()) {
					LOGGER.warn("{} shader program does not use sampler {} defined in the pipeline. This might be a bug.", this.debugLabel, string2);
				}
			} else {
				int n = j++;
				this.uniformsByName.put(string2, new Uniform.Sampler(m, n));
			}
		}

		java.util.List<VulkanicAPI.ActiveUniformBlockInfo> activeUniformBlocks = VulkanicAPI.getActiveUniformBlocks(VulkanicAPI.getCommandContext(), this.programId);

		for (VulkanicAPI.ActiveUniformBlockInfo activeUniformBlock : activeUniformBlocks) {
			String string = activeUniformBlock.name();
			if (!this.uniformsByName.containsKey(string)) {
				int p = activeUniformBlock.index();
				if (!list2.contains(string) && BUILT_IN_UNIFORMS.contains(string)) {
					int n = i++;
					VulkanicAPI.uniformBlockBinding(VulkanicAPI.getCommandContext(), this.programId, p, n);
					this.uniformsByName.put(string, new Uniform.Ubo(n));
				} else if (string.startsWith("iris_")) {
					// Silently skip Iris-injected uniforms
					// These uniforms are managed by Iris's own pipeline
				} else {
					LOGGER.warn("Found unknown and unsupported uniform {} in {}", string, this.debugLabel);
				}
			}
		}
	}

	public void close() {
		this.uniformsByName.values().forEach(Uniform::close);
		VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), this.programId);
	}

	@Nullable
	public Uniform getUniform(String string) {
		RenderSystem.assertOnRenderThread();
		return (Uniform)this.uniformsByName.get(string);
	}

	@VisibleForTesting
	public int getProgramId() {
		return this.programId;
	}

	public String toString() {
		return this.debugLabel;
	}

	public String getDebugLabel() {
		return this.debugLabel;
	}

	public Map<String, Uniform> getUniforms() {
		return this.uniformsByName;
	}
	
	// Iris: Merged from MixinCompiledShaderProgram
	@Override
	public void setShouldSkip(java.lang.invoke.MethodHandle s) {
		shouldSkip = s;
	}
	
	public boolean iris$shouldSkipThis() {
		if (net.irisshaders.iris.Iris.getIrisConfig().shouldAllowUnknownShaders()) {
			if (net.irisshaders.iris.shadows.ShadowRenderer.ACTIVE) return true;

			if (!shouldOverrideShaders()) return false;

			if (shouldSkip == net.irisshaders.iris.compat.SkipList.NONE) return false;
			if (shouldSkip == net.irisshaders.iris.compat.SkipList.ALWAYS) return true;

			try {
				return (boolean) shouldSkip.invoke(this);
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		} else {
			return !(this instanceof net.irisshaders.iris.pipeline.programs.ExtendedShader || this instanceof net.irisshaders.iris.pipeline.programs.FallbackShader || !shouldOverrideShaders());
		}
	}
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean shouldOverrideShaders() {
		net.irisshaders.iris.pipeline.WorldRenderingPipeline pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof net.irisshaders.iris.pipeline.ShaderRenderingPipeline) {
			return ((net.irisshaders.iris.pipeline.ShaderRenderingPipeline) pipeline).shouldOverrideShaders();
		} else {
			return false;
		}
	}
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isKnownShader() {
		return this instanceof net.irisshaders.iris.pipeline.programs.ExtendedShader || this instanceof net.irisshaders.iris.pipeline.programs.FallbackShader;
	}
}
