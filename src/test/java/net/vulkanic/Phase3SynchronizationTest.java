package net.vulkanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3 prep synchronization/resource-barrier metadata seam.
 */
public class Phase3SynchronizationTest {

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
}