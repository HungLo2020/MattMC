package net.sodium.client.render.chunk.data;

import net.sodium.client.gl.arena.PendingUpload;
import net.sodium.client.gl.device.CommandList;
import net.sodium.client.gl.device.MultiDrawBatch;
import net.sodium.client.render.chunk.lists.ChunkRenderList;
import net.sodium.client.render.chunk.buffer.ChunkBufferAllocation;
import net.sodium.client.render.chunk.buffer.ChunkBufferArena;
import net.sodium.client.render.chunk.SharedQuadIndexBuffer;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.render.viewport.CameraTransform;
import net.minecraft.util.NativeLibraryLoader;
import org.lwjgl.system.MemoryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.stream.Stream;

/**
 * The section render data storage stores the chunk buffer allocations of uploaded
 * data on the gpu. There's one storage object per region. It stores information
 * about vertex and optionally index buffer data. The array of buffer allocations is
 * indexed by the region-local section index. The data about the contents of
 * buffer segments is stored in a natively allocated piece of memory referenced
 * by {@code pMeshDataArray}. Rust owns the packed record layout and the hot draw-command assembly path.
 * <p>
 * When the backing buffer (from the chunk buffer arena) is resized, the storage
 * object is notified, and then it updates the changed offsets of the buffer
 * segments. Since the index data's size and alignment directly corresponds to
 * that of the vertex data except for the vertex/index scaling of two thirds,
 * only an offset to the index data within the index data buffer arena is
 * stored.
 * <p>
 * Index and vertex data storage can be managed separately since they may be
 * updated independently of each other (in both directions).
 */
public class SectionRenderDataStorage {
    private static final int OK = 0;
    private static final int REGION_SIZE = RenderRegion.REGION_SIZE;
    private static final int VERTEX_SEGMENT_VALUES = 14;
    private static final ThreadLocal<NativeScratch> NATIVE_SCRATCH = ThreadLocal.withInitial(NativeScratch::new);

    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle ALLOCATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_allocate",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle FREE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_free",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle SET_VERTEX_DATA = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_set_vertex_data",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle SET_LOCAL_BASE_ELEMENT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_set_local_base_element",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle SET_SHARED_BASE_ELEMENT = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_set_shared_base_element",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle SET_BASE_VERTEX = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_set_base_vertex",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle CLEAR_FULL = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_clear_full",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle CLEAR_VERTEX_DATA = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_clear_vertex_data",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle CLEAR_INDEX_DATA = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_clear_index_data",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle FILL_DRAW_COMMANDS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_fill_draw_commands",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle VISIBLE_FACES = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_visible_faces",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final int VERIFY_STATUS = invokeVerify();

    private final @Nullable ChunkBufferAllocation[] vertexAllocations;
    private final @Nullable ChunkBufferAllocation @Nullable [] elementAllocations;
    private @Nullable ChunkBufferAllocation sharedIndexAllocation;
    private int sharedIndexCapacity = 0;
    private boolean needsSharedIndexUpdate = false;
    private final int[] sharedIndexUsage = new int[RenderRegion.REGION_SIZE];

    private final long pMeshDataArray;

    public SectionRenderDataStorage(boolean storesIndices) {
        this.vertexAllocations = new ChunkBufferAllocation[RenderRegion.REGION_SIZE];

        if (storesIndices) {
            this.elementAllocations = new ChunkBufferAllocation[RenderRegion.REGION_SIZE];
        } else {
            this.elementAllocations = null;
        }

        this.pMeshDataArray = invokeAllocate(REGION_SIZE);
        if (this.pMeshDataArray == 0) {
            throw new IllegalStateException("Rust section render data allocation failed");
        }
    }

    public void setVertexData(int localSectionIndex, ChunkBufferAllocation allocation, int[] vertexSegments) {
        ChunkBufferAllocation prev = this.vertexAllocations[localSectionIndex];

        if (prev != null) {
            prev.delete();
        }

        this.vertexAllocations[localSectionIndex] = allocation;

        NativeScratch scratch = NATIVE_SCRATCH.get();
        scratch.writeVertexSegments(vertexSegments);
        check(invokeSetVertexData(this.pMeshDataArray, localSectionIndex, allocation.getOffset(),
                MemoryUtil.memAddress(scratch.vertexSegments), VERTEX_SEGMENT_VALUES),
                "native section render vertex data update");
    }

    public void setIndexData(int localSectionIndex, ChunkBufferAllocation allocation) {
        if (this.elementAllocations == null) {
            throw new IllegalStateException("Cannot set index data on a render data storage that does not store indices");
        }

        ChunkBufferAllocation prev = this.elementAllocations[localSectionIndex];

        if (prev != null) {
            prev.delete();
        }

        this.elementAllocations[localSectionIndex] = allocation;

        check(invokeSetLocalBaseElement(this.pMeshDataArray, localSectionIndex, allocation.getOffset()),
                "native section render local index update");
    }

    public boolean setSharedIndexUsage(int localSectionIndex, int newUsage) {
        var previousUsage = this.sharedIndexUsage[localSectionIndex];
        if (previousUsage == newUsage) {
            return false;
        }

        // mark for update if usage is down from max (may need to shrink buffer)
        // or if usage increased beyond the max (need to grow buffer)
        boolean newlyUsingSharedIndexBuffer = false;
        if (newUsage < previousUsage && previousUsage == this.sharedIndexCapacity ||
                newUsage > this.sharedIndexCapacity ||
                newUsage > 0 && this.sharedIndexAllocation == null) {
            this.needsSharedIndexUpdate = true;
        } else {
            // just set the base element since no update is happening
            var sharedBaseElement = this.sharedIndexAllocation.getOffset();
            check(invokeSetSharedBaseElement(this.pMeshDataArray, localSectionIndex, sharedBaseElement),
                    "native section render shared index update");

            if (previousUsage == 0 && newUsage > 0) {
                newlyUsingSharedIndexBuffer = true;
            }
        }

        this.sharedIndexUsage[localSectionIndex] = newUsage;

        return newlyUsingSharedIndexBuffer;
    }

    public boolean needsSharedIndexUpdate() {
        return this.needsSharedIndexUpdate;
    }

    /**
     * Updates the shared index data buffer to match the current usage.
     *
     * @param arena The buffer arena to allocate the new buffer from
     * @return true if the arena resized itself
     */
    public boolean updateSharedIndexData(CommandList commandList, ChunkBufferArena arena) {
        // assumes this.needsSharedIndexUpdate is true when this is called
        this.needsSharedIndexUpdate = false;

        // determine the new required capacity
        int newCapacity = 0;
        for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
            newCapacity = Math.max(newCapacity, this.sharedIndexUsage[i]);
        }
        if (newCapacity == this.sharedIndexCapacity) {
            return false;
        }

        this.sharedIndexCapacity = newCapacity;

        // remove the existing allocation and exit if we don't need to create a new one
        if (this.sharedIndexAllocation != null) {
            this.sharedIndexAllocation.delete();
            this.sharedIndexAllocation = null;
        }
        if (this.sharedIndexCapacity == 0) {
            return false;
        }

        // add some base-level capacity to avoid resizing the buffer too often
        if (this.sharedIndexCapacity < 128) {
            this.sharedIndexCapacity += 32;
        }

        // create and upload a new shared index buffer
        var buffer = SharedQuadIndexBuffer.createIndexBuffer(SharedQuadIndexBuffer.IndexType.INTEGER, this.sharedIndexCapacity);
        var pendingUpload = new PendingUpload(buffer);
        var bufferChanged = arena.upload(commandList, Stream.of(pendingUpload));
        this.sharedIndexAllocation = pendingUpload.getResult();
        buffer.free();

        // only write the base elements now if we're not going to do so again later because of the buffer resize
        if (!bufferChanged) {
            var sharedBaseElement = this.sharedIndexAllocation.getOffset();
            for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
                if (this.sharedIndexUsage[i] > 0) {
                    check(invokeSetSharedBaseElement(this.pMeshDataArray, i, sharedBaseElement),
                            "native section render shared index update");
                }
            }
        }

        return bufferChanged;
    }

    private boolean storesIndexData() {
        return this.elementAllocations != null;
    }

    public void removeIndexData(int localSectionIndex) {
        if (!this.storesIndexData()) {
            throw new IllegalStateException("Cannot remove index data on a render data storage that does not store indices");
        }
        this.removeData(localSectionIndex, false, true);
    }

    public void removeVertexData(int localSectionIndex) {
        this.removeData(localSectionIndex, true, false);
    }

    public void removeData(int localSectionIndex) {
        this.removeData(localSectionIndex, true, true);
    }

    private void removeData(int localSectionIndex, boolean removeVertexData, boolean removeIndexData) {
        if (removeVertexData) {
            ChunkBufferAllocation prev = this.vertexAllocations[localSectionIndex];
            if (prev != null) {
                prev.delete();
                this.vertexAllocations[localSectionIndex] = null;
            }
        }
        if (removeIndexData && this.storesIndexData()) {
            ChunkBufferAllocation prev = this.elementAllocations[localSectionIndex];

            if (prev != null) {
                prev.delete();
                this.elementAllocations[localSectionIndex] = null;
            }

            this.setSharedIndexUsage(localSectionIndex, 0);
        }

        if ((removeIndexData || !this.storesIndexData()) && removeVertexData) {
            check(invokeClearFull(this.pMeshDataArray, localSectionIndex), "native section render data clear");
        } else if (removeVertexData) {
            check(invokeClearVertexData(this.pMeshDataArray, localSectionIndex), "native section render vertex data clear");
        } else if (removeIndexData) {
            check(invokeClearIndexData(this.pMeshDataArray, localSectionIndex), "native section render index data clear");
        }
    }

    public void onBufferResized() {
        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            this.updateMeshes(sectionIndex);
        }
    }

    private void updateMeshes(int sectionIndex) {
        var allocation = this.vertexAllocations[sectionIndex];

        if (allocation == null) {
            return;
        }

        long offset = allocation.getOffset();
        check(invokeSetBaseVertex(this.pMeshDataArray, sectionIndex, offset),
                "native section render base vertex update");
    }

    public void onIndexBufferResized() {
        long sharedBaseElement = 0;
        if (this.sharedIndexAllocation != null) {
            sharedBaseElement = this.sharedIndexAllocation.getOffset();
        }

        for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
            if (this.sharedIndexUsage[i] > 0) {
                // update index sharing sections to use the new shared index buffer's offset
                check(invokeSetSharedBaseElement(this.pMeshDataArray, i, sharedBaseElement),
                        "native section render shared index update");
            } else if (this.elementAllocations != null) {
                var allocation = this.elementAllocations[i];

                if (allocation != null) {
                    check(invokeSetLocalBaseElement(this.pMeshDataArray, i, allocation.getOffset()),
                            "native section render local index update");
                }
            }
        }
    }

    public void fillDrawCommandBuffer(MultiDrawBatch batch, RenderRegion region, ChunkRenderList renderList,
            CameraTransform camera, boolean reverseSections, boolean useBlockFaceCulling,
            boolean useIndexedTessellation) {
        batch.isFilled = true;

        int sectionCount = renderList.getSectionsWithGeometryCount();
        if (sectionCount == 0) {
            batch.size = 0;
            return;
        }

        NativeScratch scratch = NATIVE_SCRATCH.get();
        scratch.ensureSectionCapacity(sectionCount);
        renderList.copySectionsWithGeometry(scratch.sectionIndices);

        check(invokeFillDrawCommands(
                this.pMeshDataArray,
                MemoryUtil.memAddress(scratch.sectionIndices),
                sectionCount,
                reverseSections ? 1 : 0,
                region.getChunkX(),
                region.getChunkY(),
                region.getChunkZ(),
                camera.intX,
                camera.intY,
                camera.intZ,
                useBlockFaceCulling ? 1 : 0,
                useIndexedTessellation ? 1 : 0,
                batch.pElementPointer,
                batch.pElementCount,
                batch.pBaseVertex,
                batch.capacity,
                MemoryUtil.memAddress(scratch.outputSize)), "native section render draw-command assembly");
        batch.size = scratch.outputSize.getInt(0);
    }

    public String diagnosticMeshReadinessSignature(ChunkRenderList renderList, boolean reverseSections,
            boolean useIndexedTessellation) {
        int sectionCount = renderList.getSectionsWithGeometryCount();
        NativeScratch scratch = NATIVE_SCRATCH.get();
        scratch.ensureSectionCapacity(sectionCount);
        renderList.copySectionsWithGeometry(scratch.sectionIndices);

        CRC32 crc = new CRC32();
        int vertexReady = 0;
        int indexReady = 0;
        int missing = 0;
        updateCrcInt(crc, sectionCount);
        updateCrcBool(crc, reverseSections);
        updateCrcBool(crc, useIndexedTessellation);

        for (int ordinal = 0; ordinal < sectionCount; ordinal++) {
            int sectionOrdinal = reverseSections ? sectionCount - 1 - ordinal : ordinal;
            int sectionIndex = scratch.sectionIndices.get(sectionOrdinal) & 0xFF;
            ChunkBufferAllocation vertexAllocation = this.vertexAllocations[sectionIndex];
            ChunkBufferAllocation indexAllocation = this.elementAllocations == null ? null : this.elementAllocations[sectionIndex];
            boolean hasVertex = vertexAllocation != null;
            boolean hasIndex = useIndexedTessellation ? indexAllocation != null : this.sharedIndexUsage[sectionIndex] > 0;
            if (hasVertex) {
                vertexReady++;
            }
            if (hasIndex) {
                indexReady++;
            }
            if (!hasVertex || !hasIndex) {
                missing++;
            }

            updateCrcInt(crc, sectionIndex);
            updateCrcBool(crc, hasVertex);
            updateCrcBool(crc, hasIndex);
            updateCrcLong(crc, vertexAllocation == null ? -1L : vertexAllocation.getOffset());
            updateCrcLong(crc, vertexAllocation == null ? -1L : vertexAllocation.getLength());
            updateCrcLong(crc, indexAllocation == null ? -1L : indexAllocation.getOffset());
            updateCrcLong(crc, indexAllocation == null ? -1L : indexAllocation.getLength());
            updateCrcInt(crc, this.sharedIndexUsage[sectionIndex]);
        }

        return String.format(
            Locale.ROOT,
            "mesh=%d;v=%d;i=%d;miss=%d;shared=%d;idx=%s;h=%s",
            sectionCount,
            vertexReady,
            indexReady,
            missing,
            this.sharedIndexCapacity,
            Boolean.toString(useIndexedTessellation),
            Long.toHexString(crc.getValue())
        );
    }

    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        check(VERIFY_STATUS, "native section render data verification");
        try {
            return (int) VISIBLE_FACES.invokeExact(originX, originY, originZ, chunkX, chunkY, chunkZ);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render visible-face calculation downcall failed", throwable);
        }
    }

    public void delete() {
        deleteAllocations(this.vertexAllocations);

        if (this.elementAllocations != null) {
            deleteAllocations(this.elementAllocations);
        }

        if (this.sharedIndexAllocation != null) {
            this.sharedIndexAllocation.delete();
        }

        check(invokeFree(this.pMeshDataArray, REGION_SIZE), "native section render data free");
    }

    private static void deleteAllocations(ChunkBufferAllocation @NotNull [] allocations) {
        for (var allocation : allocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        Arrays.fill(allocations, null);
    }

    private static int invokeVerify() {
        try {
            return (int) VERIFY.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render data verification downcall failed", throwable);
        }
    }

    private static long invokeAllocate(int count) {
        check(VERIFY_STATUS, "native section render data verification");
        try {
            return (long) ALLOCATE.invokeExact(count);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render data allocation downcall failed", throwable);
        }
    }

    private static int invokeFree(long pointer, int count) {
        try {
            return (int) FREE.invokeExact(pointer, count);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render data free downcall failed", throwable);
        }
    }

    private static int invokeSetVertexData(long baseAddress, int sectionIndex, long baseVertex,
            long vertexSegmentsAddress, int vertexSegmentsLength) {
        try {
            return (int) SET_VERTEX_DATA.invokeExact(baseAddress, sectionIndex, baseVertex,
                    MemorySegment.ofAddress(vertexSegmentsAddress), vertexSegmentsLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render vertex data downcall failed", throwable);
        }
    }

    private static int invokeSetLocalBaseElement(long baseAddress, int sectionIndex, long value) {
        try {
            return (int) SET_LOCAL_BASE_ELEMENT.invokeExact(baseAddress, sectionIndex, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render local index downcall failed", throwable);
        }
    }

    private static int invokeSetSharedBaseElement(long baseAddress, int sectionIndex, long value) {
        try {
            return (int) SET_SHARED_BASE_ELEMENT.invokeExact(baseAddress, sectionIndex, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render shared index downcall failed", throwable);
        }
    }

    private static int invokeSetBaseVertex(long baseAddress, int sectionIndex, long value) {
        try {
            return (int) SET_BASE_VERTEX.invokeExact(baseAddress, sectionIndex, value);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render base vertex downcall failed", throwable);
        }
    }

    private static int invokeClearFull(long baseAddress, int sectionIndex) {
        try {
            return (int) CLEAR_FULL.invokeExact(baseAddress, sectionIndex);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render full clear downcall failed", throwable);
        }
    }

    private static int invokeClearVertexData(long baseAddress, int sectionIndex) {
        try {
            return (int) CLEAR_VERTEX_DATA.invokeExact(baseAddress, sectionIndex);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render vertex clear downcall failed", throwable);
        }
    }

    private static int invokeClearIndexData(long baseAddress, int sectionIndex) {
        try {
            return (int) CLEAR_INDEX_DATA.invokeExact(baseAddress, sectionIndex);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render index clear downcall failed", throwable);
        }
    }

    private static int invokeFillDrawCommands(long baseAddress, long sectionIndicesAddress, int sectionCount,
            int reverseSections, int regionChunkX, int regionChunkY, int regionChunkZ, int cameraX,
            int cameraY, int cameraZ, int useBlockFaceCulling, int useIndexedTessellation,
            long elementPointerAddress, long elementCountAddress, long baseVertexAddress, int drawCapacity,
            long outputSizeAddress) {
        try {
            return (int) FILL_DRAW_COMMANDS.invokeExact(
                    baseAddress,
                    MemorySegment.ofAddress(sectionIndicesAddress),
                    sectionCount,
                    reverseSections,
                    regionChunkX,
                    regionChunkY,
                    regionChunkZ,
                    cameraX,
                    cameraY,
                    cameraZ,
                    useBlockFaceCulling,
                    useIndexedTessellation,
                    elementPointerAddress,
                    elementCountAddress,
                    baseVertexAddress,
                    drawCapacity,
                    MemorySegment.ofAddress(outputSizeAddress));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render draw-command assembly downcall failed", throwable);
        }
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static void updateCrcBool(CRC32 crc, boolean value) {
        updateCrcInt(crc, value ? 1 : 0);
    }

    private static void updateCrcInt(CRC32 crc, int value) {
        crc.update(value & 0xFF);
        crc.update((value >>> 8) & 0xFF);
        crc.update((value >>> 16) & 0xFF);
        crc.update((value >>> 24) & 0xFF);
    }

    private static void updateCrcLong(CRC32 crc, long value) {
        updateCrcInt(crc, (int) value);
        updateCrcInt(crc, (int) (value >>> 32));
    }

    private static final class NativeScratch {
        private ByteBuffer vertexSegments = allocate(VERTEX_SEGMENT_VALUES * Integer.BYTES);
        private ByteBuffer sectionIndices = allocate(REGION_SIZE);
        private ByteBuffer outputSize = allocate(Integer.BYTES);

        void writeVertexSegments(int[] segments) {
            if (segments.length != VERTEX_SEGMENT_VALUES) {
                throw new IllegalArgumentException("Expected " + VERTEX_SEGMENT_VALUES
                        + " native section vertex segment values, got " + segments.length);
            }

            for (int index = 0; index < VERTEX_SEGMENT_VALUES; index++) {
                this.vertexSegments.putInt(index * Integer.BYTES, segments[index]);
            }
        }

        void ensureSectionCapacity(int sectionCount) {
            if (this.sectionIndices.capacity() >= sectionCount) {
                return;
            }

            this.sectionIndices = allocate(sectionCount);
        }

        private static ByteBuffer allocate(int bytes) {
            return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        }
    }
}
