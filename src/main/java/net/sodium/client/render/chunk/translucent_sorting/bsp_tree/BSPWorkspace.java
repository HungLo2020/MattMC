package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentGeometryAnalyzer;
import net.sodium.client.render.chunk.translucent_sorting.quad.FullTQuad;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.minecraft.core.SectionPos;
import org.joml.Vector3fc;

/**
 * The BSP workspace holds the state during the BSP building process. It brings
 * a number of fixed parameters and receives partition
 * planes to return as part of the final result.
 * 
 * Implementation note: Storing the multi partition node's interval points in a
 * global array instead of making a new one at each tree level doesn't appear to
 * have any performance benefit.
 */
class BSPWorkspace extends ObjectArrayList<TQuad> implements AutoCloseable {
    final BSPResult result = new BSPResult();
    private final NativeTranslucentGeometryAnalyzer.TopoQuadStore topoQuadStore;
    private boolean resultReleased;

    private final SectionPos sectionPos;
    final boolean prepareNodeReuse;
    final boolean quantizeTriggerNormals;

    private int quadCount;
    private final int maxQuadCount;
    private IntArrayList availableQuadIndexes;
    private UpdatedQuadsList updatedQuads;

    BSPWorkspace(TQuad[] quads, SectionPos sectionPos, boolean prepareNodeReuse, QuadSplittingMode quadSplittingMode) {
        super(quads);
        this.topoQuadStore = NativeTranslucentGeometryAnalyzer.createTopoQuadStore(quads);
        this.sectionPos = sectionPos;
        this.prepareNodeReuse = prepareNodeReuse;
        this.quantizeTriggerNormals = quadSplittingMode.quantizeTriggerNormals();

        this.quadCount = quads.length;
        if (quadSplittingMode.allowsSplitting()) {
            this.maxQuadCount = quadSplittingMode.getMaxTotalQuads(this.quadCount);
        } else {
            this.maxQuadCount = this.quadCount;
        }
    }

    boolean canSplitQuads() {
        return this.quadCount < this.maxQuadCount;
    }

    boolean doubleLeafPossible(int quadIndexA, int quadIndexB, boolean failOnIntersection) {
        return this.topoQuadStore.bspDoubleLeafPossible(quadIndexA, quadIndexB, failOnIntersection);
    }

    // TODO: better bidirectional triggering: integrate bidirectionality in GFNI if
    // top-level topo sorting isn't used anymore (and only use half as much memory
    // by not storing trigger planes twice)
    void addAlignedPartitionPlane(int axis, float distance) {
        this.result.addDoubleSidedAlignedPlane(axis, distance);
    }

    void addUnalignedPartitionPlane(Vector3fc planeNormal, float distance) {
        this.result.addDoubleSidedUnalignedPlane(planeNormal, distance);
    }

    private void registerQuadUpdate(FullTQuad quad) {
        if (quad.triggerAndSetUpdatedVertices()) {
            if (this.updatedQuads == null) {
                this.updatedQuads = new UpdatedQuadsList();
            }
            this.updatedQuads.add(quad);
        }
    }

    public UpdatedQuadsList getFinalizedUpdatedQuads() {
        if (this.updatedQuads != null) {
            this.updatedQuads.setQuadCounts(this.size(), this.quadCount);
        }
        return this.updatedQuads;
    }

    BSPResult releaseResult() {
        this.resultReleased = true;
        return this.result;
    }

    int pushQuad(FullTQuad quad) {
        // null or invalid quads simply don't get added
        if (quad == null || quad.isInvalid()) {
            return -1;
        }

        // take an index from the list of holes if there are any
        int index;
        if (this.availableQuadIndexes == null || this.availableQuadIndexes.isEmpty()) {
            index = this.size();
            this.add(quad);
        } else {
            index = this.availableQuadIndexes.removeInt(this.availableQuadIndexes.size() - 1);
            this.set(index, quad);
        }

        quad.setWriteToIndex(index);
        this.topoQuadStore.set(index, quad);
        this.quadCount++;

        this.registerQuadUpdate(quad);

        return index;
    }

    int updateQuad(FullTQuad quad, int quadIndex) {
        if (quad == null) {
            return -1;
        }

        // invalid quads that have already been added to this list have to be removed
        if (quad.isInvalid()) {
            var lastIndex = this.size() - 1;
            if (quadIndex == lastIndex) {
                this.remove(lastIndex);
            } else {
                this.set(quadIndex, null);
                if (this.availableQuadIndexes == null) {
                    this.availableQuadIndexes = new IntArrayList();
                }
                this.availableQuadIndexes.add(quadIndex);
            }

            quad.setNoWrite();
            this.topoQuadStore.remove(quadIndex);
            this.registerQuadUpdate(quad);

            this.quadCount--;

            return -1;
        }

        quad.setWriteToIndex(quadIndex);
        this.topoQuadStore.set(quadIndex, quad);
        this.registerQuadUpdate(quad);

        return quadIndex;
    }

    @Override
    public void close() {
        this.topoQuadStore.close();
        if (!this.resultReleased) {
            this.result.close();
        }
    }
}
