package net.irisshaders.iris.pipeline.programs;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.gl.shader.ShaderWorkarounds;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.fallback.ShaderSynthesizer;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.VanillaUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicProgramHandle;
import net.vulkanic.VulkanicShaderHandle;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class ShaderCreator {
	public static ShaderSupplier create(WorldRenderingPipeline pipeline, String name, ShaderKey shaderKey, ProgramSource source, ProgramId programId, GlFramebuffer writingToBeforeTranslucent,
										GlFramebuffer writingToAfterTranslucent, AlphaTest fallbackAlpha,
										VertexFormat vertexFormat, ShaderAttributeInputs inputs, FrameUpdateNotifier updateNotifier,
										IrisRenderingPipeline parent, Supplier<ImmutableSet<Integer>> flipped, FogMode fogMode, boolean isIntensity,
										boolean isFullbright, boolean isShadowPass, boolean isLines, CustomUniforms customUniforms) throws IOException {
		AlphaTest alpha = source.getDirectives().getAlphaTestOverride().orElse(fallbackAlpha);
		BlendModeOverride blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(programId.getBlendModeOverride());

		Map<PatchShaderType, String> transformed = TransformPatcher.patchVanilla(
			name,
			source.getVertexSource().orElseThrow(RuntimeException::new),
			source.getGeometrySource().orElse(null),
			source.getTessControlSource().orElse(null),
			source.getTessEvalSource().orElse(null),
			source.getFragmentSource().orElseThrow(RuntimeException::new),
			alpha, isLines, shaderKey == ShaderKey.CLOUDS, true, inputs, pipeline.getTextureMap());
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
		String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);

		String shaderJsonString = String.format("""
			    {
			    "blend": {
			        "func": "add",
			        "srcrgb": "srcalpha",
			        "dstrgb": "1-srcalpha"
			    },
			    "vertex": "%s",
			    "fragment": "%s",
			    "attributes": [
			        "Position",
			        "Color",
			        "UV0",
			        "UV1",
			        "UV2",
			        "Normal"
			    ],
			    "uniforms": [
			        { "name": "iris_TextureMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
			        { "name": "iris_ModelViewMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
			        { "name": "iris_ModelViewMatInverse", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
			        { "name": "iris_ProjMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
			        { "name": "iris_ProjMatInverse", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
			        { "name": "iris_NormalMat", "type": "matrix3x3", "count": 9, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0 ] },
			        { "name": "iris_ModelOffset", "type": "float", "count": 3, "values": [ 0.0, 0.0, 0.0 ] },
			        { "name": "iris_ColorModulator", "type": "float", "count": 4, "values": [ 1.0, 1.0, 1.0, 1.0 ] },
			        { "name": "iris_GlintAlpha", "type": "float", "count": 1, "values": [ 1.0 ] },
			        { "name": "iris_FogStart", "type": "float", "count": 1, "values": [ 0.0 ] },
			        { "name": "iris_FogEnd", "type": "float", "count": 1, "values": [ 1.0 ] },
			        { "name": "iris_FogColor", "type": "float", "count": 4, "values": [ 0.0, 0.0, 0.0, 0.0 ] },
			        {
			                    "name": "iris_OverlayUV",
			                    "type": "int",
			                    "count": 2,
			                    "values": [
			                        0,
			                        0
			                    ]
			                },
			                {
			                    "name": "iris_LightUV",
			                    "type": "int",
			                    "count": 2,
			                    "values": [
			                        0,
			                        0
			                    ]
			                }
			    ]
			}""", name, name);

		ShaderPrinter.printProgram(name).addSources(transformed).addJson(shaderJsonString).print();

		ResourceProvider shaderResourceFactory = new IrisProgramResourceFactory(shaderJsonString, vertex, geometry, tessControl, tessEval, fragment);

		List<BufferBlendOverride> overrides = new ArrayList<>();
		source.getDirectives().getBufferBlendOverrides().forEach(information -> {
			int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
			if (index > -1) {
				overrides.add(new BufferBlendOverride(index, information.blendMode()));
			}
		});

		PartialShader id = link(name, vertex, geometry, tessControl, tessEval, fragment, vertexFormat, false);


		return new ShaderSupplier(shaderKey, id, () -> {
			try {
				return new ExtendedShader(id.getFinally(), name, vertexFormat, tessControl != null || tessEval != null, writingToBeforeTranslucent, writingToAfterTranslucent, blendModeOverride, alpha, uniforms -> {
					CommonUniforms.addDynamicUniforms(uniforms, FogMode.PER_VERTEX);
					customUniforms.assignTo(uniforms);
					BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
					VanillaUniforms.addVanillaUniforms(uniforms);
				}, (samplerHolder, imageHolder) -> {
					parent.addGbufferOrShadowSamplers(samplerHolder, imageHolder, flipped, isShadowPass, inputs.hasTex(), inputs.hasLight(), inputs.hasOverlay());
				}, isIntensity, parent, overrides, customUniforms);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}



	public static PartialShader link(String name, String vertex, String geometry, String tessControl, String tessEval, String fragment, VertexFormat vertexFormat, boolean isFallback) throws ShaderCompileException {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicProgramHandle program = VulkanicAPI.createShaderProgramHandle(ctx);
		int i = program.value();
		if (!program.isValid()) {
			throw new RuntimeException("Could not create shader program (returned program ID " + i + ")");
		} else {
			int vertexS = createShader(ctx, name, ShaderType.VERTEX, vertex);
			int geometryS = createShader(ctx, name, ShaderType.GEOMETRY, geometry);
			int tessContS = createShader(ctx, name, ShaderType.TESSELATION_CONTROL, tessControl);
			int tessEvalS = createShader(ctx, name, ShaderType.TESSELATION_EVAL, tessEval);
			int fragS = createShader(ctx, name, ShaderType.FRAGMENT, fragment);

			attachIfValid(ctx, program, vertexS);
			attachIfValid(ctx, program, geometryS);
			attachIfValid(ctx, program, tessContS);
			attachIfValid(ctx, program, tessEvalS);
			attachIfValid(ctx, program, fragS);

			((VertexFormatExtension) vertexFormat).bindAttributesIris(isFallback, i);

			VulkanicAPI.linkProgram(ctx, program);

			return new PartialShader(i, vertexS, fragS, geometryS, tessContS, tessEvalS);
		}
	}

	private static void attachIfValid(CommandContext ctx, VulkanicProgramHandle program, int s) {
		if (s >= 0) {
			VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(s));
		}
	}

	private static void detachIfValid(CommandContext ctx, VulkanicProgramHandle program, int s) {
		if (s >= 0) {
			VulkanicAPI.detachShader(ctx, program, VulkanicShaderHandle.of(s));
			VulkanicAPI.deleteShader(ctx, VulkanicShaderHandle.of(s));
		}
	}

	private static int createShader(CommandContext ctx, String name, ShaderType shaderType, String source) {
		if (source == null) return -1;

		VulkanicShaderHandle shader = VulkanicAPI.createShaderHandle(ctx, shaderType.stage);
		ShaderWorkarounds.safeShaderSource(shader.value(), source);
		VulkanicAPI.compileShader(ctx, shader);
		String log = VulkanicAPI.getShaderInfoLog(ctx, shader);

		if (!log.isEmpty()) {
			Iris.logger.warn("Shader compilation log for " + name + ": " + log);
		}

		if (!VulkanicAPI.isShaderCompileSuccessful(ctx, shader)) {
			throw new ShaderCompileException(name, log);
		}

		return shader.value();
	}

	public static ShaderSupplier createFallback(String name, ShaderKey shaderKey, GlFramebuffer writingToBeforeTranslucent,
												GlFramebuffer writingToAfterTranslucent, AlphaTest alpha,
												VertexFormat vertexFormat, BlendModeOverride blendModeOverride,
												IrisRenderingPipeline parent, FogMode fogMode, boolean entityLighting,
												boolean isGlint, boolean isText, boolean intensityTex, boolean isFullbright) throws IOException {
		ShaderAttributeInputs inputs = new ShaderAttributeInputs(vertexFormat, isFullbright, false, isGlint, isText, false);

		// TODO: Is this check sound in newer versions?
		boolean isLeash = vertexFormat == DefaultVertexFormat.POSITION_COLOR_LIGHTMAP;
		String vertex = ShaderSynthesizer.vsh(true, inputs, fogMode, entityLighting, isLeash);
		String fragment = ShaderSynthesizer.fsh(inputs, fogMode, alpha, intensityTex, isLeash);

		ShaderPrinter.printProgram(name)
			.addSource(PatchShaderType.VERTEX, vertex)
			.addSource(PatchShaderType.FRAGMENT, fragment)
			.print();


		PartialShader id = link(name, vertex, null, null, null, fragment, vertexFormat, true);

		// TODO 24w34a FALLBACK
		return new ShaderSupplier(shaderKey, id, () -> {
			try {
				GLDebug.nameObject(VulkanicAPI.GL_PROGRAM, id.program(), name + "_fallback");

				// TODO 1.21.5 (oh no)
				return new FallbackShader(id.getFinally(), RenderPipelines.ENTITY_CUTOUT, name, vertexFormat, writingToBeforeTranslucent,
					writingToAfterTranslucent, blendModeOverride, alpha.reference(), parent);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static ShaderSupplier createFallbackShadow(String name, ShaderKey shaderKey, Supplier<ShadowRenderTargets> shadowSupplier, AlphaTest alpha,
												VertexFormat vertexFormat, BlendModeOverride blendModeOverride,
												IrisRenderingPipeline parent, FogMode fogMode, boolean entityLighting,
												boolean isGlint, boolean isText, boolean intensityTex, boolean isFullbright) throws IOException {
		ShaderAttributeInputs inputs = new ShaderAttributeInputs(vertexFormat, isFullbright, false, isGlint, isText, false);

		// TODO: Is this check sound in newer versions?
		boolean isLeash = vertexFormat == DefaultVertexFormat.POSITION_COLOR_LIGHTMAP;
		String vertex = ShaderSynthesizer.vsh(true, inputs, fogMode, entityLighting, isLeash);
		String fragment = ShaderSynthesizer.fsh(inputs, fogMode, alpha, intensityTex, isLeash);

		ShaderPrinter.printProgram(name)
			.addSource(PatchShaderType.VERTEX, vertex)
			.addSource(PatchShaderType.FRAGMENT, fragment)
			.print();

		PartialShader id = link(name, vertex, null, null, null, fragment, vertexFormat, true);

		// TODO 24w34a FALLBACK
		return new ShaderSupplier(shaderKey, id, () -> {
			try {
				// TODO: Fix
				GlFramebuffer framebuffer = shadowSupplier.get().createShadowFramebuffer(ImmutableSet.of(), new int[]{0});
				return new FallbackShader(id.getFinally(), RenderPipelines.ENTITY_CUTOUT, name, vertexFormat, framebuffer, framebuffer, blendModeOverride, alpha.reference(), parent);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static ShaderSupplier createShadow(WorldRenderingPipeline pipeline, String name, ShaderKey shaderKey, ProgramSource source, ProgramId programId, Supplier<ShadowRenderTargets> shadowSupplier, AlphaTest fallbackAlpha,
											  VertexFormat vertexFormat, ShaderAttributeInputs inputs, FrameUpdateNotifier updateNotifier,
											  IrisRenderingPipeline parent, Supplier<ImmutableSet<Integer>> flipped, FogMode fogMode, boolean isIntensity,
											  boolean isFullbright, boolean isShadowPass, boolean isLines, CustomUniforms customUniforms) throws IOException {
		AlphaTest alpha = source.getDirectives().getAlphaTestOverride().orElse(fallbackAlpha);
		BlendModeOverride blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(programId.getBlendModeOverride());

		Map<PatchShaderType, String> transformed = TransformPatcher.patchVanilla(
			name,
			source.getVertexSource().orElseThrow(RuntimeException::new),
			source.getGeometrySource().orElse(null),
			source.getTessControlSource().orElse(null),
			source.getTessEvalSource().orElse(null),
			source.getFragmentSource().orElseThrow(RuntimeException::new),
			alpha, isLines, shaderKey == ShaderKey.CLOUDS, true, inputs, pipeline.getTextureMap());
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
		String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);

		ShaderPrinter.printProgram(name).addSources(transformed).print();

		ResourceProvider shaderResourceFactory = new IrisProgramResourceFactory("", vertex, geometry, tessControl, tessEval, fragment);

		List<BufferBlendOverride> overrides = new ArrayList<>();
		source.getDirectives().getBufferBlendOverrides().forEach(information -> {
			int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
			if (index > -1) {
				overrides.add(new BufferBlendOverride(index, information.blendMode()));
			}
		});

		PartialShader id = link(name, vertex, geometry, tessControl, tessEval, fragment, vertexFormat, false);


		return new ShaderSupplier(shaderKey, id, () -> {
			GlFramebuffer framebuffer = shadowSupplier.get().createShadowFramebuffer(ImmutableSet.of(), source.getDirectives().hasUnknownDrawBuffers() ? new int[]{0, 1} : source.getDirectives().getDrawBuffers());
			try {
				return new ExtendedShader(id.getFinally(), name, vertexFormat, tessControl != null || tessEval != null, framebuffer, framebuffer, blendModeOverride, alpha, uniforms -> {
					CommonUniforms.addDynamicUniforms(uniforms, FogMode.PER_VERTEX);
					customUniforms.assignTo(uniforms);
					BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
					VanillaUniforms.addVanillaUniforms(uniforms);
				}, (samplerHolder, imageHolder) -> {
					parent.addGbufferOrShadowSamplers(samplerHolder, imageHolder, flipped, isShadowPass, inputs.hasTex(), inputs.hasLight(), inputs.hasOverlay());
				}, isIntensity, parent, overrides, customUniforms);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private record IrisProgramResourceFactory(String json, String vertex, String geometry, String tessControl,
											  String tessEval, String fragment) implements ResourceProvider {

		@Override
		public Optional<Resource> getResource(ResourceLocation id) {
			final String path = id.getPath();

			if (path.endsWith("json")) {
				return Optional.of(new StringResource(id, json));
			} else if (path.endsWith("vsh")) {
				return Optional.of(new StringResource(id, vertex));
			} else if (path.endsWith("gsh")) {
				if (geometry == null) {
					return Optional.empty();
				}
				return Optional.of(new StringResource(id, geometry));
			} else if (path.endsWith("tcs")) {
				if (tessControl == null) {
					return Optional.empty();
				}
				return Optional.of(new StringResource(id, tessControl));
			} else if (path.endsWith("tes")) {
				if (tessEval == null) {
					return Optional.empty();
				}
				return Optional.of(new StringResource(id, tessEval));
			} else if (path.endsWith("fsh")) {
				return Optional.of(new StringResource(id, fragment));
			}

			return Optional.empty();
		}
	}

	private static class StringResource extends Resource {
		private final String content;

		private StringResource(ResourceLocation id, String content) {
			super(new PathPackResources(new PackLocationInfo("<iris shaderpack shaders>", Component.literal("iris"), PackSource.BUILT_IN, Optional.of(new KnownPack("iris", "shader", "1.0"))), IrisPlatformHelpers.getInstance().getConfigDir()), () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            this.content = content;
		}

		@Override
		public InputStream open() {
			return IOUtils.toInputStream(content, StandardCharsets.UTF_8);
		}
	}
}
