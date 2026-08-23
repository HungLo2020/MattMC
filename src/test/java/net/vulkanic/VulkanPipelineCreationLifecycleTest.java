package net.vulkanic;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanPipelineCreationLifecycleTest {

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
            return "pipeline-test";
        }
    };

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    public void testOpenGLPipelineCreationPathIsUnaffectedByVulkanChanges() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> VulkanicAPI.createPipeline(PipelineDescriptor.fromRenderPipeline(buildTestPipeline())),
            "OpenGL backend should continue using its own pipeline path without Vulkan fallback"
        );

        String message = failure.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("GlDevice has not been registered"),
            "OpenGL createPipeline should still fail for missing GlDevice in unit tests");
        assertFalse(message.contains("Vulkan-native pipeline creation is not implemented yet."),
            "OpenGL path should not route through old Vulkan unsupported stubs");
    }

    @Test
    public void testVulkanPipelineCreationFailsHardOrExecutesWhenReady() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);
        VulkanNativeInitializationInfo info = VulkanicAPI.initializeNativeVulkanRuntime();

        if (!info.isNativeVulkanReady()) {
            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> VulkanicAPI.createPipeline(descriptorWithDummySpirv()),
                "Vulkan-selected createPipeline should fail hard with readiness diagnostics when runtime is unavailable"
            );
            assertTrue(
                failure.getMessage().contains("Readiness report:")
                    || failure.getMessage().contains("Rust Vulkan whole-frame ownership"),
                "Pipeline creation must fail with either native readiness diagnostics or the Rust ownership boundary"
            );
            return;
        }

        PipelineDescriptor descriptor = descriptorWithCompiledSpirv();
        PipelineHandle handle = assertDoesNotThrow(() -> VulkanicAPI.createPipeline(descriptor));
        assertTrue(handle.isValid(), "Created Vulkan pipeline handle should report valid state");

        handle.close();
        assertFalse(handle.isValid(), "Closed Vulkan pipeline handle should report invalid state");
    }

    @Test
    public void testVulkanPipelineSourceNoLongerUsesUnsupportedStubs() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("vkCreateGraphicsPipelines"),
            "Vulkan pipeline creation should materialize native VkPipeline handles");
        assertTrue(source.contains("vkCmdBindPipeline"),
            "Vulkan render pass should bind VkPipeline via vkCmdBindPipeline");
        assertTrue(source.contains("createVulkanPipeline("),
            "Vulkan backend should provide a native pipeline creation path");

        assertFalse(source.contains("Vulkan-native pipeline creation is not implemented yet."),
            "Legacy unsupported createPipeline stub should be removed");
        assertFalse(source.contains("Vulkan render-pass pipeline binding is not implemented yet."),
            "Legacy unsupported render-pass setPipeline stub should be removed");
    }

    private static PipelineDescriptor descriptorWithCompiledSpirv() {
        VulkanicSpirvModule vertModule = VulkanicAPI.compileSpirvModule(
            TEST_CONTEXT,
            VulkanicShaderStage.VERTEX,
            "#version 450\n"
                + "layout(location = 0) in vec3 inPos;\n"
                + "layout(location = 1) in vec4 inColor;\n"
                + "layout(location = 0) out vec4 vColor;\n"
                + "void main() {\n"
                + "  gl_Position = vec4(inPos, 1.0);\n"
                + "  vColor = inColor;\n"
                + "}\n",
            "test-pipeline.vert",
            "main"
        );

        VulkanicSpirvModule fragModule = VulkanicAPI.compileSpirvModule(
            TEST_CONTEXT,
            VulkanicShaderStage.FRAGMENT,
            "#version 450\n"
                + "layout(location = 0) in vec4 vColor;\n"
                + "layout(location = 0) out vec4 outColor;\n"
                + "void main() {\n"
                + "  outColor = vColor;\n"
                + "}\n",
            "test-pipeline.frag",
            "main"
        );

        return PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            buildTestPipeline(),
            List.of(vertModule, fragModule)
        );
    }

    private static PipelineDescriptor descriptorWithDummySpirv() {
        VulkanicSpirvModule vertModule = new VulkanicSpirvModule(
            VulkanicShaderStage.VERTEX,
            "main",
            new byte[]{0x03, 0x02, 0x23, 0x07},
            "dummy.vert",
            "unit-test"
        );
        VulkanicSpirvModule fragModule = new VulkanicSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[]{0x03, 0x02, 0x23, 0x07},
            "dummy.frag",
            "unit-test"
        );

        return PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            buildTestPipeline(),
            List.of(vertModule, fragModule)
        );
    }

    private static RenderPipeline buildTestPipeline() {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace("vulkanic/test_pipeline_creation"))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
            .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
            .withPolygonMode(PolygonMode.FILL)
            .withCull(true)
            .withoutBlend()
            .withColorWrite(true, true)
            .withDepthWrite(true)
            .withColorLogic(LogicOp.NONE)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthBias(0.0f, 0.0f)
            .build();
    }

    private static void resetBackendState() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        Field rawVulkanBackendField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
        rawVulkanBackendField.setAccessible(true);
        rawVulkanBackendField.set(null, null);
        net.vulkanic.bridge.RustGalVulkanWholeFrameMode.deactivateRustPresentation();
        net.vulkanic.bridge.RustGalVulkanWholeFrameMode.clearVulkanBackendSelection();
    }
}
