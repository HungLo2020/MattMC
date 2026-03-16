package net.vulkanic.backends.vulkan;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanBackendSpirvPathTest {

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
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main(){ fragColor = texture(Sampler0, vec2(0.0)); }"
        );

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        VulkanCommandContext introspectionContext = new VulkanCommandContext(1L, "introspection-test");

        assertEquals(1,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_ACTIVE_UNIFORMS));
        assertEquals(2,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_ACTIVE_UNIFORM_BLOCKS));
        assertEquals(0,
            backend.getUniformLocation(introspectionContext, program, "Sampler0"));
        assertEquals(0,
            backend.getUniformBlockIndex(introspectionContext, program, "DynamicTransforms"));
        assertEquals(1,
            backend.getUniformBlockIndex(introspectionContext, program, "Projection"));
        assertEquals("DynamicTransforms",
            backend.retrieveActiveUniformBlockName(introspectionContext, program, 0));
        assertEquals("Projection",
            backend.retrieveActiveUniformBlockName(introspectionContext, program, 1));
        assertEquals("Sampler0",
            backend.getActiveUniform(introspectionContext, program, 0, 256, null, null));
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
