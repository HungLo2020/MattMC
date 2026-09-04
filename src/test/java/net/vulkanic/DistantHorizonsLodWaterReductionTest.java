package net.vulkanic;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointReducingList;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DistantHorizonsLodWaterReductionTest {

    @Test
    public void reduceToOneKeepsWaterBelowTransparentOceanPlants() {
        ColumnArrayView source = column(
            dataPoint(65, 64, ColorUtil.argbToInt(128, 30, 110, 45), EDhApiBlockMaterial.UNKNOWN),
            dataPoint(64, 60, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER),
            dataPoint(60, 0, ColorUtil.argbToInt(255, 190, 170, 120), EDhApiBlockMaterial.SAND)
        );

        long reduced = RenderDataPointReducingList.reduceToOne(source);

        assertEquals(EDhApiBlockMaterial.WATER.index, RenderDataPointUtil.getBlockMaterialId(reduced));
        assertEquals(64, RenderDataPointUtil.getYMax(reduced));
        assertEquals(0, RenderDataPointUtil.getYMin(reduced));
    }

    @Test
    public void reduceToOneKeepsWaterBelowLeafLikeOceanPlants() {
        ColumnArrayView source = column(
            dataPoint(65, 64, ColorUtil.argbToInt(255, 30, 110, 45), EDhApiBlockMaterial.LEAVES),
            dataPoint(64, 60, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER),
            dataPoint(60, 0, ColorUtil.argbToInt(255, 190, 170, 120), EDhApiBlockMaterial.SAND)
        );

        long reduced = RenderDataPointReducingList.reduceToOne(source);

        assertEquals(EDhApiBlockMaterial.WATER.index, RenderDataPointUtil.getBlockMaterialId(reduced));
        assertEquals(64, RenderDataPointUtil.getYMax(reduced));
        assertEquals(0, RenderDataPointUtil.getYMin(reduced));
    }

    @Test
    public void reduceToOneDoesNotPullWaterThroughOpaqueTerrain() {
        ColumnArrayView source = column(
            dataPoint(70, 65, ColorUtil.argbToInt(255, 80, 90, 70), EDhApiBlockMaterial.STONE),
            dataPoint(64, 60, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER),
            dataPoint(60, 0, ColorUtil.argbToInt(255, 190, 170, 120), EDhApiBlockMaterial.SAND)
        );

        long reduced = RenderDataPointReducingList.reduceToOne(source);

        assertEquals(EDhApiBlockMaterial.STONE.index, RenderDataPointUtil.getBlockMaterialId(reduced));
        assertEquals(70, RenderDataPointUtil.getYMax(reduced));
        assertEquals(0, RenderDataPointUtil.getYMin(reduced));
    }

    @Test
    public void reduceToTwoKeepsWaterSurfaceAndTransparentOceanDetails() {
        ColumnArrayView source = column(
            dataPoint(65, 64, ColorUtil.argbToInt(128, 30, 110, 45), EDhApiBlockMaterial.UNKNOWN),
            dataPoint(64, 60, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER),
            dataPoint(60, 0, ColorUtil.argbToInt(255, 190, 170, 120), EDhApiBlockMaterial.SAND)
        );
        ColumnArrayView output = emptyColumn(2);

        RenderDataPointUtil.mergeMultiData(source, output);

        assertContainsMaterial(output, EDhApiBlockMaterial.UNKNOWN);
        assertContainsMaterial(output, EDhApiBlockMaterial.WATER);
    }

    @Test
    public void reduceKeepsHighestWaterSurfaceWhenLowerWaterSurvives() {
        ColumnArrayView source = column(
            dataPoint(65, 64, ColorUtil.argbToInt(128, 30, 110, 45), EDhApiBlockMaterial.UNKNOWN),
            dataPoint(64, 63, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER),
            dataPoint(63, 20, ColorUtil.argbToInt(255, 190, 170, 120), EDhApiBlockMaterial.SAND),
            dataPoint(20, 10, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER)
        );
        ColumnArrayView output = emptyColumn(2);

        RenderDataPointUtil.mergeMultiData(source, output);

        assertContainsWaterSurfaceAtOrAbove(output, 64);
    }

    @Test
    public void reduceToMultipleSlotsKeepsWaterEvenWhenTerrainExistsAbove() {
        ColumnArrayView source = column(
            dataPoint(72, 68, ColorUtil.argbToInt(255, 90, 80, 65), EDhApiBlockMaterial.STONE),
            dataPoint(64, 60, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER),
            dataPoint(60, 0, ColorUtil.argbToInt(255, 190, 170, 120), EDhApiBlockMaterial.SAND)
        );
        ColumnArrayView output = emptyColumn(2);

        RenderDataPointUtil.mergeMultiData(source, output);

        assertContainsMaterial(output, EDhApiBlockMaterial.STONE);
        assertContainsWaterSurfaceAtOrAbove(output, 64);
    }

    @Test
    public void reduceToMultipleSlotsPreservesOriginalWaterSurfaceHeight() {
        ColumnArrayView source = column(
            dataPoint(72, 68, ColorUtil.argbToInt(255, 90, 80, 65), EDhApiBlockMaterial.STONE),
            dataPoint(65, 64, ColorUtil.argbToInt(128, 30, 110, 45), EDhApiBlockMaterial.UNKNOWN),
            dataPoint(64, 63, ColorUtil.argbToInt(128, 40, 100, 180), EDhApiBlockMaterial.WATER),
            dataPoint(63, 0, ColorUtil.argbToInt(255, 190, 170, 120), EDhApiBlockMaterial.SAND)
        );
        ColumnArrayView output = emptyColumn(2);

        RenderDataPointUtil.mergeMultiData(source, output);

        assertContainsWaterSurface(output, 64, 63);
    }

    @Test
    public void parentSliceSelectionKeepsWaterSurfaceOverKelpLikeDetails() throws Exception {
        FullDataPointIdMap mapping = testMapping();
        int water = mapping.addIfNotPresentAndGetId(TestBiome.INSTANCE, new TestBlock("water", EDhApiBlockMaterial.WATER, false, true, false));
        int kelp = mapping.addIfNotPresentAndGetId(TestBiome.INSTANCE, new TestBlock("kelp", EDhApiBlockMaterial.UNKNOWN, false, false, false));

        int selected = selectParentSliceId(new int[] { kelp, water, kelp, water }, mapping);

        assertEquals(water, selected);
    }

    @Test
    public void parentSliceSelectionKeepsWaterSurfaceWhenTerrainWouldOtherwiseWin() throws Exception {
        FullDataPointIdMap mapping = testMapping();
        int water = mapping.addIfNotPresentAndGetId(TestBiome.INSTANCE, new TestBlock("water", EDhApiBlockMaterial.WATER, false, true, false));
        int sand = mapping.addIfNotPresentAndGetId(TestBiome.INSTANCE, new TestBlock("sand", EDhApiBlockMaterial.SAND, true, false, true));

        int selected = selectParentSliceId(new int[] { water, sand, sand, sand }, mapping);

        assertEquals(water, selected);
    }

    @Test
    public void parentTwoByTwoMergeKeepsOceanWaterSurface() throws Exception {
        FullDataPointIdMap mapping = testMapping();
        int water = mapping.addIfNotPresentAndGetId(TestBiome.INSTANCE, new TestBlock("water", EDhApiBlockMaterial.WATER, false, true, false));
        int sand = mapping.addIfNotPresentAndGetId(TestBiome.INSTANCE, new TestBlock("sand", EDhApiBlockMaterial.SAND, true, false, true));
        LongArrayList[] columns = emptyFullDataColumns();
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                columns[FullDataSourceV2.relativePosToIndex(x, z)] = new LongArrayList(new long[] {
                    FullDataPointUtil.encode(water, 4, 60, (byte) 0, (byte) 15),
                    FullDataPointUtil.encode(sand, 60, 0, (byte) 0, (byte) 15)
                });
            }
        }
        FullDataSourceV2 source = FullDataSourceV2.createWithData(
            0,
            mapping,
            columns,
            fullDataGenerationSteps(),
            fullDataCompressionModes());

        LongArrayList merged = mergeParentColumn(source, 0, 0);

        assertContainsFullDataId(merged, water);
    }

    @Test
    public void downsampleCopiesCompressionModeFromMappedInputColumn() throws Exception {
        byte detail = (byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1);
        long inputPos = DhSectionPos.encode(detail, 0, 0);
        long outputPos = DhSectionPos.encode((byte) (detail - 1), 0, 0);
        LongArrayList[] columns = emptyFullDataColumns();
        byte[] steps = fullDataGenerationSteps();
        byte[] modes = fullDataCompressionModes();

        // Output (2,0) maps to input (1,0). Make those indices deliberately
        // different so reading recipientIndex would select the wrong policy.
        int mappedInputIndex = FullDataSourceV2.relativePosToIndex(1, 0);
        int unrelatedInputIndex = FullDataSourceV2.relativePosToIndex(2, 0);
        modes[mappedInputIndex] = EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value;
        modes[unrelatedInputIndex] = EDhApiWorldCompressionMode.VISUALLY_EQUAL.value;

        FullDataPointIdMap mapping = testMapping();
        FullDataSourceV2 input = FullDataSourceV2.createWithData(
            inputPos, mapping, columns, steps, modes);
        FullDataSourceV2 output = FullDataSourceV2.createEmpty(outputPos);
        try {
            java.lang.reflect.Field uniformity = FullDataSourceV2.class.getDeclaredField("semanticHorizontalUniform");
            uniformity.setAccessible(true);
            ((boolean[]) uniformity.get(input))[mappedInputIndex] = true;
            Method downsample = FullDataSourceV2.class.getDeclaredMethod(
                "downsampleFromOneAboveDetailLevel", FullDataSourceV2.class, int[].class);
            downsample.setAccessible(true);
            int[] identityRemap = new int[16];
            for (int i = 0; i < identityRemap.length; i++) {
                identityRemap[i] = i;
            }
            downsample.invoke(output, input, identityRemap);
            assertEquals(
                EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value,
                output.columnWorldCompressionMode.getByte(FullDataSourceV2.relativePosToIndex(2, 0)));
            org.junit.jupiter.api.Assertions.assertTrue(
                output.hasSemanticHorizontalUniformity(2, 0));
        } finally {
            output.close();
            input.close();
        }
    }

    @Test
    public void downsamplePropagatesMappedContributorFootprintWithIdRemap() throws Exception {
        byte detail = (byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1);
        FullDataPointIdMap inputMapping = testMapping();
        FullDataPointIdMap outputMapping = testMapping();
        LongArrayList[] columns = emptyFullDataColumns();
        int mappedInputIndex = FullDataSourceV2.relativePosToIndex(1, 0);
        columns[mappedInputIndex].add(FullDataPointUtil.encode(1, 4, 60, (byte) 0, (byte) 15));
        FullDataSourceV2 input = FullDataSourceV2.createWithData(
            DhSectionPos.encode(detail, 0, 0), inputMapping, columns,
            fullDataGenerationSteps(), fullDataCompressionModes());
        input.setSemanticHorizontalUniformity(1, 0, false);
        LongArrayList[] footprint = new LongArrayList[] {
            new LongArrayList(new long[] { FullDataPointUtil.encode(1, 4, 60, (byte) 0, (byte) 15) }),
            new LongArrayList(), new LongArrayList(), new LongArrayList()
        };
        input.setSemanticHorizontalContributors(1, 0, footprint);
        FullDataSourceV2 output = FullDataSourceV2.createEmpty(
            DhSectionPos.encode((byte) (detail - 1), 0, 0));
        try {
            Method downsample = FullDataSourceV2.class.getDeclaredMethod(
                "downsampleFromOneAboveDetailLevel", FullDataSourceV2.class, int[].class);
            downsample.setAccessible(true);
            int[] identityRemap = new int[16];
            for (int i = 0; i < identityRemap.length; i++) identityRemap[i] = i;
            downsample.invoke(output, input, identityRemap);
            LongArrayList[] copied = output.getSemanticHorizontalContributors(2, 0);
            assertEquals(4, copied.length);
            assertEquals(1, copied[0].size());
            assertTrue(!output.hasSemanticHorizontalUniformity(2, 0));
        } finally {
            output.close();
            input.close();
        }
    }

    @Test
    public void reducedColumnsRetainBoundedHorizontalSemanticContributors() throws Exception {
        byte inputDetail = (byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1);
        FullDataSourceV2 input = FullDataSourceV2.createWithData(
            DhSectionPos.encode(inputDetail, 0, 0), testMapping(),
            emptyFullDataColumns(), fullDataGenerationSteps(), fullDataCompressionModes());
        FullDataSourceV2 output = FullDataSourceV2.createEmpty(
            DhSectionPos.encode((byte) (inputDetail + 1), 0, 0));
        try {
            Method update = FullDataSourceV2.class.getDeclaredMethod(
                "updateFromOneBelowDetailLevel", FullDataSourceV2.class, int[].class);
            update.setAccessible(true);
            int[] identityRemap = new int[16];
            for (int i = 0; i < identityRemap.length; i++) identityRemap[i] = i;
            update.invoke(output, input, identityRemap);
            LongArrayList[] contributors = output.getSemanticHorizontalContributors(0, 0);
            assertEquals(4, contributors.length);
        } finally {
            output.close();
            input.close();
        }
    }

    private static ColumnArrayView column(long... dataPoints) {
        LongArrayList data = new LongArrayList(dataPoints);
        return new ColumnArrayView(data, dataPoints.length, 0, dataPoints.length);
    }

    private static ColumnArrayView emptyColumn(int verticalSize) {
        LongArrayList data = new LongArrayList(verticalSize);
        for (int i = 0; i < verticalSize; i++) {
            data.add(RenderDataPointUtil.EMPTY_DATA);
        }
        return new ColumnArrayView(data, verticalSize, 0, verticalSize);
    }

    private static LongArrayList[] emptyFullDataColumns() {
        LongArrayList[] columns = new LongArrayList[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = new LongArrayList();
        }
        return columns;
    }

    private static byte[] fullDataGenerationSteps() {
        byte[] steps = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
        java.util.Arrays.fill(steps, EDhApiWorldGenerationStep.LIGHT.value);
        return steps;
    }

    private static byte[] fullDataCompressionModes() {
        byte[] modes = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
        java.util.Arrays.fill(modes, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value);
        return modes;
    }

    private static void assertContainsMaterial(ColumnArrayView column, EDhApiBlockMaterial material) {
        for (int i = 0; i < column.size(); i++) {
            long dataPoint = column.get(i);
            if (RenderDataPointUtil.doesDataPointExist(dataPoint)
                && RenderDataPointUtil.getBlockMaterialId(dataPoint) == material.index) {
                return;
            }
        }

        assertTrue(false, "Expected material " + material + " in " + column);
    }

    private static void assertContainsWaterSurfaceAtOrAbove(ColumnArrayView column, int y) {
        for (int i = 0; i < column.size(); i++) {
            long dataPoint = column.get(i);
            if (RenderDataPointUtil.doesDataPointExist(dataPoint)
                && RenderDataPointUtil.getBlockMaterialId(dataPoint) == EDhApiBlockMaterial.WATER.index
                && RenderDataPointUtil.getYMax(dataPoint) >= y) {
                return;
            }
        }

        assertTrue(false, "Expected water surface at or above " + y + " in " + column);
    }

    private static void assertContainsWaterSurface(ColumnArrayView column, int yMax, int yMin) {
        for (int i = 0; i < column.size(); i++) {
            long dataPoint = column.get(i);
            if (RenderDataPointUtil.doesDataPointExist(dataPoint)
                && RenderDataPointUtil.getBlockMaterialId(dataPoint) == EDhApiBlockMaterial.WATER.index
                && RenderDataPointUtil.getYMax(dataPoint) == yMax
                && RenderDataPointUtil.getYMin(dataPoint) == yMin) {
                return;
            }
        }

        assertTrue(false, "Expected water surface " + yMax + ".." + yMin + " in " + column);
    }

    private static void assertContainsFullDataId(LongArrayList column, int id) {
        for (long dataPoint : column) {
            if (FullDataPointUtil.getId(dataPoint) == id) {
                return;
            }
        }

        assertTrue(false, "Expected full-data id " + id + " in " + column);
    }

    private static long dataPoint(int yMax, int yMin, int color, EDhApiBlockMaterial material) {
        return RenderDataPointUtil.createDataPoint(yMax, yMin, color, 15, 0, material.index);
    }

    private static FullDataPointIdMap testMapping() {
        FullDataPointIdMap mapping = new FullDataPointIdMap(0);
        mapping.addIfNotPresentAndGetId(TestBiome.INSTANCE, new TestBlock("air", EDhApiBlockMaterial.AIR, false, false, false));
        return mapping;
    }

    private static int selectParentSliceId(int[] ids, FullDataPointIdMap mapping) throws Exception {
        Method method = FullDataSourceV2.class.getDeclaredMethod(
            "determineMostCommonValueInColumnSlice",
            int[].class,
            FullDataPointIdMap.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, ids, mapping);
    }

    private static LongArrayList mergeParentColumn(FullDataSourceV2 source, int x, int z) throws Exception {
        Method method = FullDataSourceV2.class.getDeclaredMethod(
            "mergeInputTwoByTwoDataColumn",
            FullDataSourceV2.class,
            int.class,
            int.class);
        method.setAccessible(true);
        return (LongArrayList) method.invoke(null, source, x, z);
    }

    private enum TestBiome implements IBiomeWrapper {
        INSTANCE;

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public String getSerialString() {
            return "test:biome";
        }

        @Override
        public Object getWrappedMcObject() {
            return this;
        }
    }

    private static final class TestBlock implements IBlockStateWrapper {
        private final String name;
        private final EDhApiBlockMaterial material;
        private final boolean solid;
        private final boolean liquid;
        private final boolean opaque;

        private TestBlock(String name, EDhApiBlockMaterial material, boolean solid, boolean liquid, boolean opaque) {
            this.name = name;
            this.material = material;
            this.solid = solid;
            this.liquid = liquid;
            this.opaque = opaque;
        }

        @Override
        public boolean isAir() {
            return this.material == EDhApiBlockMaterial.AIR;
        }

        @Override
        public boolean isSolid() {
            return this.solid;
        }

        @Override
        public boolean isLiquid() {
            return this.liquid;
        }

        @Override
        public String getSerialString() {
            return "test:" + this.name;
        }

        @Override
        public int getOpacity() {
            return this.opaque ? 15 : 0;
        }

        @Override
        public int getLightEmission() {
            return 0;
        }

        @Override
        public byte getMaterialId() {
            return this.material.index;
        }

        @Override
        public boolean isBeaconBlock() {
            return false;
        }

        @Override
        public boolean isBeaconTintBlock() {
            return false;
        }

        @Override
        public boolean allowsBeaconBeamPassage() {
            return true;
        }

        @Override
        public boolean isBeaconBaseBlock() {
            return false;
        }

        @Override
        public Color getMapColor() {
            return Color.BLACK;
        }

        @Override
        public Color getBeaconTintColor() {
            return Color.WHITE;
        }

        @Override
        public Object getWrappedMcObject() {
            return this;
        }
    }
}
