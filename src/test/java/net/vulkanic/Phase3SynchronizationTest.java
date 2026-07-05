package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBackend;
import net.vulkanic.backends.vulkan.VulkanCommandContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 prep synchronization/resource-barrier metadata seam.
 */
public class Phase3SynchronizationTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @AfterEach
    public void tearDown() {
        resetBackendStateUnchecked();
    }

    @Test
    public void testGraphicsBackendHasApplyResourceBarriersMethod() throws NoSuchMethodException {
        assertNotNull(GraphicsBackend.class.getMethod(
            "applyResourceBarriers",
            CommandContext.class,
            VulkanicResourceBarriers.class));
    }

    @Test
    public void testVulkanicAPIHasApplyResourceBarriersMethod() throws NoSuchMethodException {
        assertNotNull(VulkanicAPI.class.getMethod(
            "applyResourceBarriers",
            CommandContext.class,
            VulkanicResourceBarriers.class));
    }

    @Test
    public void testBarrierSetMapsToOpenGLBits() {
        VulkanicResourceBarriers barriers = VulkanicResourceBarriers.of(
            VulkanicResourceBarriers.Barrier.SHADER_IMAGE_ACCESS,
            VulkanicResourceBarriers.Barrier.TEXTURE_FETCH,
            VulkanicResourceBarriers.Barrier.SHADER_STORAGE);

        int expected = VulkanicAPI.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
            | VulkanicAPI.GL_TEXTURE_FETCH_BARRIER_BIT
            | VulkanicAPI.GL_SHADER_STORAGE_BARRIER_BIT;

        assertEquals(expected, barriers.toOpenGLBarrierBits());
    }

    @Test
    public void testOpenGLBarrierBitsMapBackToTypedBarrierSet() {
        int bits = VulkanicAPI.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
            | VulkanicAPI.GL_TEXTURE_FETCH_BARRIER_BIT;

        java.util.Optional<VulkanicResourceBarriers> parsed = VulkanicResourceBarriers.fromOpenGLBits(bits);

        assertTrue(parsed.isPresent());
        assertTrue(parsed.get().barriers().contains(VulkanicResourceBarriers.Barrier.SHADER_IMAGE_ACCESS));
        assertTrue(parsed.get().barriers().contains(VulkanicResourceBarriers.Barrier.TEXTURE_FETCH));
        assertEquals(bits, parsed.get().toOpenGLBarrierBits());
    }

    @Test
    public void testUnknownOpenGLBarrierBitsAreNotMisclassified() {
        assertTrue(VulkanicResourceBarriers.fromOpenGLBits(0).isEmpty());
        assertTrue(VulkanicResourceBarriers.fromOpenGLBits(0x40000000).isEmpty());
    }

    @Test
    public void testBarrierSetDeduplicatesEntries() {
        VulkanicResourceBarriers barriers = VulkanicResourceBarriers.of(
            VulkanicResourceBarriers.Barrier.TEXTURE_FETCH,
            VulkanicResourceBarriers.Barrier.TEXTURE_FETCH,
            VulkanicResourceBarriers.Barrier.SHADER_IMAGE_ACCESS);

        assertEquals(2, barriers.barriers().size());
        assertTrue(barriers.barriers().contains(VulkanicResourceBarriers.Barrier.TEXTURE_FETCH));
        assertTrue(barriers.barriers().contains(VulkanicResourceBarriers.Barrier.SHADER_IMAGE_ACCESS));
    }

    @Test
    public void testBarrierFactoryRejectsNullFirst() {
        assertThrows(NullPointerException.class,
            () -> VulkanicResourceBarriers.of(null));
    }

    @Test
    public void testBarrierFactoryRejectsNullEntry() {
        assertThrows(NullPointerException.class,
            () -> VulkanicResourceBarriers.of(
                VulkanicResourceBarriers.Barrier.SHADER_IMAGE_ACCESS,
                (VulkanicResourceBarriers.Barrier) null));
    }

    @Test
    public void testComputePresetIncludesAllCurrentDomains() {
        VulkanicResourceBarriers preset = VulkanicResourceBarriers.computeWritesVisibleToTextureSampling();

        assertTrue(preset.barriers().contains(VulkanicResourceBarriers.Barrier.SHADER_IMAGE_ACCESS));
        assertTrue(preset.barriers().contains(VulkanicResourceBarriers.Barrier.TEXTURE_FETCH));
        assertTrue(preset.barriers().contains(VulkanicResourceBarriers.Barrier.SHADER_STORAGE));
    }

    @Test
    public void testVulkanBarrierMaskMappingIncludesShaderStagesAndAccessMasks() throws Exception {
        Method mappingMethod = VulkanBackend.class.getDeclaredMethod(
            "toVkBarrierMasks",
            VulkanicResourceBarriers.class
        );
        mappingMethod.setAccessible(true);

        Object masks = mappingMethod.invoke(
            null,
            VulkanicResourceBarriers.computeWritesVisibleToTextureSampling()
        );

        int srcStageMask = invokeIntAccessor(masks, "srcStageMask");
        int dstStageMask = invokeIntAccessor(masks, "dstStageMask");
        int srcAccessMask = invokeIntAccessor(masks, "srcAccessMask");
        int dstAccessMask = invokeIntAccessor(masks, "dstAccessMask");

        assertTrue((srcStageMask & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT) != 0,
            "Source stage mask should include compute shader stage");
        assertTrue((dstStageMask & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT) != 0,
            "Destination stage mask should include fragment shader stage");
        assertTrue((srcAccessMask & VK10.VK_ACCESS_SHADER_WRITE_BIT) != 0,
            "Source access mask should include shader write visibility");
        assertTrue((dstAccessMask & VK10.VK_ACCESS_SHADER_READ_BIT) != 0,
            "Destination access mask should include shader read visibility");
    }

    @Test
    public void testVulkanRenderPassBarrierMaskMappingRemovesIllegalNonGraphicsStages() throws Exception {
        Method mappingMethod = VulkanBackend.class.getDeclaredMethod(
            "toVkRenderPassBarrierMasks",
            VulkanicResourceBarriers.class
        );
        mappingMethod.setAccessible(true);

        Object masks = mappingMethod.invoke(
            null,
            VulkanicResourceBarriers.computeWritesVisibleToTextureSampling()
        );

        int srcStageMask = invokeIntAccessor(masks, "srcStageMask");
        int dstStageMask = invokeIntAccessor(masks, "dstStageMask");
        int srcAccessMask = invokeIntAccessor(masks, "srcAccessMask");
        int dstAccessMask = invokeIntAccessor(masks, "dstAccessMask");

        assertEquals(0, srcStageMask & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            "Render-pass barriers must not include compute source stages");
        assertEquals(0, dstStageMask & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            "Render-pass barriers must not include compute destination stages");
        assertEquals(0, srcStageMask & VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
            "Render-pass barriers must not include transfer source stages");
        assertEquals(0, srcAccessMask & VK10.VK_ACCESS_TRANSFER_WRITE_BIT,
            "Render-pass barriers must not include transfer writes");
        assertEquals(0, srcStageMask & VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,
            "Render-pass barriers must not include non-framebuffer vertex stages");
        assertEquals(0, dstStageMask & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            "Render-pass barriers must not include non-framebuffer fragment stages");
        assertEquals(0, srcAccessMask & VK10.VK_ACCESS_SHADER_WRITE_BIT,
            "Render-pass barriers must not include shader writes");
        assertEquals(0, dstAccessMask & VK10.VK_ACCESS_SHADER_READ_BIT,
            "Render-pass barriers must not include shader reads");
        assertTrue((srcStageMask & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0,
            "Render-pass barrier source stages should include framebuffer-space attachment stages");
        assertTrue((dstStageMask & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0,
            "Render-pass barrier destination stages should include framebuffer-space attachment stages");
        assertTrue((srcAccessMask & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0,
            "Render-pass barrier source access should include attachment writes");
        assertTrue((dstAccessMask & VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT) != 0,
            "Render-pass barrier destination access should include attachment reads");
    }

    @Test
    public void testVulkanApplyResourceBarriersValidatesContextAndBarriersBeforeNativeChecks() {
        VulkanBackend backend = new VulkanBackend();
        VulkanicResourceBarriers barriers = VulkanicResourceBarriers.of(VulkanicResourceBarriers.Barrier.TEXTURE_FETCH);

        IllegalArgumentException contextFailure = assertThrows(
            IllegalArgumentException.class,
            () -> backend.applyResourceBarriers(null, barriers)
        );
        assertTrue(contextFailure.getMessage().contains("requires VulkanCommandContext"));

        NullPointerException nullBarrierFailure = assertThrows(
            NullPointerException.class,
            () -> backend.applyResourceBarriers(new VulkanCommandContext(1L, "test-context"), null)
        );
        assertTrue(nullBarrierFailure.getMessage().contains("barriers must not be null"));
    }

    @Test
    public void testVulkanBackendSourceUsesNativePipelineBarrierForResourceBarriers() throws Exception {
        String source = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("spine.applyResourceBarriers(commandBufferHandle, safeBarriers);"),
            "Vulkan backend should delegate barrier application to native spine");
        assertTrue(source.contains("VulkanicResourceBarriers.fromOpenGLBits(barriers)"),
            "Vulkan raw memoryBarrier calls should be translated into typed barriers when possible");
        assertTrue(source.contains("spine.applyConservativeMemoryBarrier(commandBufferHandle);"),
            "Vulkan raw memoryBarrier calls with unsupported bits should still emit a conservative native barrier");
        assertTrue(source.contains("VK10.vkCmdPipelineBarrier("),
            "Vulkan native barrier path should use vkCmdPipelineBarrier");
        assertTrue(source.contains("toVkRenderPassBarrierMasks(barriers)"),
            "In-render-pass barriers should use render-pass-compatible masks");
        assertTrue(source.contains("renderPassRecording ? VK10.VK_DEPENDENCY_BY_REGION_BIT : 0"),
            "In-render-pass barriers should opt into BY_REGION self-dependency synchronization");
        assertTrue(source.contains("toVkRenderPassConservativeBarrierMasks()"),
            "Conservative memory barriers should also be legal inside an active render pass");
        assertFalse(source.contains("Vulkan-native resource barrier mapping is not implemented yet."),
            "Vulkan barrier mapping should no longer be marked unsupported");
    }

    private static int invokeIntAccessor(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (Integer) method.invoke(target);
    }

    private static void resetBackendStateUnchecked() {
        try {
            for (String fieldName : new String[]{"backend", "rawVulkanBackend"}) {
                Field field = VulkanicAPI.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to reset VulkanicAPI backend state", exception);
        }
    }
}
