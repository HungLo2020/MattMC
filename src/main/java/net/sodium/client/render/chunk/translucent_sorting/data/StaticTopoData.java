package net.sodium.client.render.chunk.translucent_sorting.data;

import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentSortData;
import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentGeometryAnalyzer;
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
        int[] quadIndexes = NativeTranslucentGeometryAnalyzer.topoGraphSort(quads, failOnIntersection);

        if (quadIndexes == null) {
            return null;
        }

        var sorter = new StaticSorter(quads.length);
        TranslucentData.writeQuadVertexIndexes(sorter.getIntBuffer(), quadIndexes, quadIndexes.length);

        var staticTopoData = new StaticTopoData(sectionPos, quads.length);
        staticTopoData.sorterOnce = sorter;
        return staticTopoData;
    }

    public static StaticTopoData fromNativeOrder(int quadCount, int[] quadIndexes, SectionPos sectionPos) {
        var sorter = new StaticSorter(quadCount);
        TranslucentData.writeQuadVertexIndexes(sorter.getIntBuffer(), quadIndexes, quadIndexes.length);

        var staticTopoData = new StaticTopoData(sectionPos, quadCount);
        staticTopoData.sorterOnce = sorter;
        return staticTopoData;
    }

    public static StaticTopoData fromNativeSortData(int quadCount, NativeTranslucentSortData sortData,
            SectionPos sectionPos) {
        var staticTopoData = new StaticTopoData(sectionPos, quadCount);
        staticTopoData.sorterOnce = sortData.createStaticSorter();
        return staticTopoData;
    }
}
