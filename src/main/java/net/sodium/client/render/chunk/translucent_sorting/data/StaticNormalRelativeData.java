package net.sodium.client.render.chunk.translucent_sorting.data;

import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentSortData;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.api.util.MathUtil;
import net.minecraft.core.SectionPos;

/**
 * Static normal relative sorting orders quads by the dot product of their
 * normal and position. (referred to as "distance" throughout the code)
 *
 * Unlike sorting by distance, which is descending for translucent rendering to
 * be correct, sorting by dot product is ascending instead.
 */
public class StaticNormalRelativeData extends PresentTranslucentData {
    private Sorter sorterOnce;

    public StaticNormalRelativeData(SectionPos sectionPos, int inputQuadCount) {
        super(sectionPos, inputQuadCount);
    }

    @Override
    public SortType getSortType() {
        return SortType.STATIC_NORMAL_RELATIVE;
    }

    @Override
    public Sorter getSorter() {
        var sorter = this.sorterOnce;
        if (sorter == null) {
            throw new IllegalStateException("Sorter already used!");
        }
        this.sorterOnce = null;
        return sorter;
    }

    private static StaticNormalRelativeData fromDoubleUnaligned(TQuad[] quads, SectionPos sectionPos) {
        final var keys = new int[quads.length];
        for (int q = 0; q < quads.length; q++) {
            keys[q] = MathUtil.floatToComparableInt(quads[q].getAccurateDotProduct());
        }

        return fromNative(emptyFacingCounts(), keys, sectionPos, quads.length, true);
    }

    /**
     * Important: The vertex indexes must start at zero for each facing.
     */
    private static StaticNormalRelativeData fromMixed(int[] meshFacingCounts,
                                                      TQuad[] quads, SectionPos sectionPos) {
        final var keys = new int[quads.length];
        int quadIndex = 0;
        for (var quadCount : meshFacingCounts) {
            if (quadCount == -1 || quadCount == 0) {
                continue;
            }

            for (int idx = 0; idx < quadCount; idx++) {
                keys[quadIndex] = MathUtil.floatToComparableInt(quads[quadIndex].getAccurateDotProduct());
                quadIndex++;
            }
        }

        return fromNative(meshFacingCounts, keys, sectionPos, quads.length, false);
    }

    public static StaticNormalRelativeData fromMesh(int[] meshFacingCounts,
            TQuad[] quads, SectionPos sectionPos, boolean isDoubleUnaligned) {
        if (isDoubleUnaligned) {
            return fromDoubleUnaligned(quads, sectionPos);
        } else {
            return fromMixed(meshFacingCounts, quads, sectionPos);
        }
    }

    public static StaticNormalRelativeData fromNative(int[] meshFacingCounts, int[] sortKeys, SectionPos sectionPos,
            int quadCount, boolean isDoubleUnaligned) {
        var snrData = new StaticNormalRelativeData(sectionPos, quadCount);
        NativeTranslucentSortData sortData = NativeTranslucentSortData.createStaticNormalRelative(meshFacingCounts,
                sortKeys, quadCount, isDoubleUnaligned);
        snrData.sorterOnce = sortData.createStaticSorter();
        return snrData;
    }

    private static int[] emptyFacingCounts() {
        return new int[] {0, 0, 0, 0, 0, 0, 0};
    }
}
