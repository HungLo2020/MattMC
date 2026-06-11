package net.vulkanic.backends.vulkan;

import net.vulkanic.CommandContext;
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
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
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
            super(
                location,
                ResourceLocation.withDefaultNamespace("core/particle"),
                ResourceLocation.withDefaultNamespace("core/particle"),
                ShaderDefines.builder().build(),
                List.of(),
                List.of(),
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
        assertEquals("#version 450\nvoid main(){}", capturedSource.get());

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
            source,
            GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source)
        );
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
    public void testNormalizeForVulkanKeepsFramebufferTexelCoordsNative() {
        String source = "#version 330\n"
            + "uniform float viewHeight;\n"
            + "uniform float viewWidth;\n"
            + "uniform sampler2D colortex0;\n"
            + "uniform sampler2D depthtex0;\n"
            + "uniform mat4 iris_ModelViewMatrix;\n"
            + "noperspective in vec2 texCoord;\n"
            + "out vec4 fragColor;\n"
            + "void main(){ivec2 texelCoord = ivec2(gl_FragCoord.xy);"
            + "ivec2 texelCoordM2 = texelCoord + ivec2(1, 0);"
            + "vec3 color = texelFetch(colortex0, texelCoord, 0).rgb;"
            + "float depth = texelFetch(depthtex0, texelCoord, 0).r;"
            + "float depthNeighbour = texelFetch(depthtex0, texelCoordM2, 0).r;"
            + "vec3 screenPos = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z);"
            + "fragColor = vec4(color, depth + depthNeighbour + screenPos.y);}";

        String normalized = GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source);

        assertTrue(normalized.contains("ivec2 texelCoord = ivec2(gl_FragCoord.xy);"));
        assertTrue(normalized.contains("texelFetch(colortex0, texelCoord, 0).rgb"));
        assertTrue(normalized.contains("texelFetch(depthtex0, texelCoord, 0).r"));
        assertTrue(normalized.contains("texelFetch(depthtex0, texelCoordM2, 0).r"));
        assertTrue(normalized.contains(
            "vec3 screenPos = vec3((vec2(gl_FragCoord.x, viewHeight - gl_FragCoord.y) / vec2(viewWidth, viewHeight)), gl_FragCoord.z);"
        ));
        assertFalse(normalized.contains("ivec2((vec"));
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
    public void testNormalizeForVulkanKeepsIrisTaaHistoryBlend() {
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
            "#version 450\nvoid main(){}",
            GlslangSpirvCompiler.normalizeForVulkan(VulkanicShaderStage.FRAGMENT, source)
        );
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
