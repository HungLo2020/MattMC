package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLCommandContext;
import net.vulkanic.backends.vulkan.VulkanBackend;
import net.vulkanic.backends.vulkan.VulkanCommandContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.drawArrays(OpenGLCommandContext.IMMEDIATE, VulkanicAPI.GL_TRIANGLES, 0, 3)
        );

        assertTrue(exception.getMessage().contains("drawArrays requires VulkanCommandContext"));
    }

    @Test
    public void testDrawElementsRejectsUnsupportedIndexTypeBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.drawElements(new VulkanCommandContext(1L, "draw-cmd"),
                VulkanicAPI.GL_TRIANGLES,
                3,
                0xDEAD,
                0L)
        );

        assertTrue(exception.getMessage().contains("Unsupported drawElements index type"));
    }

    @Test
    public void testDrawElementsRejectsMisalignedIndexOffsetBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.drawElements(new VulkanCommandContext(1L, "draw-cmd"),
                VulkanicAPI.GL_TRIANGLES,
                3,
                VulkanicAPI.GL_UNSIGNED_SHORT,
                1L)
        );

        assertTrue(exception.getMessage().contains("align to index type size"));
    }

    @Test
    public void testDrawArraysInstancedRejectsInvalidInstanceCountBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.drawArraysInstanced(new VulkanCommandContext(1L, "draw-cmd"),
                VulkanicAPI.GL_TRIANGLES,
                0,
                3,
                0)
        );

        assertTrue(exception.getMessage().contains("instanceCount >= 1"));
    }

    @Test
    public void testVulkanBackendSourceUsesLegacyDrawRouting() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String drawCoordinatorSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanDrawExecutionCoordinator.java"));

        assertTrue(source.contains("public void drawArrays("),
            "Vulkan backend should expose drawArrays entrypoint");
        assertTrue(source.contains("public void drawElements("),
            "Vulkan backend should expose drawElements entrypoint");
        assertTrue(source.contains("VulkanDrawExecutionCoordinator.SemanticDrawRequest.arrays("),
            "Vulkan arrays draw-call path should be converted into a semantic draw request");
        assertTrue(source.contains("VulkanDrawExecutionCoordinator.SemanticDrawRequest.indexed("),
            "Vulkan indexed draw-call path should be converted into a semantic draw request");
        assertTrue(source.contains("private void executeLegacyDraw("),
            "Vulkan draw-call path should converge on one legacy draw execution lifecycle");
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
