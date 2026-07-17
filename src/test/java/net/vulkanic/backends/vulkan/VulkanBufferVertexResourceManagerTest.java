package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanBufferVertexResourceManagerTest {
    @Test
    void createsBindsLabelsAndDeletesLegacyBuffersWithoutExposingMaps() {
        VulkanBufferVertexResourceManager manager = new VulkanBufferVertexResourceManager();
        int buffer = manager.createLegacyBuffer();

        manager.bindLegacyBuffer(VulkanicAPI.GL_ARRAY_BUFFER, buffer);
        manager.setLegacyBufferExplicitUsage(buffer, 123);
        manager.setLegacyBufferDebugLabel(buffer, "terrain-vertices");

        VulkanBufferVertexResourceManager.BufferStorageSnapshot snapshot =
            manager.requireBoundLegacyBufferSnapshot(VulkanicAPI.GL_ARRAY_BUFFER);
        assertEquals(buffer, snapshot.id());
        assertEquals(VulkanicAPI.GL_ARRAY_BUFFER, snapshot.lastTarget());
        assertEquals(123, snapshot.explicitUsage());
        assertEquals("terrain-vertices", snapshot.debugLabel());

        VulkanBufferVertexResourceManager.BufferDeletionSnapshot deletion = manager.deleteLegacyBuffer(buffer);
        assertEquals(List.of(VulkanicAPI.GL_ARRAY_BUFFER), deletion.unboundTargets());
        assertFalse(manager.containsLegacyBuffer(buffer));
        assertEquals(0, manager.boundLegacyBufferId(VulkanicAPI.GL_ARRAY_BUFFER));
    }

    @Test
    void storageReplacementPlanDetachesPreviousStorageAndPublishesNewSnapshot() {
        VulkanBufferVertexResourceManager manager = new VulkanBufferVertexResourceManager();
        int buffer = manager.createLegacyBuffer();
        manager.bindLegacyBuffer(VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, buffer);

        VulkanBufferVertexResourceManager.BufferStorageReplacementPlan replacement =
            manager.beginStorageReplacement(buffer, VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, 96);
        assertEquals(buffer, replacement.bufferId());
        assertEquals(96, replacement.size());
        assertNull(replacement.previousStorage());

        manager.publishStorage(buffer, VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, 96, null);
        VulkanBufferVertexResourceManager.BufferStorageSnapshot snapshot = manager.requireLegacyBufferSnapshot(buffer);
        assertEquals(96, snapshot.logicalSizeBytes());
        assertEquals(VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, snapshot.lastTarget());
        assertNull(snapshot.buffer());
    }

    @Test
    void mapCopyAndFlushValidationRejectsInvalidRangesAndMappedCopies() {
        VulkanBufferVertexResourceManager manager = new VulkanBufferVertexResourceManager();
        int source = manager.createLegacyBuffer();
        int destination = manager.createLegacyBuffer();
        manager.publishStorage(source, VulkanicAPI.GL_ARRAY_BUFFER, 64, null);
        manager.publishStorage(destination, VulkanicAPI.GL_ARRAY_BUFFER, 64, null);

        VulkanBufferVertexResourceManager.BufferMapRequest request =
            manager.validateMapRequest(source, 8, 16, VulkanicAPI.GL_MAP_WRITE_BIT, 0x0001);
        assertEquals(8, request.offset());
        assertEquals(16, request.length());
        assertFalse(request.read());
        assertTrue(request.write());

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.validateMapRequest(source, 60, 8, VulkanicAPI.GL_MAP_WRITE_BIT, 0x0001)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.validateCopy(source, destination, 0, 60, 8)
        );
        VulkanBufferVertexResourceManager.BufferCopyPlan copy = manager.validateCopy(source, destination, 4, 12, 20);
        assertEquals(4, copy.sourceOffset());
        assertEquals(12, copy.destinationOffset());
        assertEquals(20, copy.size());
    }

    @Test
    void vertexArraySnapshotsAreImmutableAndElementBufferIsDrawResourceOwned() {
        VulkanBufferVertexResourceManager manager = new VulkanBufferVertexResourceManager();
        int vertexBuffer = manager.createLegacyBuffer();
        int indexBuffer = manager.createLegacyBuffer();
        manager.publishStorage(vertexBuffer, VulkanicAPI.GL_ARRAY_BUFFER, 256, fakeBuffer(11L, 256));
        manager.publishStorage(indexBuffer, VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, 128, fakeBuffer(12L, 128));
        manager.bindLegacyBuffer(VulkanicAPI.GL_ARRAY_BUFFER, vertexBuffer);
        manager.bindLegacyBuffer(VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        int vao = manager.createVertexArray();
        manager.bindVertexArray(vao);
        manager.setVertexAttributePointer(2, 3, VulkanicAPI.GL_FLOAT, false, false, 0, 24L);
        manager.enableVertexAttribute(2);

        VulkanDrawExecutionCoordinator.DrawResourceSnapshot snapshot = manager.drawResourceSnapshot();

        manager.bindLegacyBuffer(VulkanicAPI.GL_ARRAY_BUFFER, 0);
        manager.disableVertexAttribute(2);
        manager.setVertexBinding(2, 16, 0L, 0);

        assertEquals(1, snapshot.vertexArray().enabledAttributes().size());
        assertEquals(2, snapshot.vertexArray().enabledAttributes().get(0).index());
        assertEquals(24, snapshot.vertexArray().enabledAttributes().get(0).offset());
        assertEquals(vertexBuffer, snapshot.vertexArray().vertexBuffersForDraw().get(1).bufferId());
        assertEquals(indexBuffer, snapshot.indexBuffer().bufferId());
        assertEquals(128, snapshot.indexBuffer().sizeBytes());
    }

    @Test
    void bufferDeletionInvalidatesDependentVertexArrayReferences() {
        VulkanBufferVertexResourceManager manager = new VulkanBufferVertexResourceManager();
        int vertexBuffer = manager.createLegacyBuffer();
        manager.bindLegacyBuffer(VulkanicAPI.GL_ARRAY_BUFFER, vertexBuffer);
        int vao = manager.createVertexArray();
        manager.bindVertexArray(vao);
        manager.setVertexAttributePointer(0, 3, VulkanicAPI.GL_FLOAT, false, false, 0, 0L);
        manager.enableVertexAttribute(0);

        VulkanBufferVertexResourceManager.BufferDeletionSnapshot deletion = manager.deleteLegacyBuffer(vertexBuffer);
        VulkanDrawExecutionCoordinator.DrawResourceSnapshot snapshot = manager.drawResourceSnapshot();

        assertEquals(1, deletion.invalidatedVertexReferences());
        assertEquals(1, snapshot.vertexArray().enabledAttributes().size());
        assertEquals(1, snapshot.vertexArray().vertexBuffersForDraw().size());
        assertTrue(snapshot.vertexArray().vertexBuffersForDraw().get(0).defaultAttributeBuffer());
    }

    @Test
    void shutdownCleanupClearsBufferAndVertexState() {
        VulkanBufferVertexResourceManager manager = new VulkanBufferVertexResourceManager();
        int buffer = manager.createLegacyBuffer();
        manager.bindLegacyBuffer(VulkanicAPI.GL_ARRAY_BUFFER, buffer);
        manager.createVertexArray();

        manager.clearAll();

        assertEquals(0, manager.legacyBufferCountForTests());
        assertEquals(0, manager.mappedBufferCountForTests());
        assertEquals(0, manager.vertexArrayCountForTests());
        assertEquals(0, manager.boundLegacyBufferId(VulkanicAPI.GL_ARRAY_BUFFER));
        assertEquals(0, manager.boundVertexArray());
    }

    private static VulkanBuffer fakeBuffer(long handle, int size) {
        return new VulkanBuffer(handle, handle + 1000L, 0, size, "test-buffer", () -> {
        });
    }
}
