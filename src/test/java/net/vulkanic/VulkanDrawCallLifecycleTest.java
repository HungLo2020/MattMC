package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.vulkan.VulkanBackend;
import net.vulkanic.backends.vulkan.VulkanCommandContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Vulkan draw-call lifecycle wiring.
 */
public class VulkanDrawCallLifecycleTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    public void testDrawArraysRejectsNonVulkanContextBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        VulkanicGalExecutionRequest.ExecutionResult result = backend.executeGraphicsDraw(
            OpenGLCommandContext.IMMEDIATE,
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArrays",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            )
        );

        assertFalse(result.successful());
        assertEquals(VulkanicGalExecutionRequest.ExecutionStatus.BACKEND_FAILURE, result.status());
        assertTrue(result.detail().contains("requires VulkanCommandContext"));
    }

    @Test
    public void testDrawElementsRejectsUnsupportedIndexTypeBeforeNativeReadinessChecks() {
        assertTrue(VulkanicIndexType.fromLegacyGlConstant(0xDEAD).isEmpty());
    }

    @Test
    public void testDrawElementsRejectsMisalignedIndexOffsetBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        VulkanicGalExecutionRequest.ExecutionResult result = backend.executeGraphicsDraw(
            new VulkanCommandContext(1L, "draw-cmd"),
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
                "drawElements",
                VulkanicPrimitiveMode.TRIANGLES,
                3,
                VulkanicIndexType.SHORT,
                1L,
                1,
                0
            )
        );

        assertFalse(result.successful());
        assertEquals(VulkanicGalExecutionRequest.ExecutionStatus.BACKEND_FAILURE, result.status());
        assertTrue(result.detail().contains("align to index type size"));
    }

    @Test
    public void testDrawArraysInstancedRejectsInvalidInstanceCountBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        VulkanicGalExecutionRequest.ExecutionResult result = backend.executeGraphicsDraw(
            new VulkanCommandContext(1L, "draw-cmd"),
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArraysInstanced",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                0
            )
        );

        assertFalse(result.successful());
        assertEquals(VulkanicGalExecutionRequest.ExecutionStatus.BACKEND_FAILURE, result.status());
        assertTrue(result.detail().contains("instanceCount >= 1"));
    }

    @Test
    public void testZeroCountDrawsReturnBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();
        VulkanCommandContext context = new VulkanCommandContext(1L, "draw-cmd");

        assertTrue(backend.executeGraphicsDraw(
            context,
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArrays",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                0,
                1
            )
        ).successful());
        assertTrue(backend.executeGraphicsDraw(
            context,
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
                "drawElements",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                VulkanicIndexType.SHORT,
                0L,
                1,
                0
            )
        ).successful());
    }

    @Test
    public void testVulkanBackendSourceUsesTypedGalDrawRouting() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String drawCoordinatorSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanDrawExecutionCoordinator.java"));

        assertTrue(source.contains("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDraw("),
            "Vulkan backend should expose typed GAL draw entrypoint");
        assertTrue(source.contains("VulkanDrawExecutionCoordinator.SemanticDrawRequest.arrays("),
            "Vulkan arrays draw-call path should be converted into a semantic draw request");
        assertTrue(source.contains("VulkanDrawExecutionCoordinator.SemanticDrawRequest.indexed("),
            "Vulkan indexed draw-call path should be converted into a semantic draw request");
        assertTrue(source.contains("private void executeTypedGalDrawPlan("),
            "Vulkan draw-call path should converge on one typed GAL draw lowering lifecycle");
        assertTrue(source.contains("drawExecution.planLegacyDraw("),
            "Vulkan draw-call path should resolve immutable draw plans through VulkanDrawExecutionCoordinator");
        assertTrue(source.contains("vkCmdDrawIndexed"),
            "Vulkan draw-call path should record vkCmdDrawIndexed");
        assertTrue(source.contains("VulkanGraphicsCommandExecutionCoordinator.IndexBufferBindingRequirement")
                && source.contains("drawExecution.validateBoundIndexRange("),
            "Vulkan indexed draws should track and validate the bound index buffer range through the graphics command execution plan");
        assertTrue(drawCoordinatorSource.contains("Indexed draw exceeds bound index buffer range"),
            "Vulkan indexed draws should fail before recording an out-of-range vkCmdDrawIndexed");
    }
}
