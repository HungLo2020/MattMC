package net.sodium.client.render.chunk.shader;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.sodium.client.gl.attribute.GlVertexFormat;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.gl.device.RenderDevice;
import net.vulkanic.VulkanicBlendFactor;
import net.vulkanic.VulkanicAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SodiumChunkRenderPipelines {
    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumChunkRenderPipelines.class);
    private static final int BASE_VERTEX_STRIDE = 20;
    private static final List<String> LEGACY_SAMPLER_NAMES = List.of("Sampler0", "Sampler2");
    private static final VertexFormatElement SODIUM_POSITION = registerNextAvailable(
        0,
        VertexFormatElement.Type.UINT,
        VertexFormatElement.Usage.GENERIC,
        2
    );
    private static final VertexFormatElement SODIUM_COLOR = VertexFormatElement.COLOR;
    private static final VertexFormatElement SODIUM_TEXCOORD = registerNextAvailable(
        2,
        VertexFormatElement.Type.USHORT,
        VertexFormatElement.Usage.GENERIC,
        2
    );
    private static final VertexFormatElement SODIUM_LIGHT_AND_DATA = registerNextAvailable(
        3,
        VertexFormatElement.Type.UBYTE,
        VertexFormatElement.Usage.GENERIC,
        4
    );
    private static final VertexFormatElement IRIS_NORMAL = registerNextAvailable(
        10,
        VertexFormatElement.Type.BYTE,
        VertexFormatElement.Usage.GENERIC,
        4
    );
    private static final VertexFormatElement IRIS_BLOCK_ID = registerNextAvailable(
        11,
        VertexFormatElement.Type.UINT,
        VertexFormatElement.Usage.GENERIC,
        1
    );
    private static final VertexFormatElement IRIS_MID_TEX_COORD = registerNextAvailable(
        12,
        VertexFormatElement.Type.USHORT,
        VertexFormatElement.Usage.GENERIC,
        2
    );
    private static final VertexFormatElement IRIS_TANGENT = registerNextAvailable(
        13,
        VertexFormatElement.Type.BYTE,
        VertexFormatElement.Usage.GENERIC,
        4
    );
    private static final VertexFormatElement IRIS_MID_BLOCK = registerNextAvailable(
        14,
        VertexFormatElement.Type.BYTE,
        VertexFormatElement.Usage.GENERIC,
        4
    );
    private static final Map<PipelineKey, RenderPipeline> PIPELINES = new ConcurrentHashMap<>();
    private static volatile int cachedShaderReloadVersion = Integer.MIN_VALUE;

    private SodiumChunkRenderPipelines() {
    }

    public static RenderPipeline forPass(TerrainRenderPass pass, RenderPassChunkShaderInterface shaderInterface) {
        int shaderReloadVersion = Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
        clearStalePipelines(shaderReloadVersion);
        VertexFormat vertexFormat = createVertexFormat(WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat());
        List<String> bindableSamplers = collectSamplerNames(shaderInterface);
        PassState passState = PassState.from(pass.getPipeline());
        PipelineKey key = new PipelineKey(
            shaderReloadVersion,
            net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered(),
            vertexFormat,
            bindableSamplers,
            PassKind.from(pass),
            passState
        );
        return PIPELINES.computeIfAbsent(key, SodiumChunkRenderPipelines::createPipeline);
    }

    private static synchronized void clearStalePipelines(int shaderReloadVersion) {
        if (cachedShaderReloadVersion == shaderReloadVersion) {
            return;
        }

        for (RenderPipeline pipeline : PIPELINES.values()) {
            SharedChunkProgramOverrides.unregister(pipeline);
        }
        PIPELINES.clear();
        cachedShaderReloadVersion = shaderReloadVersion;
    }

    private static RenderPipeline createPipeline(PipelineKey key) {
        LOGGER.info(
            "Creating shared Sodium chunk pipeline reloadVersion={} shadow={} pass={} vertexSize={} state={} samplers={}",
            key.shaderReloadVersion(),
            key.shadowPass(),
            key.passKind(),
            key.vertexFormat().getVertexSize(),
            key.passState(),
            key.samplerNames()
        );
        RenderPipeline.Snippet snippet = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/vulkan_chunk"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/vulkan_chunk"))
            .withUniform("SodiumChunkParams", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(key.vertexFormat(), VertexFormat.Mode.TRIANGLES)
            .withShaderDefine("MAX_TEXTURE_LOD_BIAS", RenderDevice.instance().getMaxTextureLodBias())
            .buildSnippet();

        RenderPipeline.Builder snippetBuilder = RenderPipeline.builder(snippet);
        for (String samplerName : key.samplerNames()) {
            snippetBuilder.withSampler(samplerName);
        }
        snippet = snippetBuilder.buildSnippet();
        String signature = Integer.toUnsignedString(Objects.hash(
            key.vertexFormat(),
            key.samplerNames(),
            key.shadowPass(),
            key.passKind(),
            key.passState()
        ), 36);

        RenderPipeline.Builder builder = RenderPipeline.builder(snippet)
            .withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "pipeline/shared_chunk_" + key.passKind().id() + "_v" + key.shaderReloadVersion() + "_" + signature))
            .withShaderDefine("VULKAN_DISABLE_TERRAIN_FOG")
            .withDepthTestFunction(key.passState().depthTest())
            .withPolygonMode(key.passState().polygonMode())
            .withCull(key.passState().cull())
            .withColorWrite(key.passState().writeColor(), key.passState().writeAlpha())
            .withDepthWrite(key.passState().writeDepth())
            .withDepthBias(key.passState().depthBiasScaleFactor(), key.passState().depthBiasConstant());

        if (key.passKind().fragmentDiscard()) {
            builder.withShaderDefine("USE_FRAGMENT_DISCARD");
        }

        key.passState().blend().ifPresent(builder::withBlend);

        RenderPipeline pipeline = builder.build();

        SharedChunkProgramOverrides.register(pipeline, key.samplerNames());
        VulkanTerrainPipelineDiagnostics.logPipeline(pipeline, key.samplerNames());

        Minecraft minecraft = Minecraft.getInstance();
        VulkanicAPI.precompileRenderPipeline(pipeline, minecraft.getShaderManager()::getShader);

        return pipeline;
    }

    private static VertexFormat createVertexFormat(GlVertexFormat sourceFormat) {
        List<GlVertexAttributeBinding> bindings = new ArrayList<>(Arrays.asList(sourceFormat.getShaderBindings()));
        bindings.sort(Comparator.comparingInt(GlVertexAttributeBinding::getPointer));

        VertexFormat.Builder builder = VertexFormat.builder();
        int offset = 0;
        for (GlVertexAttributeBinding binding : bindings) {
            if (binding.getPointer() > offset) {
                builder.padding(binding.getPointer() - offset);
            }

            builder.add(attributeName(binding.getIndex()), vertexElement(binding));
            offset = binding.getPointer() + binding.getSize();
        }

        int stride = Math.max(sourceFormat.getStride(), BASE_VERTEX_STRIDE);
        if (stride > offset) {
            builder.padding(stride - offset);
        }

        return builder.build();
    }

    private static List<String> collectSamplerNames(RenderPassChunkShaderInterface shaderInterface) {
        LinkedHashSet<String> samplers = new LinkedHashSet<>(LEGACY_SAMPLER_NAMES);
        if (shaderInterface != null) {
            samplers.addAll(shaderInterface.getRenderPassSamplerNames());
        }
        return List.copyOf(samplers);
    }

    private static String attributeName(int location) {
        return switch (location) {
            case ChunkShaderBindingPoints.ATTRIBUTE_POSITION -> "a_Position";
            case ChunkShaderBindingPoints.ATTRIBUTE_COLOR -> "a_Color";
            case ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE -> "a_TexCoord";
            case ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX -> "a_LightAndData";
            case 10 -> "iris_Normal";
            case 11 -> "mc_Entity";
            case 12 -> "mc_midTexCoord";
            case 13 -> "at_tangent";
            case 14 -> "at_midBlock";
            default -> throw new IllegalStateException("Unsupported Sodium chunk attribute location: " + location);
        };
    }

    private static VertexFormatElement vertexElement(GlVertexAttributeBinding binding) {
        return switch (binding.getIndex()) {
            case ChunkShaderBindingPoints.ATTRIBUTE_POSITION -> SODIUM_POSITION;
            case ChunkShaderBindingPoints.ATTRIBUTE_COLOR -> SODIUM_COLOR;
            case ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE -> SODIUM_TEXCOORD;
            case ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX -> SODIUM_LIGHT_AND_DATA;
            case 10 -> IRIS_NORMAL;
            case 11 -> IRIS_BLOCK_ID;
            case 12 -> IRIS_MID_TEX_COORD;
            case 13 -> IRIS_TANGENT;
            case 14 -> IRIS_MID_BLOCK;
            default -> throw new IllegalStateException("Unsupported Sodium chunk attribute binding: " + binding.getIndex());
        };
    }

    private static VertexFormatElement registerNextAvailable(
        int index,
        VertexFormatElement.Type type,
        VertexFormatElement.Usage usage,
        int count
    ) {
        for (int id = 0; id < VertexFormatElement.MAX_COUNT; id++) {
            if (VertexFormatElement.byId(id) == null) {
                return VertexFormatElement.register(id, index, type, usage, count);
            }
        }

        throw new IllegalStateException("No free vertex format element ids remain for Sodium Vulkan chunk attributes");
    }

	private static Optional<BlendFunction> currentBlend(RenderPipeline pipeline) {
		if (!BlendModeStorage.isBlendEnabled()) {
			return Optional.empty();
		}

		Optional<SourceFactor> sourceColor = toSourceFactor(BlendModeStorage.getBlendSrcRgb());
		Optional<DestFactor> destColor = toDestFactor(BlendModeStorage.getBlendDstRgb());
		Optional<SourceFactor> sourceAlpha = toSourceFactor(BlendModeStorage.getBlendSrcAlpha());
		Optional<DestFactor> destAlpha = toDestFactor(BlendModeStorage.getBlendDstAlpha());
		if (sourceColor.isPresent() && destColor.isPresent() && sourceAlpha.isPresent() && destAlpha.isPresent()) {
			return Optional.of(new BlendFunction(sourceColor.get(), destColor.get(), sourceAlpha.get(), destAlpha.get()));
		}

		return pipeline.getBlendFunction();
	}

	private static Optional<SourceFactor> toSourceFactor(int glFactor) {
		return VulkanicBlendFactor.fromLegacyGlConstant(glFactor)
			.map(factor -> SourceFactor.valueOf(factor.name()));
	}

	private static Optional<DestFactor> toDestFactor(int glFactor) {
		return VulkanicBlendFactor.fromLegacyGlConstant(glFactor)
			.flatMap(factor -> {
				try {
					return Optional.of(DestFactor.valueOf(factor.name()));
				} catch (IllegalArgumentException e) {
					return Optional.empty();
				}
			});
	}

    private record PipelineKey(
		int shaderReloadVersion,
		boolean shadowPass,
		VertexFormat vertexFormat,
		List<String> samplerNames,
		PassKind passKind,
		PassState passState
	) {
    }

	private record PassState(
		Optional<BlendFunction> blend,
		DepthTestFunction depthTest,
		PolygonMode polygonMode,
		boolean cull,
		boolean writeColor,
		boolean writeAlpha,
		boolean writeDepth,
		float depthBiasScaleFactor,
		float depthBiasConstant
	) {
		private static PassState from(RenderPipeline pipeline) {
			return new PassState(
				currentBlend(pipeline),
				pipeline.getDepthTestFunction(),
				pipeline.getPolygonMode(),
				// Sodium chunk meshes already encode visible block faces; Vulkan backface culling can remove valid terrain.
				false,
				DepthColorStorage.isRedMaskEnabled() || DepthColorStorage.isGreenMaskEnabled() || DepthColorStorage.isBlueMaskEnabled(),
				DepthColorStorage.isAlphaMaskEnabled(),
				DepthColorStorage.isDepthMaskEnabled(),
				pipeline.getDepthBiasScaleFactor(),
				pipeline.getDepthBiasConstant()
			);
		}
	}

	private enum PassKind {
		SOLID("solid", false),
		CUTOUT("cutout", true),
		TRANSLUCENT("translucent", false);

		private final String id;
		private final boolean fragmentDiscard;

		PassKind(String id, boolean fragmentDiscard) {
			this.id = id;
			this.fragmentDiscard = fragmentDiscard;
		}

		private static PassKind from(TerrainRenderPass pass) {
			if (pass.isTranslucent()) {
				return TRANSLUCENT;
			}
			return pass.supportsFragmentDiscard() ? CUTOUT : SOLID;
		}

		private String id() {
			return this.id;
		}

		private boolean fragmentDiscard() {
			return this.fragmentDiscard;
		}
	}
}
