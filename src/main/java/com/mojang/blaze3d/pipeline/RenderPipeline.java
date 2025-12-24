package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public class RenderPipeline {
	private final ResourceLocation location;
	private final ResourceLocation vertexShader;
	private final ResourceLocation fragmentShader;
	private final ShaderDefines shaderDefines;
	private final List<String> samplers;
	private final List<RenderPipeline.UniformDescription> uniforms;
	private final DepthTestFunction depthTestFunction;
	private final PolygonMode polygonMode;
	private final boolean cull;
	private final LogicOp colorLogic;
	private final Optional<BlendFunction> blendFunction;
	private final boolean writeColor;
	private final boolean writeAlpha;
	private final boolean writeDepth;
	private final VertexFormat vertexFormat;
	private final VertexFormat.Mode vertexFormatMode;
	private final float depthBiasScaleFactor;
	private final float depthBiasConstant;
	private final int sortKey;
	private static int sortKeySeed;

	protected RenderPipeline(
		ResourceLocation resourceLocation,
		ResourceLocation resourceLocation2,
		ResourceLocation resourceLocation3,
		ShaderDefines shaderDefines,
		List<String> list,
		List<RenderPipeline.UniformDescription> list2,
		Optional<BlendFunction> optional,
		DepthTestFunction depthTestFunction,
		PolygonMode polygonMode,
		boolean bl,
		boolean bl2,
		boolean bl3,
		boolean bl4,
		LogicOp logicOp,
		VertexFormat vertexFormat,
		VertexFormat.Mode mode,
		float f,
		float g,
		int i
	) {
		this.location = resourceLocation;
		this.vertexShader = resourceLocation2;
		this.fragmentShader = resourceLocation3;
		this.shaderDefines = shaderDefines;
		this.samplers = list;
		this.uniforms = list2;
		this.depthTestFunction = depthTestFunction;
		this.polygonMode = polygonMode;
		this.cull = bl;
		this.blendFunction = optional;
		this.writeColor = bl2;
		this.writeAlpha = bl3;
		this.writeDepth = bl4;
		this.colorLogic = logicOp;
		this.vertexFormat = vertexFormat;
		this.vertexFormatMode = mode;
		this.depthBiasScaleFactor = f;
		this.depthBiasConstant = g;
		this.sortKey = i;
	}

	public int getSortKey() {
		return SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER ? super.hashCode() * (sortKeySeed + 1) : this.sortKey;
	}

	public static void updateSortKeySeed() {
		sortKeySeed = Math.round(100000.0F * (float)Math.random());
	}

	public String toString() {
		return this.location.toString();
	}

	public DepthTestFunction getDepthTestFunction() {
		return this.depthTestFunction;
	}

	public PolygonMode getPolygonMode() {
		return this.polygonMode;
	}

	public boolean isCull() {
		return this.cull;
	}

	public LogicOp getColorLogic() {
		return this.colorLogic;
	}

	public Optional<BlendFunction> getBlendFunction() {
		return this.blendFunction;
	}

	public boolean isWriteColor() {
		return this.writeColor;
	}

	public boolean isWriteAlpha() {
		return this.writeAlpha;
	}

	public boolean isWriteDepth() {
		return this.writeDepth;
	}

	public float getDepthBiasScaleFactor() {
		return this.depthBiasScaleFactor;
	}

	public float getDepthBiasConstant() {
		return this.depthBiasConstant;
	}

	public ResourceLocation getLocation() {
		return this.location;
	}

	public VertexFormat getVertexFormat() {
		return this.vertexFormat;
	}

	public VertexFormat.Mode getVertexFormatMode() {
		return this.vertexFormatMode;
	}

	public ResourceLocation getVertexShader() {
		return this.vertexShader;
	}

	public ResourceLocation getFragmentShader() {
		return this.fragmentShader;
	}

	public ShaderDefines getShaderDefines() {
		return this.shaderDefines;
	}

	public List<String> getSamplers() {
		return this.samplers;
	}

	public List<RenderPipeline.UniformDescription> getUniforms() {
		return this.uniforms;
	}

	public boolean wantsDepthTexture() {
		return this.depthTestFunction != DepthTestFunction.NO_DEPTH_TEST || this.depthBiasConstant != 0.0F || this.depthBiasScaleFactor != 0.0F || this.writeDepth;
	}

	public static RenderPipeline.Builder builder(RenderPipeline.Snippet... snippets) {
		RenderPipeline.Builder builder = new RenderPipeline.Builder();

		for (RenderPipeline.Snippet snippet : snippets) {
			builder.withSnippet(snippet);
		}

		return builder;
	}

	@Environment(EnvType.CLIENT)
	@DontObfuscate
	public static class Builder {
		private static int nextPipelineSortKey;
		private Optional<ResourceLocation> location = Optional.empty();
		private Optional<ResourceLocation> fragmentShader = Optional.empty();
		private Optional<ResourceLocation> vertexShader = Optional.empty();
		private Optional<ShaderDefines.Builder> definesBuilder = Optional.empty();
		private Optional<List<String>> samplers = Optional.empty();
		private Optional<List<RenderPipeline.UniformDescription>> uniforms = Optional.empty();
		private Optional<DepthTestFunction> depthTestFunction = Optional.empty();
		private Optional<PolygonMode> polygonMode = Optional.empty();
		private Optional<Boolean> cull = Optional.empty();
		private Optional<Boolean> writeColor = Optional.empty();
		private Optional<Boolean> writeAlpha = Optional.empty();
		private Optional<Boolean> writeDepth = Optional.empty();
		private Optional<LogicOp> colorLogic = Optional.empty();
		private Optional<BlendFunction> blendFunction = Optional.empty();
		private Optional<VertexFormat> vertexFormat = Optional.empty();
		private Optional<VertexFormat.Mode> vertexFormatMode = Optional.empty();
		private float depthBiasScaleFactor;
		private float depthBiasConstant;

		Builder() {
		}

		public RenderPipeline.Builder withLocation(String string) {
			this.location = Optional.of(ResourceLocation.withDefaultNamespace(string));
			return this;
		}

		public RenderPipeline.Builder withLocation(ResourceLocation resourceLocation) {
			this.location = Optional.of(resourceLocation);
			return this;
		}

		public RenderPipeline.Builder withFragmentShader(String string) {
			this.fragmentShader = Optional.of(ResourceLocation.withDefaultNamespace(string));
			return this;
		}

		public RenderPipeline.Builder withFragmentShader(ResourceLocation resourceLocation) {
			this.fragmentShader = Optional.of(resourceLocation);
			return this;
		}

		public RenderPipeline.Builder withVertexShader(String string) {
			this.vertexShader = Optional.of(ResourceLocation.withDefaultNamespace(string));
			return this;
		}

		public RenderPipeline.Builder withVertexShader(ResourceLocation resourceLocation) {
			this.vertexShader = Optional.of(resourceLocation);
			return this;
		}

		public RenderPipeline.Builder withShaderDefine(String string) {
			if (this.definesBuilder.isEmpty()) {
				this.definesBuilder = Optional.of(ShaderDefines.builder());
			}

			((ShaderDefines.Builder)this.definesBuilder.get()).define(string);
			return this;
		}

		public RenderPipeline.Builder withShaderDefine(String string, int i) {
			if (this.definesBuilder.isEmpty()) {
				this.definesBuilder = Optional.of(ShaderDefines.builder());
			}

			((ShaderDefines.Builder)this.definesBuilder.get()).define(string, i);
			return this;
		}

		public RenderPipeline.Builder withShaderDefine(String string, float f) {
			if (this.definesBuilder.isEmpty()) {
				this.definesBuilder = Optional.of(ShaderDefines.builder());
			}

			((ShaderDefines.Builder)this.definesBuilder.get()).define(string, f);
			return this;
		}

		public RenderPipeline.Builder withSampler(String string) {
			if (this.samplers.isEmpty()) {
				this.samplers = Optional.of(new ArrayList());
			}

			((List)this.samplers.get()).add(string);
			return this;
		}

		public RenderPipeline.Builder withUniform(String string, UniformType uniformType) {
			if (this.uniforms.isEmpty()) {
				this.uniforms = Optional.of(new ArrayList());
			}

			if (uniformType == UniformType.TEXEL_BUFFER) {
				throw new IllegalArgumentException("Cannot use texel buffer without specifying texture format");
			} else {
				((List)this.uniforms.get()).add(new RenderPipeline.UniformDescription(string, uniformType));
				return this;
			}
		}

		public RenderPipeline.Builder withUniform(String string, UniformType uniformType, TextureFormat textureFormat) {
			if (this.uniforms.isEmpty()) {
				this.uniforms = Optional.of(new ArrayList());
			}

			if (uniformType != UniformType.TEXEL_BUFFER) {
				throw new IllegalArgumentException("Only texel buffer can specify texture format");
			} else {
				((List)this.uniforms.get()).add(new RenderPipeline.UniformDescription(string, textureFormat));
				return this;
			}
		}

		public RenderPipeline.Builder withDepthTestFunction(DepthTestFunction depthTestFunction) {
			this.depthTestFunction = Optional.of(depthTestFunction);
			return this;
		}

		public RenderPipeline.Builder withPolygonMode(PolygonMode polygonMode) {
			this.polygonMode = Optional.of(polygonMode);
			return this;
		}

		public RenderPipeline.Builder withCull(boolean bl) {
			this.cull = Optional.of(bl);
			return this;
		}

		public RenderPipeline.Builder withBlend(BlendFunction blendFunction) {
			this.blendFunction = Optional.of(blendFunction);
			return this;
		}

		public RenderPipeline.Builder withoutBlend() {
			this.blendFunction = Optional.empty();
			return this;
		}

		public RenderPipeline.Builder withColorWrite(boolean bl) {
			this.writeColor = Optional.of(bl);
			this.writeAlpha = Optional.of(bl);
			return this;
		}

		public RenderPipeline.Builder withColorWrite(boolean bl, boolean bl2) {
			this.writeColor = Optional.of(bl);
			this.writeAlpha = Optional.of(bl2);
			return this;
		}

		public RenderPipeline.Builder withDepthWrite(boolean bl) {
			this.writeDepth = Optional.of(bl);
			return this;
		}

		@Deprecated
		public RenderPipeline.Builder withColorLogic(LogicOp logicOp) {
			this.colorLogic = Optional.of(logicOp);
			return this;
		}

		public RenderPipeline.Builder withVertexFormat(VertexFormat vertexFormat, VertexFormat.Mode mode) {
			this.vertexFormat = Optional.of(vertexFormat);
			this.vertexFormatMode = Optional.of(mode);
			return this;
		}

		public RenderPipeline.Builder withDepthBias(float f, float g) {
			this.depthBiasScaleFactor = f;
			this.depthBiasConstant = g;
			return this;
		}

		void withSnippet(RenderPipeline.Snippet snippet) {
			if (snippet.vertexShader.isPresent()) {
				this.vertexShader = snippet.vertexShader;
			}

			if (snippet.fragmentShader.isPresent()) {
				this.fragmentShader = snippet.fragmentShader;
			}

			if (snippet.shaderDefines.isPresent()) {
				if (this.definesBuilder.isEmpty()) {
					this.definesBuilder = Optional.of(ShaderDefines.builder());
				}

				ShaderDefines shaderDefines = (ShaderDefines)snippet.shaderDefines.get();

				for (Entry<String, String> entry : shaderDefines.values().entrySet()) {
					((ShaderDefines.Builder)this.definesBuilder.get()).define((String)entry.getKey(), (String)entry.getValue());
				}

				for (String string : shaderDefines.flags()) {
					((ShaderDefines.Builder)this.definesBuilder.get()).define(string);
				}
			}

			snippet.samplers.ifPresent(list -> {
				if (this.samplers.isPresent()) {
					((List)this.samplers.get()).addAll(list);
				} else {
					this.samplers = Optional.of(new ArrayList(list));
				}
			});
			snippet.uniforms.ifPresent(list -> {
				if (this.uniforms.isPresent()) {
					((List)this.uniforms.get()).addAll(list);
				} else {
					this.uniforms = Optional.of(new ArrayList(list));
				}
			});
			if (snippet.depthTestFunction.isPresent()) {
				this.depthTestFunction = snippet.depthTestFunction;
			}

			if (snippet.cull.isPresent()) {
				this.cull = snippet.cull;
			}

			if (snippet.writeColor.isPresent()) {
				this.writeColor = snippet.writeColor;
			}

			if (snippet.writeAlpha.isPresent()) {
				this.writeAlpha = snippet.writeAlpha;
			}

			if (snippet.writeDepth.isPresent()) {
				this.writeDepth = snippet.writeDepth;
			}

			if (snippet.colorLogic.isPresent()) {
				this.colorLogic = snippet.colorLogic;
			}

			if (snippet.blendFunction.isPresent()) {
				this.blendFunction = snippet.blendFunction;
			}

			if (snippet.vertexFormat.isPresent()) {
				this.vertexFormat = snippet.vertexFormat;
			}

			if (snippet.vertexFormatMode.isPresent()) {
				this.vertexFormatMode = snippet.vertexFormatMode;
			}
		}

		public RenderPipeline.Snippet buildSnippet() {
			return new RenderPipeline.Snippet(
				this.vertexShader,
				this.fragmentShader,
				this.definesBuilder.map(ShaderDefines.Builder::build),
				this.samplers.map(Collections::unmodifiableList),
				this.uniforms.map(Collections::unmodifiableList),
				this.blendFunction,
				this.depthTestFunction,
				this.polygonMode,
				this.cull,
				this.writeColor,
				this.writeAlpha,
				this.writeDepth,
				this.colorLogic,
				this.vertexFormat,
				this.vertexFormatMode
			);
		}

		public RenderPipeline build() {
			if (this.location.isEmpty()) {
				throw new IllegalStateException("Missing location");
			} else if (this.vertexShader.isEmpty()) {
				throw new IllegalStateException("Missing vertex shader");
			} else if (this.fragmentShader.isEmpty()) {
				throw new IllegalStateException("Missing fragment shader");
			} else if (this.vertexFormat.isEmpty()) {
				throw new IllegalStateException("Missing vertex buffer format");
			} else if (this.vertexFormatMode.isEmpty()) {
				throw new IllegalStateException("Missing vertex mode");
			} else {
				return new RenderPipeline(
					(ResourceLocation)this.location.get(),
					(ResourceLocation)this.vertexShader.get(),
					(ResourceLocation)this.fragmentShader.get(),
					((ShaderDefines.Builder)this.definesBuilder.orElse(ShaderDefines.builder())).build(),
					List.copyOf((Collection)this.samplers.orElse(new ArrayList())),
					(List<RenderPipeline.UniformDescription>)this.uniforms.orElse(Collections.emptyList()),
					this.blendFunction,
					(DepthTestFunction)this.depthTestFunction.orElse(DepthTestFunction.LEQUAL_DEPTH_TEST),
					(PolygonMode)this.polygonMode.orElse(PolygonMode.FILL),
					(Boolean)this.cull.orElse(true),
					(Boolean)this.writeColor.orElse(true),
					(Boolean)this.writeAlpha.orElse(true),
					(Boolean)this.writeDepth.orElse(true),
					(LogicOp)this.colorLogic.orElse(LogicOp.NONE),
					(VertexFormat)this.vertexFormat.get(),
					(VertexFormat.Mode)this.vertexFormatMode.get(),
					this.depthBiasScaleFactor,
					this.depthBiasConstant,
					nextPipelineSortKey++
				);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	@DontObfuscate
	public record Snippet(
		Optional<ResourceLocation> vertexShader,
		Optional<ResourceLocation> fragmentShader,
		Optional<ShaderDefines> shaderDefines,
		Optional<List<String>> samplers,
		Optional<List<RenderPipeline.UniformDescription>> uniforms,
		Optional<BlendFunction> blendFunction,
		Optional<DepthTestFunction> depthTestFunction,
		Optional<PolygonMode> polygonMode,
		Optional<Boolean> cull,
		Optional<Boolean> writeColor,
		Optional<Boolean> writeAlpha,
		Optional<Boolean> writeDepth,
		Optional<LogicOp> colorLogic,
		Optional<VertexFormat> vertexFormat,
		Optional<VertexFormat.Mode> vertexFormatMode
	) {
	}

	@Environment(EnvType.CLIENT)
	@DontObfuscate
	public record UniformDescription(String name, UniformType type, @Nullable TextureFormat textureFormat) {
		public UniformDescription(String string, UniformType uniformType) {
			this(string, uniformType, null);
			if (uniformType == UniformType.TEXEL_BUFFER) {
				throw new IllegalArgumentException("Texel buffer needs a texture format");
			}
		}

		public UniformDescription(String string, TextureFormat textureFormat) {
			this(string, UniformType.TEXEL_BUFFER, textureFormat);
		}
	}
}
