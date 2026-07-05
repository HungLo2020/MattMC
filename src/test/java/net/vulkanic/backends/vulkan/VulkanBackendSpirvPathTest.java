package net.vulkanic.backends.vulkan;

import net.vulkanic.CommandContext;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIntegerQuery;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import net.vulkanic.VulkanicUniformReflectionType;
import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanBackendSpirvPathTest {

    private static final class TestRenderPipeline extends RenderPipeline {
        private final VertexFormat vertexFormat;

        private TestRenderPipeline(ResourceLocation location, VertexFormat vertexFormat, VertexFormat.Mode mode) {
            this(location, vertexFormat, mode, List.of(), List.of());
        }

        private TestRenderPipeline(
            ResourceLocation location,
            VertexFormat vertexFormat,
            VertexFormat.Mode mode,
            List<String> samplers,
            List<RenderPipeline.UniformDescription> uniforms
        ) {
            super(
                location,
                ResourceLocation.withDefaultNamespace("core/particle"),
                ResourceLocation.withDefaultNamespace("core/particle"),
                ShaderDefines.builder().build(),
                samplers,
                uniforms,
                Optional.<BlendFunction>empty(),
                DepthTestFunction.LEQUAL_DEPTH_TEST,
                PolygonMode.FILL,
                true,
                true,
                true,
                true,
                LogicOp.NONE,
                vertexFormat,
                mode,
                0.0f,
                0.0f,
                0
            );
            this.vertexFormat = vertexFormat;
        }

        @Override
        public VertexFormat getVertexFormat() {
            return this.vertexFormat;
        }
    }

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    private static final CommandContext TEST_CONTEXT = new CommandContext() {
        @Override
        public boolean isImmediate() {
            return true;
        }

        @Override
        public long getHandle() {
            return 0L;
        }

        @Override
        public String getDebugName() {
            return "vulkan-test";
        }
    };

    @Test
    public void testCompileShaderBuildsSpirvModuleUsingInjectedCompiler() {
        AtomicReference<String> capturedSource = new AtomicReference<>();

        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            capturedSource.set(source.toString());
            return new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x01, 0x02, 0x03, 0x04}, sourceName, "stub");
        });

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        uploadSource(backend, shader, "#version 450\nvoid main(){}");
        backend.compileShader(TEST_CONTEXT, shader);

        assertEquals(VulkanicAPI.GL_TRUE,
            backend.getShaderParameter(TEST_CONTEXT, shader, VulkanicAPI.GL_COMPILE_STATUS));
        assertEquals("#version 450\n#define VULKANIC_BACKEND 1\nvoid main(){}", capturedSource.get());

        Optional<VulkanicSpirvModule> module = backend.getCompiledSpirvModule(TEST_CONTEXT, shader);
        assertTrue(module.isPresent());
        assertEquals(VulkanicShaderStage.VERTEX, module.get().stage());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, module.get().spirvBytes());
    }

    @Test
    public void testCompileShaderNormalizesLegacyOpenGlVertexBuiltinsForVulkan() {
        AtomicReference<String> capturedSource = new AtomicReference<>();

        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            capturedSource.set(source.toString());
            return new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x05, 0x06}, sourceName, "stub");
        });

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        uploadSource(
            backend,
            shader,
            "#version 450\nvoid main(){int a = gl_VertexID; int b = gl_InstanceID; gl_Position = vec4(float(a + b));}"
        );
        backend.compileShader(TEST_CONTEXT, shader);

        assertTrue(capturedSource.get().contains("gl_VertexIndex"));
        assertTrue(capturedSource.get().contains("gl_InstanceIndex"));
        assertFalse(capturedSource.get().contains("gl_VertexID"));
        assertFalse(capturedSource.get().contains("gl_InstanceID"));
    }

    @Test
    public void testNormalizeForVulkanLeavesFragmentShadersUntouched() {
        String source = "#version 450\nvoid main(){int a = gl_VertexID; int b = gl_InstanceID;}";

        assertEquals(
            "#version 450\n#define VULKANIC_BACKEND 1\nvoid main(){int a = gl_VertexID; int b = gl_InstanceID;}",
            GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source)
        );
    }

    @Test
    public void testNormalizeForVulkanInjectsBackendDefineWhenShaderReferencesConditional() {
        String source = "#version 150 core\n"
            + "#ifdef VULKANIC_BACKEND\n"
            + "vec2 uv = vec2(1.0);\n"
            + "#endif\n"
            + "void main(){}";

        assertEquals(
            "#version 450 core\n"
                + "#define VULKANIC_BACKEND 1\n"
                + "#ifdef VULKANIC_BACKEND\n"
                + "vec2 uv = vec2(1.0);\n"
                + "#endif\n"
                + "void main(){}",
            GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source)
        );
    }

    @Test
    public void testNormalizeForVulkanOwnsLightmapSkyCoordinateOrientation() {
        String source = "#version 330\n"
            + "layout(std140) uniform LightmapInfo {\n"
            + "    float AmbientLightFactor;\n"
            + "    float SkyFactor;\n"
            + "    float BlockFactor;\n"
            + "} lightmapInfo;\n"
            + "in vec2 texCoord;\n"
            + "out vec4 fragColor;\n"
            + "float get_brightness(float level) { return level; }\n"
            + "void main(){\n"
            + "    float sky_brightness = get_brightness(floor(texCoord.y * 16) / 15) * lightmapInfo.SkyFactor;\n"
            + "    fragColor = vec4(vec3(sky_brightness), 1.0);\n"
            + "}";

        String lightmap = GlslangSpirvCompiler.normalizeForVulkan(
            VulkanicShaderStage.FRAGMENT,
            source,
            "minecraft:core/lightmap"
        );
        String generic = GlslangSpirvCompiler.normalizeForVulkan(
            VulkanicShaderStage.FRAGMENT,
            source,
            "minecraft:core/generic"
        );

        assertTrue(lightmap.contains("floor((1.0 - texCoord.y) * 16) / 15"),
            "Vulkan lightmap compilation should own the offscreen row-orientation compensation");
        assertFalse(generic.contains("1.0 - texCoord.y"),
            "The lightmap row-orientation rewrite must not affect unrelated fragment shaders");
    }

    @Test
    public void testFeedbackLoopCapabilityRequiresActualImageUsageSupport() throws Exception {
        Method capability = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine")
            .getDeclaredMethod("isFeedbackLoopCapableImageUsage", int.class);
        capability.setAccessible(true);

        int sampledStorageOnly = VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
            | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
            | VK10.VK_IMAGE_USAGE_SAMPLED_BIT
            | VK10.VK_IMAGE_USAGE_STORAGE_BIT;
        int feedbackOnlyWithoutAttachment = sampledStorageOnly
            | EXTAttachmentFeedbackLoopLayout.VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
        int colorAttachmentFeedback = sampledStorageOnly
            | VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            | EXTAttachmentFeedbackLoopLayout.VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
        int depthAttachmentFeedback = VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
            | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
            | VK10.VK_IMAGE_USAGE_SAMPLED_BIT
            | VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
            | EXTAttachmentFeedbackLoopLayout.VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;

        assertFalse((Boolean) capability.invoke(null, sampledStorageOnly),
            "Sampled/storage-only images, including legacy 3D textures, cannot use attachment feedback-loop layouts");
        assertFalse((Boolean) capability.invoke(null, feedbackOnlyWithoutAttachment),
            "The feedback-loop usage bit is not sufficient without color/depth attachment usage");
        assertTrue((Boolean) capability.invoke(null, colorAttachmentFeedback),
            "Color attachments created as sampled feedback-loop images may use feedback-loop layouts");
        assertTrue((Boolean) capability.invoke(null, depthAttachmentFeedback),
            "Depth attachments created as sampled feedback-loop images may use feedback-loop layouts");
    }

    @Test
    public void testNormalizeForVulkanRewritesFragmentCoordScreenMathToLowerLeft() {
        String source = "#version 330\n"
            + "uniform float viewHeight;\n"
            + "uniform float viewWidth;\n"
            + "out vec4 fragColor;\n"
            + "float Bayer64(vec2 c) { return c.y; }"
            + "void main(){"
            + "vec3 screenPos = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z);"
            + "float dither = Bayer64(gl_FragCoord.xy);"
            + "float scalarY = gl_FragCoord.y;"
            + "fragColor = vec4(screenPos.xy, dither + scalarY, gl_FragCoord.w);"
            + "}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("float viewHeight;"));
        assertTrue(normalized.contains(
            "vec3 screenPos = vec3((vec2(gl_FragCoord.x, viewHeight - gl_FragCoord.y) / vec2(viewWidth, viewHeight)), gl_FragCoord.z);"
        ));
        assertTrue(normalized.contains("float dither = Bayer64(vec2(gl_FragCoord.x, viewHeight - gl_FragCoord.y));"));
        assertTrue(normalized.contains("float scalarY = (viewHeight - gl_FragCoord.y);"));
    }

    @Test
    public void testNormalizeForVulkanKeepsFramebufferTexelFetchCoordsNative() {
        String source = "#version 330\n"
            + "uniform float viewHeight;\n"
            + "uniform float viewWidth;\n"
            + "uniform sampler2D colortex0;\n"
            + "uniform sampler2D depthtex0;\n"
            + "uniform sampler2D noisetex;\n"
            + "uniform mat4 iris_ModelViewMatrix;\n"
            + "noperspective in vec2 texCoord;\n"
            + "out vec4 fragColor;\n"
            + "void main(){ivec2 texelCoord = ivec2(gl_FragCoord.xy);"
            + "ivec2 texelCoordM2 = texelCoord + ivec2(1, 0);"
            + "vec3 color = texelFetch(colortex0, texelCoord, 0).rgb;"
            + "float depth = texelFetch(depthtex0, texelCoord, 0).r;"
            + "float depthNeighbour = texelFetch(depthtex0, texelCoordM2, 0).r;"
            + "float noise = texelFetch(noisetex, texelCoord, 0).r;"
            + "vec3 screenPos = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z);"
            + "fragColor = vec4(color, depth + depthNeighbour + noise + screenPos.y);}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("ivec2 texelCoord = ivec2(gl_FragCoord.xy);"));
        assertTrue(normalized.contains("texelFetch(colortex0, texelCoord, 0).rgb"));
        assertTrue(normalized.contains("texelFetch(depthtex0, texelCoord, 0).r"));
        assertTrue(normalized.contains("texelFetch(depthtex0, texelCoordM2, 0).r"));
        assertTrue(normalized.contains("texelFetch(noisetex, texelCoord, 0).r"));
        assertTrue(normalized.contains(
            "vec3 screenPos = vec3((vec2(gl_FragCoord.x, viewHeight - gl_FragCoord.y) / vec2(viewWidth, viewHeight)), gl_FragCoord.z);"
        ));
        assertFalse(normalized.contains("ivec2((vec"));
    }

    @Test
    public void testNormalizeForVulkanKeepsCompositeScreenSpaceTexCoordNative() {
        String source = "#version 330\n"
            + "uniform sampler2D depthtex0;\n"
            + "uniform sampler2D dhDepthTex;\n"
            + "uniform mat4 gbufferProjectionInverse;\n"
            + "uniform mat4 dhProjectionInverse;\n"
            + "noperspective in vec2 texCoord;\n"
            + "out vec4 fragColor;\n"
            + "void main(){"
            + "float z0 = texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r;"
            + "vec4 screenPos = vec4(texCoord, z0, 1.0);"
            + "vec4 viewPos = gbufferProjectionInverse * (screenPos * 2.0 - 1.0);"
            + "float z0DH = texelFetch(dhDepthTex, ivec2(gl_FragCoord.xy), 0).r;"
            + "vec4 screenPosDH = vec4(texCoord, z0DH, 1.0);"
            + "vec4 viewPosDH = dhProjectionInverse * (screenPosDH * 2.0 - 1.0);"
            + "vec4 screenPos1 = vec4(texCoord, z0, 1.0);"
            + "vec4 screenPos1DH = vec4(texCoord, z0DH, 1.0);"
            + "fragColor = vec4(viewPos.xy + viewPosDH.xy + screenPos1.xy + screenPos1DH.xy, 0.0, 1.0);"
            + "}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("vec4 screenPos = vec4(texCoord, z0, 1.0);"));
        assertTrue(normalized.contains("vec4 screenPosDH = vec4(texCoord, z0DH, 1.0);"));
        assertTrue(normalized.contains("vec4 screenPos1 = vec4(texCoord, z0, 1.0);"));
        assertTrue(normalized.contains("vec4 screenPos1DH = vec4(texCoord, z0DH, 1.0);"));
        assertFalse(normalized.contains("vec4 screenPos = vec4(vec2((texCoord).x, 1.0f - (texCoord).y)"));
        assertFalse(normalized.contains("vec4 screenPosDH = vec4(vec2((texCoord).x, 1.0f - (texCoord).y)"));
        assertTrue(normalized.contains("texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r"));
        assertTrue(normalized.contains("texelFetch(dhDepthTex, ivec2(gl_FragCoord.xy), 0).r"));
    }

    @Test
    public void testNormalizeForVulkanFlipsFramebufferTextureSamplingCoords() {
        String source = "#version 330\n"
            + "uniform float viewHeight;\n"
            + "uniform sampler2D colortex0;\n"
            + "uniform sampler2D depthtex0;\n"
            + "uniform sampler2D gaux2;\n"
            + "uniform sampler2D noisetex;\n"
            + "uniform sampler2D tex;\n"
            + "noperspective in vec2 texCoord;\n"
            + "out vec4 fragColor;\n"
            + "void main(){"
            + "vec2 coord1 = texCoord + vec2(0.01);"
            + "vec3 color = texture(colortex0, texCoord).rgb;"
            + "float depth = texture2D(depthtex0, coord1).r;"
            + "vec3 history = texture(gaux2, vec2(0.5, 0.25), 0).rgb;"
            + "vec3 noise = texture(noisetex, texCoord).rgb;"
            + "vec3 atlas = texture(tex, texCoord).rgb;"
            + "fragColor = vec4(color + history + noise + atlas, depth);"
            + "}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("texture(colortex0, vec2((texCoord).x, 1.0f - (texCoord).y)).rgb"));
        assertTrue(normalized.contains("texture2D(depthtex0, vec2((coord1).x, 1.0f - (coord1).y)).r"));
        assertTrue(normalized.contains("texture(gaux2, vec2((vec2(0.5, 0.25)).x, 1.0f - (vec2(0.5, 0.25)).y), 0).rgb"));
        assertTrue(normalized.contains("texture(noisetex, texCoord).rgb"));
        assertTrue(normalized.contains("texture(tex, texCoord).rgb"));
    }

    @Test
    public void testNormalizeForVulkanKeepsShadowTextureSamplingCoordsNative() {
        String source = "#version 330\n"
            + "uniform sampler2DShadow shadowtex0;\n"
            + "uniform sampler2DShadow shadowtex1;\n"
            + "uniform sampler2D noisetex;\n"
            + "out vec4 fragColor;\n"
            + "void main(){"
            + "vec3 shadowPosition = vec3(0.25, 0.75, 0.5);"
            + "float shadow0 = texture(shadowtex0, shadowPosition).x;"
            + "float shadow1 = texture2D(shadowtex1, vec3(shadowPosition.st, shadowPosition.z)).x;"
            + "float noise = texture(noisetex, shadowPosition.xy).r;"
            + "fragColor = vec4(shadow0 + shadow1 + noise);"
            + "}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("texture(shadowtex0, shadowPosition).x"));
        assertTrue(normalized.contains("texture2D(shadowtex1, vec3(shadowPosition.st, shadowPosition.z)).x"));
        assertTrue(normalized.contains("texture(noisetex, shadowPosition.xy).r"));
        assertFalse(normalized.contains("texture(shadowtex0, vec3((shadowPosition).x, 1.0f - (shadowPosition).y"));
        assertFalse(normalized.contains("texture2D(shadowtex1, vec3((vec3(shadowPosition.st, shadowPosition.z)).x, 1.0f - (vec3(shadowPosition.st, shadowPosition.z)).y"));
    }

    @Test
    public void testNormalizeForVulkanFlipsIrisTaaHistoryTextureSampling() {
        String source = "#version 330\n"
            + "uniform float viewHeight;\n"
            + "uniform sampler2D colortex2;\n"
            + "out vec4 fragColor;\n"
            + "void DoTAA(inout vec3 color, inout vec3 temp, float z1){"
            + "vec2 prvCoord = vec2(0.5);"
            + "vec3 tempColor = texture(colortex2, prvCoord).rgb;"
            + "color = mix(color, tempColor, 0.5);"
            + "temp = color;"
            + "}"
            + "void main(){vec3 color = vec3(1.0); vec3 temp = vec3(0.0); float z1 = gl_FragCoord.z;"
            + "DoTAA(color, temp, z1);"
            + "fragColor = vec4(color + temp, 1.0);}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("DoTAA(color, temp, z1);"));
        assertTrue(normalized.contains("texture(colortex2, vec2((prvCoord).x, 1.0f - (prvCoord).y)).rgb"));
    }

    @Test
    public void testNormalizeForVulkanKeepsIrisAmbientOcclusion() {
        String source = "#version 330\n"
            + "uniform float viewHeight;\n"
            + "out vec4 fragColor;\n"
            + "float DoAmbientOcclusion(float z0, float linearZ0, float dither) { return 0.25; }"
            + "void main(){float z0 = gl_FragCoord.z; float linearZ0 = z0; float dither = 0.0;"
            + "float ssao = DoAmbientOcclusion(z0, linearZ0, dither);"
            + "fragColor = vec4(vec3(ssao), 1.0);}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("float ssao = DoAmbientOcclusion(z0, linearZ0, dither);"));
        assertFalse(normalized.contains("float ssao = 1.0f;"));
    }

    @Test
    public void testNormalizeForVulkanPromotesLegacyShaderVersions() {
        String source = "#version 330\nvoid main(){}";

        assertEquals(
            "#version 450\n#define VULKANIC_BACKEND 1\nvoid main(){}",
            GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source)
        );
    }

    @Test
    public void testNormalizeForVulkanRemapsVertexClipDepthToZeroOne() {
        String source = "#version 330\n"
            + "uniform mat4 iris_ProjectionMatrix;\n"
            + "void main(){ gl_Position = iris_ProjectionMatrix * vec4(1.0); }";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.VERTEX, source);

        assertTrue(normalized.contains("float vulkanicOpenGlClipDepthToVulkan(float z, float w)"));
        assertTrue(normalized.contains("gl_Position.z = vulkanicOpenGlClipDepthToVulkan(gl_Position.z, gl_Position.w);"));
    }

    @Test
    public void testNormalizeForVulkanMovesMinecraftLightingFlipIntoShader() {
        String source = "#version 450\n"
            + "layout(std140) uniform Lighting {\n"
            + "    vec3 Light0_Direction;\n"
            + "    vec3 Light1_Direction;\n"
            + "};\n"
            + "vec2 minecraft_compute_light(vec3 lightDir0, vec3 lightDir1, vec3 normal) {\n"
            + "    return vec2(dot(lightDir0, normal), dot(lightDir1, normal));\n"
            + "}\n"
            + "void main(){gl_Position = vec4(1.0);}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.VERTEX, source);

        assertTrue(normalized.contains("vec3 vulkanicMinecraftLightingNormal(vec3 normal)"));
        assertTrue(normalized.contains("normal = vulkanicMinecraftLightingNormal(normal);"));
        assertTrue(normalized.contains("return vec3(normal.x, -normal.y, normal.z);"));
    }

    @Test
    public void testNormalizeForVulkanRewritesStandaloneNonOpaqueUniformsIntoBlock() {
        String source = "#version 330\n"
            + "uniform vec3 u_RegionOffset;\n"
            + "uniform vec2 u_TexCoordShrink;\n"
            + "uniform sampler2D u_LightTex;\n"
            + "void main(){}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.VERTEX, source);

        assertTrue(normalized.contains("uniform VulkanicStandaloneUniforms {"));
        assertTrue(normalized.contains("layout(std140, set = 0, binding = 1)"));
        assertTrue(normalized.contains("vec3 u_RegionOffset;"));
        assertTrue(normalized.contains("vec2 u_TexCoordShrink;"));
        assertTrue(normalized.contains("uniform sampler2D u_LightTex;"));
        assertFalse(normalized.contains("uniform vec3 u_RegionOffset;"));
        assertFalse(normalized.contains("uniform vec2 u_TexCoordShrink;"));
    }

    @Test
    public void testNormalizeForVulkanLegalizesDistantHorizonsTerrainFragmentShaderShape() {
        String source = "#version 150\n"
            + "in vec4 vertexColor;\n"
            + "in vec3 vertexWorldPos;\n"
            + "in vec4 vPos;\n"
            + "in vec4 gl_FragCoord;\n"
            + "out vec4 fragColor;\n"
            + "uniform float uClipDistance = 0.0;\n"
            + "uniform bool uNoiseEnabled;\n"
            + "uniform int uNoiseSteps;\n"
            + "uniform float uNoiseIntensity;\n"
            + "uniform int uNoiseDropoff;\n"
            + "uniform bool uDitherDhRendering;\n"
            + "void main(){ fragColor = vertexColor; if (uDitherDhRendering) { fragColor.a = gl_FragCoord.x; } }";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.startsWith("#version 450"));
        assertTrue(normalized.contains("layout(location = 1) in vec4 vertexColor;"));
        assertTrue(normalized.contains("layout(location = 2) in vec3 vertexWorldPos;"));
        assertTrue(normalized.contains("layout(location = 0) in vec4 vPos;"));
        assertTrue(normalized.contains("uniform VulkanicStandaloneUniforms {"));
        assertTrue(normalized.contains("float uClipDistance;"));
        assertTrue(normalized.contains("bool uNoiseEnabled;"));
        assertTrue(normalized.contains("int uNoiseSteps;"));
        assertTrue(normalized.contains("float uNoiseIntensity;"));
        assertTrue(normalized.contains("int uNoiseDropoff;"));
        assertTrue(normalized.contains("bool uDitherDhRendering;"));
        assertFalse(normalized.contains("in vec4 gl_FragCoord;"));
        assertFalse(normalized.contains("uniform float uClipDistance = 0.0;"));
        assertTrue(normalized.contains("fragColor.a = gl_FragCoord.x;"));
    }

    @Test
    public void testNormalizeForVulkanPinsDistantHorizonsTerrainVaryingsByName() {
        String vertexSource = "#version 150 core\n"
            + "\n"
            + "in uvec4 vPosition;\n"
            + "out vec4 vPos;\n"
            + "in vec4 color;\n"
            + "\n"
            + "out vec4 vertexColor;\n"
            + "out vec3 vertexWorldPos;\n"
            + "out float vertexYPos;\n"
            + "void main(){ vPos = vPosition; vertexColor = color; vertexWorldPos = vPosition.xyz; vertexYPos = vPosition.y; }\n";
        String fragmentSource = "#version 150\n"
            + "in vec4 vertexColor;\n"
            + "in vec3 vertexWorldPos;\n"
            + "in vec4 vPos;\n"
            + "uniform bool uDitherDhRendering;\n"
            + "out vec4 fragColor;\n"
            + "void main(){ fragColor = vertexColor + vec4(vertexWorldPos, 0.0) + vPos; if (uDitherDhRendering) { discard; } }\n";

        String normalizedVertex = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.VERTEX, vertexSource);
        String normalizedFragment = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, fragmentSource);

        assertTrue(normalizedVertex.contains("layout(location = 0) out vec4 vPos;"));
        assertTrue(normalizedVertex.contains("layout(location = 1) out vec4 vertexColor;"));
        assertTrue(normalizedVertex.contains("layout(location = 2) out vec3 vertexWorldPos;"));
        assertTrue(normalizedVertex.contains("layout(location = 3) out float vertexYPos;"));

        assertTrue(normalizedFragment.contains("layout(location = 1) in vec4 vertexColor;"));
        assertTrue(normalizedFragment.contains("layout(location = 2) in vec3 vertexWorldPos;"));
        assertTrue(normalizedFragment.contains("layout(location = 0) in vec4 vPos;"));
    }

    @Test
    public void testNormalizeForVulkanDoesNotGloballyPinVertexColorVaryings() {
        String source = "#version 330\n"
            + "out vec4 vertexColor;\n"
            + "void main(){ vertexColor = vec4(1.0); }\n";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.VERTEX, source);

        assertTrue(normalized.contains("out vec4 vertexColor;"));
        assertFalse(normalized.contains("layout(location = 1) out vec4 vertexColor;"));
    }

    @Test
    public void testNormalizeForVulkanRoutesSodiumTerrainRegionOffsetThroughDynamicTransforms() {
        String source = "#version 330\n"
            + "uniform vec3 u_RegionOffset;\n"
            + "vec3 _vert_position;\n"
            + "uint _draw_id;\n"
            + "vec3 _get_draw_translation(uint drawId) { return vec3(drawId); }\n"
            + "vec4 getVertexPosition() { return vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1.0); }\n"
            + "void main(){ gl_Position = getVertexPosition(); }";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.VERTEX, source);

        assertTrue(normalized.contains("uniform DynamicTransforms {"));
        assertTrue(normalized.contains("vec3 ModelOffset;"));
        assertTrue(normalized.contains("_vert_position + ModelOffset + _get_draw_translation(_draw_id)"));
        assertFalse(normalized.contains("u_RegionOffset"));
        assertFalse(normalized.contains("VulkanicStandaloneUniforms"));
    }

    @Test
    public void testLinkedSodiumTerrainProgramReflectsRegionOffsetAsDynamicTransforms() {
        List<String> capturedSources = new ArrayList<>();
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            capturedSources.add(source.toString());
            return new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x31, 0x32}, sourceName, "stub");
        });

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);
        uploadSource(
            backend,
            vertexShader,
            "#version 330\n"
                + "uniform vec3 u_RegionOffset;\n"
                + "vec3 _vert_position;\n"
                + "uint _draw_id;\n"
                + "vec3 _get_draw_translation(uint drawId) { return vec3(drawId); }\n"
                + "vec4 getVertexPosition() { return vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1.0); }\n"
                + "void main(){ gl_Position = getVertexPosition(); }"
        );
        uploadSource(
            backend,
            fragmentShader,
            "#version 330\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main(){ fragColor = vec4(1.0); }"
        );

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        VulkanCommandContext introspectionContext = new VulkanCommandContext(1L, "sodium-terrain-region-offset-test");
        assertEquals(VulkanicAPI.GL_TRUE, backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_LINK_STATUS));
        assertEquals(0, backend.getUniformBlockIndex(introspectionContext, program, "DynamicTransforms"));
        assertEquals(-1, backend.getUniformLocation(introspectionContext, program, "u_RegionOffset"));
        assertTrue(capturedSources.stream().anyMatch(source ->
            source.contains("layout(std140, set = 0, binding = 0) uniform DynamicTransforms")));
        assertFalse(String.join("\n", capturedSources).contains("u_RegionOffset"));
    }

    @Test
    public void testCompileShaderNormalizesSodiumStyleStandaloneUniformsForVulkan() {
        AtomicReference<String> capturedSource = new AtomicReference<>();

        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            capturedSource.set(source.toString());
            return new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x21, 0x22}, sourceName, "stub");
        });

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);
        uploadSource(
            backend,
            shader,
            "#version 330\n"
                + "uniform sampler2D u_BlockTex;\n"
                + "uniform vec4 u_FogColor;\n"
                + "uniform vec2 u_EnvironmentFog;\n"
                + "uniform vec2 u_RenderFog;\n"
                + "void main(){}"
        );
        backend.compileShader(TEST_CONTEXT, shader);

        assertTrue(capturedSource.get().contains("uniform VulkanicStandaloneUniforms {"));
        assertTrue(capturedSource.get().contains("layout(std140, set = 0, binding = 1)"));
        assertTrue(capturedSource.get().contains("vec4 u_FogColor;"));
        assertTrue(capturedSource.get().contains("vec2 u_EnvironmentFog;"));
        assertTrue(capturedSource.get().contains("vec2 u_RenderFog;"));
        assertTrue(capturedSource.get().contains("uniform sampler2D u_BlockTex;"));
    }

    @Test
    public void testInjectExplicitVulkanBindingsPinsParticleVertexInputsToVertexFormatOrder() throws Exception {
        Method injector = VulkanBackend.class.getDeclaredMethod(
            "injectExplicitVulkanBindings",
            RenderPipeline.class,
            net.blaze3d.shaders.ShaderType.class,
            String.class
        );
        injector.setAccessible(true);

        RenderPipeline particlePipeline = new TestRenderPipeline(
            ResourceLocation.withDefaultNamespace("pipeline/opaque_particle"),
            DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS
        );

        String source = "#version 330\n"
            + "in vec3 Position;\n"
            + "in vec2 UV0;\n"
            + "in vec4 Color;\n"
            + "in ivec2 UV2;\n"
            + "void main() { gl_Position = vec4(Position, 1.0); }\n";

        String rewritten = (String) injector.invoke(null, particlePipeline, net.blaze3d.shaders.ShaderType.VERTEX, source);

        assertTrue(rewritten.contains("layout(location = 0) in vec3 Position;"));
        assertTrue(rewritten.contains("layout(location = 1) in vec2 UV0;"));
        assertTrue(rewritten.contains("layout(location = 2) in vec4 Color;"));
        assertTrue(rewritten.contains("layout(location = 3) in ivec2 UV2;"));
    }

    @Test
    public void testInjectExplicitVulkanBindingsPinsIrisWrappedUniformBlocksToPortableBindings() throws Exception {
        Method injector = VulkanBackend.class.getDeclaredMethod(
            "injectExplicitVulkanBindings",
            RenderPipeline.class,
            net.blaze3d.shaders.ShaderType.class,
            String.class
        );
        injector.setAccessible(true);

        RenderPipeline pipeline = new TestRenderPipeline(
            ResourceLocation.withDefaultNamespace("vulkanic/iris_wrapped_uniform_blocks"),
            DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.TRIANGLES,
            List.of("Sampler0", "Sampler2"),
            List.of(
                new RenderPipeline.UniformDescription("DynamicTransforms", UniformType.UNIFORM_BUFFER),
                new RenderPipeline.UniformDescription("Projection", UniformType.UNIFORM_BUFFER),
                new RenderPipeline.UniformDescription("Fog", UniformType.UNIFORM_BUFFER),
                new RenderPipeline.UniformDescription("SodiumChunkParams", UniformType.UNIFORM_BUFFER)
            )
        );

        String source = "#version 450\n"
            + "layout(std140) uniform iris_DynamicTransforms { mat4 ModelViewMat; };\n"
            + "layout(std140) uniform iris_Projection { mat4 ProjMat; };\n"
            + "layout(std140) uniform iris_Fog { vec4 FogColor; };\n"
            + "layout(std140) uniform SodiumChunkParams { vec4 ChunkInfo; };\n"
            + "uniform sampler2D Sampler0;\n"
            + "uniform sampler2D Sampler2;\n"
            + "void main(){ gl_Position = ProjMat * ModelViewMat * vec4(0.0); }";

        String rewritten = (String) injector.invoke(null, pipeline, net.blaze3d.shaders.ShaderType.VERTEX, source);

        assertTrue(rewritten.contains("layout(set = 0, binding = 0) uniform sampler2D Sampler0;"));
        assertTrue(rewritten.contains("layout(set = 0, binding = 1) uniform sampler2D Sampler2;"));
        assertTrue(rewritten.contains("layout(std140, set = 0, binding = 2) uniform iris_DynamicTransforms"));
        assertTrue(rewritten.contains("layout(std140, set = 0, binding = 3) uniform iris_Projection"));
        assertTrue(rewritten.contains("layout(std140, set = 0, binding = 4) uniform iris_Fog"));
        assertTrue(rewritten.contains("layout(std140, set = 0, binding = 5) uniform SodiumChunkParams"));
    }

    @Test
    public void testSodiumIrisTerrainVertexFormatsMatchShaderInputTypes() throws Exception {
        Method mapper = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine")
            .getDeclaredMethod("toVkVertexElementFormat", VertexFormatElement.class);
        mapper.setAccessible(true);

        assertEquals(VK10.VK_FORMAT_R32_UINT, mapper.invoke(null,
            new VertexFormatElement(20, 11, VertexFormatElement.Type.UINT, VertexFormatElement.Usage.GENERIC, 1)));
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_SNORM, mapper.invoke(null,
            new VertexFormatElement(21, 10, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 4)));
        assertEquals(VK10.VK_FORMAT_R16G16_USCALED, mapper.invoke(null,
            new VertexFormatElement(22, 12, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.GENERIC, 2)));
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_SNORM, mapper.invoke(null,
            new VertexFormatElement(23, 13, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 4)));
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_SNORM, mapper.invoke(null,
            new VertexFormatElement(24, 14, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 4)));
    }

    @Test
    public void testRemainingStandaloneVertexInputsGetNonConflictingLocations() throws Exception {
        Method injector = VulkanBackend.class.getDeclaredMethod(
            "injectExplicitRemainingVertexInputLocations",
            String.class
        );
        injector.setAccessible(true);

        String source = "#version 330\n"
            + "layout(location = 0) in uvec4 vPosition;\n"
            + "layout(location = 1) in vec4 iris_color;\n"
            + "layout(location = 2) in uvec4 irisExtra;\n"
            + "in vec4 glColor;\n"
            + "in mat4 mat;\n"
            + "in vec4 at_tangent;\n"
            + "void main() { gl_Position = mat * glColor + at_tangent + vec4(vPosition); }\n";

        String rewritten = (String) injector.invoke(null, source);

        assertTrue(rewritten.contains("layout(location = 3) in vec4 glColor;"),
            "Unbound shaderpack vertex inputs should be assigned after explicit DH terrain inputs");
        assertTrue(rewritten.contains("layout(location = 4) in mat4 mat;"),
            "Matrix inputs should receive a stable first location");
        assertTrue(rewritten.contains("layout(location = 8) in vec4 at_tangent;"),
            "Matrix location spans must be reserved so following inputs cannot collide");
        assertFalse(rewritten.contains("\nin vec4 glColor;"),
            "No auto-located standalone input should remain to collide with location zero");
    }

    @Test
    public void testStandaloneVertexInputReflectionReservesFallbackMatrixColumns() throws Exception {
        Method collector = VulkanBackend.class.getDeclaredMethod(
            "collectExplicitVertexInputDeclarations",
            String.class
        );
        collector.setAccessible(true);

        String source = "#version 330\n"
            + "layout(location = 3) in vec4 glColor;\n"
            + "layout(location = 4) in mat4 mat;\n"
            + "layout(location = 8) in uvec4 irisExtra;\n"
            + "void main() { gl_Position = mat * glColor + vec4(irisExtra); }\n";

        List<?> inputs = (List<?>) collector.invoke(null, source);

        assertEquals(6, inputs.size(),
            "Fallback input reflection should expand matrix declarations into occupied Vulkan locations");
        assertTrue(inputs.toString().contains("ReflectedVertexInput[location=3, typeName=vec4]"));
        assertTrue(inputs.toString().contains("ReflectedVertexInput[location=4, typeName=vec4]"));
        assertTrue(inputs.toString().contains("ReflectedVertexInput[location=7, typeName=vec4]"));
        assertTrue(inputs.toString().contains("ReflectedVertexInput[location=8, typeName=uvec4]"));
    }

    @Test
    public void testInjectExplicitVulkanBindingsPinsParticleStageInterfaces() throws Exception {
        Method injector = VulkanBackend.class.getDeclaredMethod(
            "injectExplicitVulkanBindings",
            RenderPipeline.class,
            net.blaze3d.shaders.ShaderType.class,
            String.class
        );
        injector.setAccessible(true);

        RenderPipeline particlePipeline = new TestRenderPipeline(
            ResourceLocation.withDefaultNamespace("pipeline/opaque_particle"),
            DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS
        );

        String vertexSource = "#version 330\n"
            + "out float sphericalVertexDistance;\n"
            + "out float cylindricalVertexDistance;\n"
            + "out vec2 texCoord0;\n"
            + "out vec4 vertexColor;\n"
            + "void main() { gl_Position = vec4(0.0); }\n";
        String fragmentSource = "#version 330\n"
            + "in float sphericalVertexDistance;\n"
            + "in float cylindricalVertexDistance;\n"
            + "in vec2 texCoord0;\n"
            + "in vec4 vertexColor;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(texCoord0, sphericalVertexDistance, vertexColor.a); }\n";

        String rewrittenVertex = (String) injector.invoke(null, particlePipeline, net.blaze3d.shaders.ShaderType.VERTEX, vertexSource);
        String rewrittenFragment = (String) injector.invoke(null, particlePipeline, net.blaze3d.shaders.ShaderType.FRAGMENT, fragmentSource);

        assertTrue(rewrittenVertex.contains("layout(location = 0) out float sphericalVertexDistance;"));
        assertTrue(rewrittenVertex.contains("layout(location = 1) out float cylindricalVertexDistance;"));
        assertTrue(rewrittenVertex.contains("layout(location = 2) out vec2 texCoord0;"));
        assertTrue(rewrittenVertex.contains("layout(location = 3) out vec4 vertexColor;"));

        assertTrue(rewrittenFragment.contains("layout(location = 0) in float sphericalVertexDistance;"));
        assertTrue(rewrittenFragment.contains("layout(location = 1) in float cylindricalVertexDistance;"));
        assertTrue(rewrittenFragment.contains("layout(location = 2) in vec2 texCoord0;"));
        assertTrue(rewrittenFragment.contains("layout(location = 3) in vec4 vertexColor;"));
    }

    @Test
    public void testVulkanFallbackTextureUnitBudgetSupportsModernShaderpacks() {
        VulkanBackend backend = new VulkanBackend();
        VulkanCommandContext queryContext = new VulkanCommandContext(1L, "texture-unit-query");

        assertEquals(32,
            backend.getInteger(queryContext, VulkanicIntegerQuery.MAX_TEXTURE_IMAGE_UNITS));
    }

    @Test
    public void testProgramLinkUsesCompiledSpirvShaders() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x0A, 0x0B}, sourceName, "stub")
        );

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(backend, vertexShader, "#version 450\nvoid main(){}");
        uploadSource(backend, fragmentShader, "#version 450\nvoid main(){}");

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        assertEquals(VulkanicAPI.GL_TRUE,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_LINK_STATUS));
        assertEquals("", backend.getProgramInfoLog(TEST_CONTEXT, program));
    }

    @Test
    public void testDeletingAttachedShadersBeforeLinkPreservesOpenGlLifetimeSemantics() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x2A, 0x2B}, sourceName, "stub")
        );

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(backend, vertexShader, "#version 450\nvoid main(){gl_Position=vec4(0.0);}");
        uploadSource(backend, fragmentShader, "#version 450\nvoid main(){}");

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.deleteShader(TEST_CONTEXT, vertexShader);
        backend.deleteShader(TEST_CONTEXT, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        assertEquals(VulkanicAPI.GL_TRUE,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_LINK_STATUS));
        assertEquals("", backend.getProgramInfoLog(TEST_CONTEXT, program));
    }

    @Test
    public void testDetachingShadersAfterSuccessfulLinkPreservesProgramLinkStatus() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x1A, 0x1B}, sourceName, "stub")
        );

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(backend, vertexShader, "#version 450\nvoid main(){gl_Position=vec4(0.0);}");
        uploadSource(backend, fragmentShader, "#version 450\nvoid main(){}");

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        backend.detachShader(TEST_CONTEXT, program, vertexShader);
        backend.detachShader(TEST_CONTEXT, program, fragmentShader);

        assertEquals(VulkanicAPI.GL_TRUE,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_LINK_STATUS));
        assertEquals("", backend.getProgramInfoLog(TEST_CONTEXT, program));
    }

    @Test
    public void testProgramLinkReflectsSamplerAndUniformBlockIntrospectionForVulkanCompatibility() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x0C, 0x0D}, sourceName, "stub")
        );

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(
            backend,
            vertexShader,
            "#version 450\n"
                + "layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; };\n"
                + "layout(std140) uniform Projection { mat4 ProjMat; };\n"
                + "void main(){ gl_Position = ProjMat * ModelViewMat * vec4(0.0); }"
        );
        uploadSource(
            backend,
            fragmentShader,
            "#version 450\n"
                + "uniform sampler2D Sampler0;\n"
                + "uniform vec4 FogColor;\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main(){ fragColor = texture(Sampler0, vec2(0.0)) + FogColor * 0.0; }"
        );

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        VulkanCommandContext introspectionContext = new VulkanCommandContext(1L, "introspection-test");

        assertEquals(2,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_ACTIVE_UNIFORMS));
        assertEquals(3,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_ACTIVE_UNIFORM_BLOCKS));
        int samplerLocation = backend.getUniformLocation(introspectionContext, program, "Sampler0");
        int fogColorLocation = backend.getUniformLocation(introspectionContext, program, "FogColor");
        assertTrue(samplerLocation >= 0);
        assertTrue(fogColorLocation >= 0);
        assertNotEquals(samplerLocation, fogColorLocation);
        assertEquals(samplerLocation,
            backend.getUniformLocation(introspectionContext, program, "Sampler0"));
        assertEquals(fogColorLocation,
            backend.getUniformLocation(introspectionContext, program, "FogColor"));
        assertEquals(0,
            backend.getUniformBlockIndex(introspectionContext, program, "DynamicTransforms"));
        assertEquals(1,
            backend.getUniformBlockIndex(introspectionContext, program, "Projection"));
        assertEquals("DynamicTransforms",
            backend.retrieveActiveUniformBlockName(introspectionContext, program, 0));
        assertEquals("Projection",
            backend.retrieveActiveUniformBlockName(introspectionContext, program, 1));
        assertEquals("VulkanicStandaloneUniforms",
            backend.retrieveActiveUniformBlockName(introspectionContext, program, 2));
        assertEquals("Sampler0",
            backend.getActiveUniform(introspectionContext, program, 0, 256, null, null));
        IntBuffer fogColorArraySize = IntBuffer.allocate(1);
        IntBuffer fogColorType = IntBuffer.allocate(1);
        assertEquals("FogColor",
            backend.getActiveUniform(introspectionContext, program, 1, 256, fogColorArraySize, fogColorType));
        assertEquals(1, fogColorArraySize.get(0));
        assertEquals(VulkanicAPI.GL_FLOAT_VEC4, fogColorType.get(0));
        assertEquals(VulkanicUniformReflectionType.FLOAT_VEC4,
            VulkanicUniformReflectionType.fromLegacyGlConstant(fogColorType.get(0)).orElseThrow());
    }

    @Test
    public void testLinkedProgramResourceLayoutNormalizesIrisWrappedUniformBlockNames() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x3A, 0x3B}, sourceName, "stub")
        );

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(
            backend,
            vertexShader,
            "#version 450\n"
                + "layout(std140, set = 0, binding = 7) uniform iris_DynamicTransforms { mat4 ModelViewMat; };\n"
                + "void main(){ gl_Position = ModelViewMat * vec4(0.0); }"
        );
        uploadSource(
            backend,
            fragmentShader,
            "#version 450\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main(){ fragColor = vec4(1.0); }"
        );

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        VulkanCommandContext introspectionContext = new VulkanCommandContext(1L, "iris-wrapped-block-layout-test");
        PipelineDescriptor.ResourceLayout layout = backend.getLinkedProgramResourceLayout(
            introspectionContext,
            program,
            Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT)
        );

        assertTrue(layout.findByName("DynamicTransforms").isPresent());
        assertFalse(layout.findByName("iris_DynamicTransforms").isPresent());
        assertEquals(7, layout.findByName("DynamicTransforms").orElseThrow().binding());
        assertEquals(0, backend.getUniformBlockIndex(introspectionContext, program, "DynamicTransforms"));
    }

    @Test
    public void testLinkedProgramResourceLayoutPreservesExplicitDescriptorBindings() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x41, 0x42}, sourceName, "stub")
        );

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(
            backend,
            vertexShader,
            "#version 450\n"
                + "layout(std140, set = 0, binding = 6) uniform DynamicTransforms { mat4 ModelViewMat; };\n"
                + "void main(){ gl_Position = ModelViewMat * vec4(0.0); }"
        );
        uploadSource(
            backend,
            fragmentShader,
            "#version 450\n"
                + "layout(set = 0, binding = 4) uniform sampler2D Sampler0;\n"
                + "layout(std140, set = 0, binding = 2) uniform Projection { mat4 ProjMat; };\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main(){ fragColor = texture(Sampler0, vec2(0.0)) + ProjMat[0][0]; }"
        );

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        VulkanCommandContext introspectionContext = new VulkanCommandContext(1L, "explicit-binding-layout-test");
        PipelineDescriptor.ResourceLayout layout = backend.getLinkedProgramResourceLayout(
            introspectionContext,
            program,
            Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT)
        );

        assertEquals(6, layout.findByName("DynamicTransforms").orElseThrow().binding());
        assertEquals(4, layout.findByName("Sampler0").orElseThrow().binding());
        assertEquals(2, layout.findByName("Projection").orElseThrow().binding());
    }

    @Test
    public void testLinkedProgramResourceLayoutAllocatesImplicitBindingsAroundExplicitOnes() {
        List<String> capturedSources = new ArrayList<>();
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            capturedSources.add(source.toString());
            return new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x43, 0x44}, sourceName, "stub");
        });

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(
            backend,
            vertexShader,
            "#version 450\n"
                + "layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; };\n"
                + "void main(){ gl_Position = ModelViewMat * vec4(0.0); }"
        );
        uploadSource(
            backend,
            fragmentShader,
            "#version 450\n"
                + "layout(set = 0, binding = 0) uniform sampler2D Sampler0;\n"
                + "uniform sampler2D Sampler1;\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main(){ fragColor = texture(Sampler0, vec2(0.0)) + texture(Sampler1, vec2(0.0)); }"
        );

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        VulkanCommandContext introspectionContext = new VulkanCommandContext(1L, "implicit-binding-layout-test");
        PipelineDescriptor.ResourceLayout layout = backend.getLinkedProgramResourceLayout(
            introspectionContext,
            program,
            Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT)
        );

        assertEquals(1, layout.findByName("DynamicTransforms").orElseThrow().binding());
        assertEquals(0, layout.findByName("Sampler0").orElseThrow().binding());
        assertEquals(2, layout.findByName("Sampler1").orElseThrow().binding());

        String linkedSources = String.join("\n", capturedSources);
        assertTrue(linkedSources.contains("layout(std140, set = 0, binding = 1) uniform DynamicTransforms"));
        assertTrue(linkedSources.contains("layout(set = 0, binding = 0) uniform sampler2D Sampler0;"));
        assertTrue(linkedSources.contains("layout(set = 0, binding = 2) uniform sampler2D Sampler1;"));
    }

    @Test
    public void testCompileFailureSurfacesThroughShaderAndProgramStatus() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            throw new IllegalStateException("forced compile failure");
        });

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        uploadSource(backend, shader, "#version 450\nvoid main(){}");
        backend.compileShader(TEST_CONTEXT, shader);

        assertEquals(VulkanicAPI.GL_FALSE,
            backend.getShaderParameter(TEST_CONTEXT, shader, VulkanicAPI.GL_COMPILE_STATUS));
        assertTrue(backend.getShaderInfoLog(TEST_CONTEXT, shader).contains("forced compile failure"));
        assertFalse(backend.getCompiledSpirvModule(TEST_CONTEXT, shader).isPresent());

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, shader);
        backend.linkProgram(TEST_CONTEXT, program);

        assertEquals(VulkanicAPI.GL_FALSE,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_LINK_STATUS));
        assertTrue(backend.getProgramInfoLog(TEST_CONTEXT, program).contains("failed compilation"));
    }

    @Test
    public void testUploadingNewSourceInvalidatesPreviouslyCompiledModule() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x11, 0x22, 0x33, 0x44}, sourceName, "stub")
        );

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        uploadSource(backend, shader, "#version 450\nvoid main(){}");
        backend.compileShader(TEST_CONTEXT, shader);
        assertTrue(backend.getCompiledSpirvModule(TEST_CONTEXT, shader).isPresent(),
            "Initial compile should produce a SPIR-V module");

        uploadSource(backend, shader, "#version 450\nvoid main(){gl_Position=vec4(1.0);}");

        assertFalse(backend.getCompiledSpirvModule(TEST_CONTEXT, shader).isPresent(),
            "Uploading new source should invalidate previous SPIR-V module");
        assertEquals(VulkanicAPI.GL_FALSE,
            backend.getShaderParameter(TEST_CONTEXT, shader, VulkanicAPI.GL_COMPILE_STATUS));
    }

    @Test
    public void testVulkanComputeCapabilityIsReportedWhenBackendImplementsDispatchPath() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x61, 0x62}, sourceName, "stub")
        );

        assertTrue(backend.checkFunctionAvailable("glDispatchCompute"));
        assertTrue(backend.checkFunctionAvailable("glDispatchComputeIndirect"));
        assertTrue(backend.checkFunctionAvailable("glBindImageTexture"));
        assertTrue(backend.checkFunctionAvailable("glMemoryBarrier"));
        assertFalse(backend.checkFunctionAvailable("glNamedStringARB"));
    }

    @Test
    public void testComputeImageUniformsBecomeStorageImageResourceBindings() {
        List<String> capturedSources = new ArrayList<>();
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            capturedSources.add(source.toString());
            return new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x63, 0x64}, sourceName, "stub");
        });

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_COMPUTE_SHADER);
        uploadSource(
            backend,
            shader,
            "#version 430\n"
                + "layout(local_size_x = 8, local_size_y = 4, local_size_z = 2) in;\n"
                + "writeonly uniform image3D floodfill_img;\n"
                + "readonly uniform image2D source_img;\n"
                + "void main(){ imageStore(floodfill_img, ivec3(gl_GlobalInvocationID.xyz), vec4(1.0)); }"
        );
        backend.compileShader(TEST_CONTEXT, shader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, shader);
        backend.linkProgram(TEST_CONTEXT, program);

        VulkanCommandContext introspectionContext = new VulkanCommandContext(1L, "compute-image-layout-test");
        PipelineDescriptor.ResourceLayout layout = backend.getLinkedProgramResourceLayout(
            introspectionContext,
            program,
            Set.of(VulkanicShaderStage.COMPUTE)
        );

        PipelineDescriptor.ResourceBinding floodfill = layout.findByName("floodfill_img").orElseThrow();
        PipelineDescriptor.ResourceBinding source = layout.findByName("source_img").orElseThrow();
        assertEquals(PipelineDescriptor.ResourceType.STORAGE_IMAGE, floodfill.type());
        assertEquals(PipelineDescriptor.ResourceType.STORAGE_IMAGE, source.type());
        assertEquals(Set.of(VulkanicShaderStage.COMPUTE), floodfill.stages());
        assertEquals(0, floodfill.binding());
        assertEquals(1, source.binding());
        assertTrue(capturedSources.stream().anyMatch(text ->
            text.contains("layout(set = 0, binding = 0) writeonly uniform image3D floodfill_img;")));
        assertTrue(capturedSources.stream().anyMatch(text ->
            text.contains("layout(set = 0, binding = 1) readonly uniform image2D source_img;")));
    }

    @Test
    public void testComputeWorkGroupSizeIsReflectedForIrisComputePrograms() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x65, 0x66}, sourceName, "stub")
        );

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_COMPUTE_SHADER);
        uploadSource(
            backend,
            shader,
            "#version 430\n"
                + "layout(local_size_x = 8, local_size_y = 16) in;\n"
                + "void main(){}"
        );
        backend.compileShader(TEST_CONTEXT, shader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, shader);
        backend.linkProgram(TEST_CONTEXT, program);

        int[] localSize = new int[3];
        backend.getProgramiv(
            new VulkanCommandContext(1L, "compute-local-size-test"),
            program,
            VulkanicAPI.GL_COMPUTE_WORK_GROUP_SIZE,
            localSize
        );

        assertArrayEquals(new int[]{8, 16, 1}, localSize);
    }

    @Test
    public void testSourceWiresNativeVulkanShaderModuleLifecycle() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("vkCreateShaderModule"),
            "Vulkan shader abstraction should materialize native VkShaderModule handles");
        assertTrue(source.contains("destroyShaderModule("),
            "Vulkan shader abstraction should destroy native VkShaderModule handles");
        assertTrue(source.contains("materializeCompiledShaderModules("),
            "Vulkan native bring-up should materialize already-compiled shader modules");
    }

    private static void uploadSource(VulkanBackend backend, int shader, String source) {
        backend.uploadShaderSource(TEST_CONTEXT, shader, source);
    }
}
