package net.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.util.iterator.ByteIterator;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeRenderListSorterTest {
    @Test
    void sortsSectionBitmapUsingNativeHistogramOrder() {
        long[] map = new long[RenderRegion.REGION_SIZE / Long.SIZE];
        byte[] output = new byte[RenderRegion.REGION_SIZE];

        mark(map, pack(0, 0, 0));
        mark(map, pack(1, 0, 0));
        mark(map, pack(0, 1, 0));
        mark(map, pack(2, 0, 0));

        int count = NativeRenderListSorter.sortSections(map, output, 0, 0, 0);

        assertEquals(4, count);
        assertArrayEquals(new byte[] {
                (byte) pack(0, 0, 0),
                (byte) pack(1, 0, 0),
                (byte) pack(0, 1, 0),
                (byte) pack(2, 0, 0),
        }, java.util.Arrays.copyOf(output, count));
    }

    @Test
    void sortsRegionsByDistanceThenOriginalIndex() {
        int regionCount = 4;
        int outputOffset = regionCount * 3;
        int[] scratch = new int[regionCount * 4];

        writeRegion(scratch, 0, 4, 0, 0);
        writeRegion(scratch, 1, 1, 0, 0);
        writeRegion(scratch, 2, 0, 0, 1);
        writeRegion(scratch, 3, 2, 2, 2);

        NativeRenderListSorter.sortRegions(scratch, regionCount, outputOffset, 0, 0, 0);

        assertArrayEquals(new int[] {1, 2, 0, 3},
                java.util.Arrays.copyOfRange(scratch, outputOffset, outputOffset + regionCount));
    }

    @Test
    void preparesRenderListsWithSingleNativeFrameBoundary() {
        RenderRegion farX = new RenderRegion(4, 0, 0, null);
        RenderRegion nearX = new RenderRegion(1, 0, 0, null);
        RenderRegion nearZ = new RenderRegion(0, 0, 1, null);
        RenderRegion farDiagonal = new RenderRegion(2, 2, 2, null);

        ChunkRenderList farXList = farX.getRenderList();
        ChunkRenderList nearXList = nearX.getRenderList();
        ChunkRenderList nearZList = nearZ.getRenderList();
        ChunkRenderList farDiagonalList = farDiagonal.getRenderList();

        addGeometrySections(nearXList, nearX, 0, 0, 0, 1, 0, 0, 0, 1, 0, 2, 0, 0);

        SortedRenderLists sorted = NativeRenderListSorter.prepareRenderLists(ObjectArrayList.of(
                farXList,
                nearXList,
                nearZList,
                farDiagonalList
        ), SectionPos.of(0, 0, 0));

        var iterator = sorted.iterator(false);
        assertSame(nearXList, iterator.next());
        assertSame(nearZList, iterator.next());
        assertSame(farXList, iterator.next());
        assertSame(farDiagonalList, iterator.next());
        assertFalse(iterator.hasNext());

        assertArrayEquals(new int[] {
                pack(0, 0, 0),
                pack(1, 0, 0),
                pack(0, 1, 0),
                pack(2, 0, 0),
        }, readGeometrySections(nearXList));
    }

    @Test
    void renderListHotPathsRequireNativeSorter() throws Exception {
        String renderListProvider = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/lists/RenderListProvider.java"));
        String chunkRenderList = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/net/sodium/client/render/chunk/lists/ChunkRenderList.java"));

        assertTrue(renderListProvider.contains("NativeRenderListSorter.prepareRenderLists("));
        assertTrue(chunkRenderList.contains("NativeRenderListSorter.sortSections("));
        assertFalse(renderListProvider.contains("NativeRenderListSorter.sortRegions("));
        assertFalse(renderListProvider.contains("prepareForRender(sectionPos)"));
        assertFalse(renderListProvider.contains("SortItemsProvider"));
        assertFalse(renderListProvider.contains("IntArrays.unstableSort"));
        assertFalse(chunkRenderList.contains("LocalSectionIndex.unpack"));
    }

    private static void addGeometrySections(ChunkRenderList list, RenderRegion region, int... localCoordinates) {
        list.reset(1, false);

        for (int index = 0; index < localCoordinates.length; index += 3) {
            RenderSection section = new RenderSection(region,
                    region.getChunkX() + localCoordinates[index],
                    region.getChunkY() + localCoordinates[index + 1],
                    region.getChunkZ() + localCoordinates[index + 2]);
            section.setInfo(geometryInfo());
            list.add(section);
        }
    }

    private static BuiltSectionInfo geometryInfo() {
        VisibilitySet visibility = new VisibilitySet();
        visibility.add(EnumSet.allOf(Direction.class));

        BuiltSectionInfo.Builder builder = new BuiltSectionInfo.Builder();
        builder.addRenderPass(DefaultTerrainRenderPasses.SOLID);
        builder.setOcclusionData(visibility);
        return builder.build();
    }

    private static int[] readGeometrySections(ChunkRenderList list) {
        ByteIterator iterator = list.sectionsWithGeometryIterator(false);
        int[] sections = new int[list.getSectionsWithGeometryCount()];

        for (int index = 0; index < sections.length; index++) {
            assertTrue(iterator.hasNext());
            sections[index] = iterator.nextByteAsInt();
        }

        assertFalse(iterator.hasNext());
        return sections;
    }

    private static void writeRegion(int[] scratch, int index, int x, int y, int z) {
        int offset = index * 3;
        scratch[offset] = x;
        scratch[offset + 1] = y;
        scratch[offset + 2] = z;
    }

    private static void mark(long[] map, int index) {
        map[index >> 6] |= 1L << (index & 0b111111);
    }

    private static int pack(int x, int y, int z) {
        return ((x & 0b111) << 5) | (y & 0b11) | ((z & 0b111) << 2);
    }
}
