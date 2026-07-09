package net.sodium.client.render.chunk.translucent_sorting.data;

import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.minecraft.core.SectionPos;

import java.util.function.IntConsumer;

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

    private static final class QuadIndexCollector implements IntConsumer {
        private final int[] quadIndexes;
        private int count;

        private QuadIndexCollector(int quadCount) {
            this.quadIndexes = new int[quadCount];
        }

        @Override
        public void accept(int value) {
            if (this.count >= this.quadIndexes.length) {
                throw new IllegalStateException("Static topo sort wrote more quad indexes than expected");
            }

            this.quadIndexes[this.count++] = value;
        }
    }

    public static StaticTopoData fromMesh(TQuad[] quads, SectionPos sectionPos, boolean failOnIntersection) {
        var indexWriter = new QuadIndexCollector(quads.length);

        if (!TopoGraphSorting.topoGraphSort(indexWriter, quads, null, null, failOnIntersection)) {
            return null;
        }

        var sorter = new StaticSorter(quads.length);
        TranslucentData.writeQuadVertexIndexes(sorter.getIntBuffer(), indexWriter.quadIndexes, indexWriter.count);

        var staticTopoData = new StaticTopoData(sectionPos, quads.length);
        staticTopoData.sorterOnce = sorter;
        return staticTopoData;
    }
}
