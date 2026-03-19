package net.sodium.client.render.chunk.shader;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.gl.device.RenderDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SodiumChunkRenderPipelines {
    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumChunkRenderPipelines.class);
    private static final int BASE_VERTEX_STRIDE = 20;
    private static final VertexFormatElement SODIUM_POSITION = registerNextAvailable(
        0,
        VertexFormatElement.Type.UINT,
        VertexFormatElement.Usage.GENERIC,
        2
    );
    private static final VertexFormatElement SODIUM_COLOR = VertexFormatElement.COLOR;
    private static final VertexFormatElement SODIUM_TEXCOORD = registerNextAvailable(
        1,
        VertexFormatElement.Type.USHORT,
        VertexFormatElement.Usage.GENERIC,
        2
    );
    private static final VertexFormatElement SODIUM_LIGHT_AND_DATA = registerNextAvailable(
        2,
        VertexFormatElement.Type.UBYTE,
        VertexFormatElement.Usage.GENERIC,
        4
    );
    private static final Map<Integer, Pipelines> PIPELINES_BY_STRIDE = new ConcurrentHashMap<>();

    private SodiumChunkRenderPipelines() {
    }

    public static RenderPipeline forPass(TerrainRenderPass pass) {
        Pipelines pipelines = PIPELINES_BY_STRIDE.computeIfAbsent(getActiveVertexStride(), SodiumChunkRenderPipelines::createPipelines);

        if (pass.isTranslucent()) {
            return pipelines.translucent();
        }

        if (pass.supportsFragmentDiscard()) {
            return pipelines.cutout();
        }

        return pipelines.solid();
    }

    private static int getActiveVertexStride() {
        int stride = WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat().getStride();
        return Math.max(stride, BASE_VERTEX_STRIDE);
    }

    private static Pipelines createPipelines(int stride) {
        LOGGER.info("Creating Sodium Vulkan chunk pipelines for vertex stride {}", stride);
        VertexFormat vertexFormat = createVertexFormat(stride);
        RenderPipeline.Snippet snippet = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/vulkan_chunk"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/vulkan_chunk"))
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withUniform("SodiumChunkParams", UniformType.UNIFORM_BUFFER)
            .withUniform("SodiumChunkRegion", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(vertexFormat, VertexFormat.Mode.TRIANGLES)
            .withShaderDefine("MAX_TEXTURE_LOD_BIAS", RenderDevice.INSTANCE.getMaxTextureLodBias())
            .buildSnippet();

        return new Pipelines(
            RenderPipeline.builder(snippet)
                .withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "pipeline/vulkan_chunk_solid_stride_" + stride))
                .build(),
            RenderPipeline.builder(snippet)
                .withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "pipeline/vulkan_chunk_cutout_stride_" + stride))
                .withShaderDefine("USE_FRAGMENT_DISCARD")
                .build(),
            RenderPipeline.builder(snippet)
                .withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "pipeline/vulkan_chunk_translucent_stride_" + stride))
                .withBlend(BlendFunction.TRANSLUCENT)
                .build()
        );
    }

    private static VertexFormat createVertexFormat(int stride) {
        VertexFormat.Builder builder = VertexFormat.builder()
            .add("a_Position", SODIUM_POSITION)
            .add("a_Color", SODIUM_COLOR)
            .add("a_TexCoord", SODIUM_TEXCOORD)
            .add("a_LightAndData", SODIUM_LIGHT_AND_DATA);

        if (stride > BASE_VERTEX_STRIDE) {
            builder.padding(stride - BASE_VERTEX_STRIDE);
        }

        return builder.build();
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

    private record Pipelines(RenderPipeline solid, RenderPipeline cutout, RenderPipeline translucent) {
    }
}