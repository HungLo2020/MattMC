package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentGeometryAnalyzer;
import net.sodium.client.render.chunk.translucent_sorting.quad.NativeFullTQuad;
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
    final NativeBspBuildResult result = new NativeBspBuildResult();
    private NativeBspTree.Builder nativeTreeBuilder = NativeBspTree.Builder.create();
    private final NativeTranslucentGeometryAnalyzer.TopoQuadStore topoQuadStore;
    private boolean resultReleased;
    private boolean nativeTreeReleased;

    private final SectionPos sectionPos;
    final boolean prepareNodeReuse;
    final boolean quantizeTriggerNormals;

    private int quadCount;
    private final int maxQuadCount;
    private IntArrayList availableQuadIndexes;
    private NativeUpdatedQuads updatedQuads;

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

    NativeBspTree.Builder nativeTreeBuilder() {
        if (this.nativeTreeBuilder == null) {
            throw new IllegalStateException("Native BSP tree builder has been released");
        }
        return this.nativeTreeBuilder;
    }

    int addNativeLeafSingle(int quad) {
        return this.nativeTreeBuilder().addLeafSingle(quad);
    }

    int addNativeLeafDouble(int quadA, int quadB) {
        return this.nativeTreeBuilder().addLeafDouble(quadA, quadB);
    }

    int addNativeLeafMulti(int[] quads) {
        return this.nativeTreeBuilder().addLeafMulti(quads);
    }

    int addNativeFixedDouble(NativeBspTree.Remap remap, BSPNode first, BSPNode second) {
        return this.nativeTreeBuilder().addFixedDouble(remap, first.nativeNodeIndex(), second.nativeNodeIndex());
    }

    int addNativeBinary(NativeBspTree.Remap remap, Vector3fc normal, float distance,
            BSPNode inside, BSPNode outside, int[] onPlane) {
        int insideIndex = inside == null ? NativeBspTree.NULL_NODE : inside.nativeNodeIndex();
        int outsideIndex = outside == null ? NativeBspTree.NULL_NODE : outside.nativeNodeIndex();
        return this.nativeTreeBuilder().addBinary(remap, normal, distance, insideIndex, outsideIndex, onPlane);
    }

    int addNativeMultiPartition(NativeBspTree.Remap remap, Vector3fc normal, float[] distances,
            BSPNode[] partitions, int[][] onPlaneQuads) {
        int[] partitionIndexes = new int[partitions.length];
        for (int index = 0; index < partitions.length; index++) {
            partitionIndexes[index] = partitions[index] == null
                    ? NativeBspTree.NULL_NODE
                    : partitions[index].nativeNodeIndex();
        }

        return this.nativeTreeBuilder().addMultiPartition(remap, normal, distances, partitionIndexes, onPlaneQuads);
    }

    long finishNativeTree(BSPNode rootNode, int indexQuadCount) {
        if (rootNode == null) {
            throw new IllegalArgumentException("BSP root node must not be null");
        }
        if (indexQuadCount < 0) {
            throw new IllegalArgumentException("Invalid BSP index quad count: " + indexQuadCount);
        }

        long treeHandle = this.nativeTreeBuilder().finishHandle(rootNode.nativeNodeIndex(), indexQuadCount);
        this.nativeTreeReleased = true;
        this.nativeTreeBuilder = null;
        return treeHandle;
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

    private void registerQuadUpdate(NativeFullTQuad quad) {
        if (quad.triggerAndSetUpdatedVertices()) {
            if (this.updatedQuads == null) {
                this.updatedQuads = new NativeUpdatedQuads();
            }
            this.updatedQuads.add(quad);
        }
    }

    public NativeUpdatedQuads getFinalizedUpdatedQuads() {
        if (this.updatedQuads != null) {
            this.updatedQuads.setQuadCounts(this.size(), this.quadCount);
        }
        return this.updatedQuads;
    }

    NativeBspBuildResult releaseResult() {
        this.resultReleased = true;
        return this.result;
    }

    int pushQuad(NativeFullTQuad quad) {
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

    int updateQuad(NativeFullTQuad quad, int quadIndex) {
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
        if (!this.nativeTreeReleased && this.nativeTreeBuilder != null) {
            this.nativeTreeBuilder.close();
            this.nativeTreeBuilder = null;
        }
        if (!this.resultReleased) {
            this.result.close();
        }
    }
}
