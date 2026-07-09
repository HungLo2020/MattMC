package net.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.core.SectionPos;
import net.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class NativeRenderListSorter {
    private static final int OK = 0;
    private static final int SECTION_MAP_LONGS = RenderRegion.REGION_SIZE / Long.SIZE;
    private static final int SECTION_OUTPUT_STRIDE = RenderRegion.REGION_SIZE;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_render_list_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle SORT_SECTIONS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_render_list_sort_sections",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle SORT_REGIONS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_render_list_sort_regions",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle PREPARE_FRAME = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_render_list_prepare_frame",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final int VERIFY_STATUS = invokeVerify();

    private NativeRenderListSorter() {
    }

    public static void verifyAvailable() {
        check(VERIFY_STATUS, "native render-list sorter verification");
    }

    public static SortedRenderLists prepareRenderLists(ObjectArrayList<ChunkRenderList> unsorted,
            SectionPos sectionPos) {
        verifyAvailable();

        int size = unsorted.size();
        if (size == 0) {
            return new SortedRenderLists(new ObjectArrayList<>());
        }

        int cameraRegionX = sectionPos.getX() >> RenderRegion.REGION_WIDTH_SH;
        int cameraRegionY = sectionPos.getY() >> RenderRegion.REGION_HEIGHT_SH;
        int cameraRegionZ = sectionPos.getZ() >> RenderRegion.REGION_LENGTH_SH;
        Scratch scratch = SCRATCH.get();
        scratch.ensureCapacity(size);

        int dirtyCount = 0;
        int sectionBatchCount = 0;

        for (int index = 0; index < size; index++) {
            ChunkRenderList list = unsorted.get(index);
            RenderRegion region = list.getRegion();
            int coordinateOffset = index * 3 * Integer.BYTES;
            scratch.regionCoordinates.putInt(coordinateOffset, region.getX());
            scratch.regionCoordinates.putInt(coordinateOffset + Integer.BYTES, region.getY());
            scratch.regionCoordinates.putInt(coordinateOffset + 2 * Integer.BYTES, region.getZ());

            int relativeCameraSectionX = list.getRelativeCameraSectionX(sectionPos);
            int relativeCameraSectionY = list.getRelativeCameraSectionY(sectionPos);
            int relativeCameraSectionZ = list.getRelativeCameraSectionZ(sectionPos);

            if (list.needsRenderPreparation(relativeCameraSectionX, relativeCameraSectionY,
                    relativeCameraSectionZ)) {
                scratch.dirtyLists[dirtyCount] = list;
                scratch.dirtyRelativeCameraSectionX[dirtyCount] = relativeCameraSectionX;
                scratch.dirtyRelativeCameraSectionY[dirtyCount] = relativeCameraSectionY;
                scratch.dirtyRelativeCameraSectionZ[dirtyCount] = relativeCameraSectionZ;
                dirtyCount++;

                if (list.needsNativeSectionSort()) {
                    scratch.sectionBatchLists[sectionBatchCount] = list;
                    scratch.writeSectionMap(sectionBatchCount, list.getSectionsWithGeometryMap());
                    scratch.writeSectionCamera(sectionBatchCount, relativeCameraSectionX, relativeCameraSectionY,
                            relativeCameraSectionZ);
                    sectionBatchCount++;
                }
            }
        }

        check(invokePrepareFrame(
                MemoryUtil.memAddress(scratch.regionCoordinates),
                size,
                MemoryUtil.memAddress(scratch.regionIndices),
                size,
                MemoryUtil.memAddress(scratch.sectionMaps),
                sectionBatchCount,
                MemoryUtil.memAddress(scratch.sectionCameraPositions),
                MemoryUtil.memAddress(scratch.sectionCounts),
                MemoryUtil.memAddress(scratch.sectionOutputs),
                SECTION_OUTPUT_STRIDE,
                cameraRegionX,
                cameraRegionY,
                cameraRegionZ
        ), "native batched render-list preparation");

        ObjectArrayList<ChunkRenderList> sorted = new ObjectArrayList<>(size);
        for (int index = 0; index < size; index++) {
            sorted.add(unsorted.get(scratch.regionIndices.getInt(index * Integer.BYTES)));
        }

        for (int index = 0; index < dirtyCount; index++) {
            scratch.dirtyLists[index].commitRenderPreparation(scratch.dirtyRelativeCameraSectionX[index],
                    scratch.dirtyRelativeCameraSectionY[index], scratch.dirtyRelativeCameraSectionZ[index]);
            scratch.dirtyLists[index] = null;
        }

        for (int index = 0; index < sectionBatchCount; index++) {
            int count = scratch.sectionCounts.getInt(index * Integer.BYTES);
            scratch.sectionBatchLists[index].applyNativeSortedSections(scratch.sectionOutputs,
                    index * SECTION_OUTPUT_STRIDE, count);
            scratch.sectionBatchLists[index] = null;
        }

        return new SortedRenderLists(sorted);
    }

    public static int sortSections(long[] sectionMap, byte[] outputSections,
            int relativeCameraSectionX, int relativeCameraSectionY, int relativeCameraSectionZ) {
        verifyAvailable();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sectionMapSegment = arena.allocate(ValueLayout.JAVA_LONG, sectionMap.length);
            MemorySegment outputSectionsSegment = arena.allocate(ValueLayout.JAVA_BYTE, outputSections.length);
            MemorySegment countSegment = arena.allocate(ValueLayout.JAVA_INT);

            for (int index = 0; index < sectionMap.length; index++) {
                sectionMapSegment.setAtIndex(ValueLayout.JAVA_LONG, index, sectionMap[index]);
            }

            check(invokeSortSections(sectionMapSegment, sectionMap.length,
                    outputSectionsSegment, outputSections.length,
                    relativeCameraSectionX, relativeCameraSectionY, relativeCameraSectionZ, countSegment),
                    "native section render-list sorting");
            int count = countSegment.get(ValueLayout.JAVA_INT, 0);

            for (int index = 0; index < count; index++) {
                outputSections[index] = outputSectionsSegment.getAtIndex(ValueLayout.JAVA_BYTE, index);
            }

            return count;
        }
    }

    public static void sortRegions(int[] sortScratch, int regionCount, int outputIndexOffset,
            int cameraRegionX, int cameraRegionY, int cameraRegionZ) {
        verifyAvailable();

        if (regionCount < 0 || outputIndexOffset < 0 || sortScratch.length < regionCount * 3
                || sortScratch.length - outputIndexOffset < regionCount) {
            throw new IllegalArgumentException("Invalid native region render-list sort sizes");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment regionCoordinatesSegment = arena.allocate(ValueLayout.JAVA_INT, regionCount * 3);
            MemorySegment outputIndicesSegment = arena.allocate(ValueLayout.JAVA_INT, regionCount);

            for (int index = 0; index < regionCount * 3; index++) {
                regionCoordinatesSegment.setAtIndex(ValueLayout.JAVA_INT, index, sortScratch[index]);
            }

            check(invokeSortRegions(regionCoordinatesSegment, regionCount, outputIndicesSegment, regionCount,
                    cameraRegionX, cameraRegionY, cameraRegionZ), "native region render-list sorting");

            for (int index = 0; index < regionCount; index++) {
                sortScratch[outputIndexOffset + index] = outputIndicesSegment.getAtIndex(ValueLayout.JAVA_INT, index);
            }
        }
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeVerify() {
        try {
            return (int) VERIFY.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust render-list sorter verification downcall failed", throwable);
        }
    }

    private static int invokeSortSections(MemorySegment sectionMap, int sectionMapLength,
            MemorySegment outputSections, int outputSectionsLength, int relativeCameraSectionX,
            int relativeCameraSectionY, int relativeCameraSectionZ, MemorySegment outputCount) {
        try {
            return (int) SORT_SECTIONS.invokeExact(sectionMap, sectionMapLength, outputSections, outputSectionsLength,
                    relativeCameraSectionX, relativeCameraSectionY, relativeCameraSectionZ, outputCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render-list sort downcall failed", throwable);
        }
    }

    private static int invokeSortRegions(MemorySegment regionCoordinates, int regionCount,
            MemorySegment outputIndices, int outputIndicesLength, int cameraRegionX, int cameraRegionY,
            int cameraRegionZ) {
        try {
            return (int) SORT_REGIONS.invokeExact(regionCoordinates, regionCount, outputIndices, outputIndicesLength,
                    cameraRegionX, cameraRegionY, cameraRegionZ);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust region render-list sort downcall failed", throwable);
        }
    }

    private static int invokePrepareFrame(long regionCoordinatesAddress, int regionCount,
            long outputRegionIndicesAddress, int outputRegionIndicesLength, long sectionMapsAddress,
            int sectionBatchCount, long sectionCameraPositionsAddress, long outputSectionCountsAddress,
            long outputSectionsAddress, int outputSectionStride, int cameraRegionX, int cameraRegionY,
            int cameraRegionZ) {
        try {
            return (int) PREPARE_FRAME.invokeExact(regionCoordinatesAddress, regionCount, outputRegionIndicesAddress,
                    outputRegionIndicesLength, sectionMapsAddress, sectionBatchCount, sectionCameraPositionsAddress,
                    outputSectionCountsAddress, outputSectionsAddress, outputSectionStride, cameraRegionX,
                    cameraRegionY, cameraRegionZ);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust batched render-list preparation downcall failed", throwable);
        }
    }

    private static final class Scratch {
        private ByteBuffer regionCoordinates;
        private ByteBuffer regionIndices;
        private ByteBuffer sectionMaps;
        private ByteBuffer sectionCameraPositions;
        private ByteBuffer sectionCounts;
        private ByteBuffer sectionOutputs;
        private ChunkRenderList[] dirtyLists = new ChunkRenderList[0];
        private ChunkRenderList[] sectionBatchLists = new ChunkRenderList[0];
        private int[] dirtyRelativeCameraSectionX = new int[0];
        private int[] dirtyRelativeCameraSectionY = new int[0];
        private int[] dirtyRelativeCameraSectionZ = new int[0];

        private void ensureCapacity(int renderListCount) {
            int safeCount = Math.max(1, renderListCount);
            this.regionCoordinates = ensureBuffer(this.regionCoordinates, safeCount * 3 * Integer.BYTES);
            this.regionIndices = ensureBuffer(this.regionIndices, safeCount * Integer.BYTES);
            this.sectionMaps = ensureBuffer(this.sectionMaps, safeCount * SECTION_MAP_LONGS * Long.BYTES);
            this.sectionCameraPositions = ensureBuffer(this.sectionCameraPositions, safeCount * 3 * Integer.BYTES);
            this.sectionCounts = ensureBuffer(this.sectionCounts, safeCount * Integer.BYTES);
            this.sectionOutputs = ensureBuffer(this.sectionOutputs, safeCount * SECTION_OUTPUT_STRIDE);

            if (this.dirtyLists.length < renderListCount) {
                this.dirtyLists = new ChunkRenderList[renderListCount];
                this.sectionBatchLists = new ChunkRenderList[renderListCount];
                this.dirtyRelativeCameraSectionX = new int[renderListCount];
                this.dirtyRelativeCameraSectionY = new int[renderListCount];
                this.dirtyRelativeCameraSectionZ = new int[renderListCount];
            }
        }

        private void writeSectionMap(int batchIndex, long[] map) {
            int offset = batchIndex * SECTION_MAP_LONGS * Long.BYTES;
            for (int index = 0; index < SECTION_MAP_LONGS; index++) {
                this.sectionMaps.putLong(offset + index * Long.BYTES, map[index]);
            }
        }

        private void writeSectionCamera(int batchIndex, int relativeCameraSectionX, int relativeCameraSectionY,
                int relativeCameraSectionZ) {
            int offset = batchIndex * 3 * Integer.BYTES;
            this.sectionCameraPositions.putInt(offset, relativeCameraSectionX);
            this.sectionCameraPositions.putInt(offset + Integer.BYTES, relativeCameraSectionY);
            this.sectionCameraPositions.putInt(offset + 2 * Integer.BYTES, relativeCameraSectionZ);
        }

        private static ByteBuffer ensureBuffer(ByteBuffer buffer, int requiredCapacity) {
            if (buffer != null && buffer.capacity() >= requiredCapacity) {
                return buffer;
            }

            int capacity = Math.max(1, Integer.highestOneBit(requiredCapacity - 1) << 1);
            return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        }
    }
}
