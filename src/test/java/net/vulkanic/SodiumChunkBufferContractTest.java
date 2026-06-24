package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SodiumChunkBufferContractTest {
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

    @Test
    public void testChunkArenaContractExistsAndGlArenaImplementsIt() throws IOException {
        String arenaContract = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/render/chunk/buffer/ChunkBufferArena.java"));
        String allocationContract = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/render/chunk/buffer/ChunkBufferAllocation.java"));
        String glArena = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/gl/arena/GlBufferArena.java"));
        String glSegment = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/gl/arena/GlBufferSegment.java"));

        assertTrue(arenaContract.contains("interface ChunkBufferArena"),
            "Chunk buffer arenas should have a backend-neutral contract");
        assertTrue(arenaContract.contains("GpuBuffer gpuBufferView"),
            "The neutral arena must expose a GpuBuffer view for Vulkan terrain submissions");
        assertTrue(arenaContract.contains("GlBuffer legacyGlBuffer"),
            "The neutral arena should keep the existing OpenGL tessellation bridge explicit");
        assertTrue(allocationContract.contains("interface ChunkBufferAllocation"),
            "Chunk buffer allocations should have a backend-neutral contract");
        assertTrue(glArena.contains("implements ChunkBufferArena"),
            "The current GL arena should be only one implementation of the neutral arena contract");
        assertTrue(glSegment.contains("implements ChunkBufferAllocation"),
            "The current GL segment should be only one implementation of the neutral allocation contract");
    }

    @Test
    public void testVulkanTerrainRendererDoesNotConstructLegacyGlBufferWrappers() throws IOException {
        String renderer = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/render/chunk/DefaultChunkRenderer.java"));

        assertFalse(renderer.contains("LegacyHandleGlBuffer"),
            "Vulkan terrain renderer should not directly construct legacy GL buffer wrappers");
        assertFalse(renderer.contains("wrapLegacyBuffer("),
            "Legacy buffer adaptation should live behind the chunk buffer arena contract");
        assertTrue(renderer.contains("getGeometryGpuBuffer("),
            "Vulkan terrain should request geometry buffers through RenderRegion device resources");
        assertTrue(renderer.contains("getIndexGpuBuffer("),
            "Vulkan terrain should request index buffers through RenderRegion device resources");
        assertTrue(renderer.contains("sharedIndexBuffer.gpuBufferView("),
            "Vulkan terrain should request the shared quad index buffer through its buffer view seam");
    }

    @Test
    public void testSectionStorageUsesNeutralAllocations() throws IOException {
        String storage = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/render/chunk/data/SectionRenderDataStorage.java"));

        assertFalse(storage.contains("GlBufferSegment"),
            "Section mesh metadata should not name GL buffer segments directly");
        assertFalse(storage.contains("GlBufferArena"),
            "Shared index metadata updates should not require a GL arena type");
        assertTrue(storage.contains("ChunkBufferAllocation"),
            "Section mesh metadata should store neutral chunk buffer allocations");
        assertTrue(storage.contains("ChunkBufferArena"),
            "Shared index metadata updates should allocate through the neutral chunk buffer arena");
    }

    @Test
    public void testOpenGlTessellationPathStillUsesLegacyBufferViewExplicitly() throws IOException {
        String region = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/render/chunk/region/RenderRegion.java"));
        String renderer = Files.readString(SRC_MAIN_JAVA.resolve(
            "net/sodium/client/render/chunk/DefaultChunkRenderer.java"));

        assertTrue(region.contains("legacyGlBuffer()"),
            "RenderRegion should keep the legacy GL buffer view explicit for the OpenGL tessellation path");
        assertTrue(renderer.contains("RenderTessellationBinding.forVertexBuffer(resources.getGeometryBuffer()"),
            "OpenGL tessellation should continue using the existing GL buffer path");
        assertTrue(renderer.contains(": resources.getIndexBuffer())"),
            "OpenGL indexed tessellation should continue using the existing GL index buffer path");
    }
}
