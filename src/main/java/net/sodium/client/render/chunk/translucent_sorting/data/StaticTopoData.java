package net.sodium.client.render.chunk.translucent_sorting.data;

import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentSortData;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.minecraft.core.SectionPos;

/**
 * Static topo acyclic sorting uses the topo sorting algorithm but only if it's
 * possible to sort without dynamic triggering, meaning the sort order never
 * needs to change.
 */
public class StaticTopoData extends PresentTranslucentData {
    private Sorter sorterOnce;

    StaticTopoData(SectionPos sectionPos, int inputQuadCount) {
        super(sectionPos, inputQuadCount);
    }

    @Override
    public SortType getSortType() {
        return SortType.STATIC_TOPO;
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

    public static StaticTopoData fromMesh(TQuad[] quads, SectionPos sectionPos, boolean failOnIntersection) {
        NativeTranslucentSortData sortData = NativeTranslucentSortData.createStaticTopo(quads, failOnIntersection);
        if (sortData == null) {
            return null;
        }

        return fromNativeSortData(quads.length, sortData, sectionPos);
    }

    public static StaticTopoData fromNativeOrder(int quadCount, int[] quadIndexes, SectionPos sectionPos) {
        return fromNativeSortData(quadCount, NativeTranslucentSortData.createStaticOrder(quadCount, quadIndexes),
                sectionPos);
    }

    public static StaticTopoData fromNativeSortData(int quadCount, NativeTranslucentSortData sortData,
            SectionPos sectionPos) {
        var staticTopoData = new StaticTopoData(sectionPos, quadCount);
        staticTopoData.sorterOnce = sortData.createStaticSorter();
        return staticTopoData;
    }
}
