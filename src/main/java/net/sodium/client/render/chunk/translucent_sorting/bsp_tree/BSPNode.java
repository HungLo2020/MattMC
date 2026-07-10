package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.minecraft.core.SectionPos;

import java.util.Arrays;

/**
 * A node in the BSP tree. The BSP tree is made up of nodes that split quads
 * into groups on either side of a plane and those that lie on the plane.
 * There's also leaf nodes that contain one or more quads.
 * 
 * Implementation note:
 * - Doing a convex box test doesn't seem to bring a performance boost, even if
 * it does trigger sometimes with man-made structures. The multi partition node
 * probably does most of the work already.
 * - Checking if the given quads are all coplanar doesn't recoup the cost of
 * iterating through all the quads. It also doesn't significantly reduce the
 * number of triggering planes (which would have a performance and memory usage
 * benefit).
 */
public class BSPNode {
    private static final int NO_QUAD = -1;

    private enum NativeNodeKind {
        NONE,
        LEAF_SINGLE,
        LEAF_DOUBLE,
        LEAF_MULTI,
        FIXED_DOUBLE,
        BINARY,
        MULTI_PARTITION
    }

    private final NativeNodeKind nativeNodeKind;
    private final int quadA;
    private final int quadB;
    private final int[] quads;
    private final BSPNode fixedFirst;
    private final BSPNode fixedSecond;
    private final Vector3fc planeNormal;
    private final int axis;
    private final float planeDistance;
    private final BSPNode inside;
    private final BSPNode outside;
    private final int[] onPlaneQuads;
    private final float[] planeDistances;
    private final BSPNode[] partitions;
    private final int[][] multiOnPlaneQuads;
    private final InnerPartitionBSPNode.NodeReuseData reuseData;
    private int[] indexMap;
    private int fixedIndexOffset = InnerPartitionBSPNode.NO_FIXED_OFFSET;
    private int nativeNodeIndex;

    BSPNode() {
        this(NativeBspTree.NULL_NODE);
    }

    BSPNode(int nativeNodeIndex) {
        this.nativeNodeKind = NativeNodeKind.NONE;
        this.quadA = NO_QUAD;
        this.quadB = NO_QUAD;
        this.quads = null;
        this.fixedFirst = null;
        this.fixedSecond = null;
        this.planeNormal = null;
        this.axis = InnerPartitionBSPNode.UNALIGNED_AXIS;
        this.planeDistance = Float.NaN;
        this.inside = null;
        this.outside = null;
        this.onPlaneQuads = null;
        this.planeDistances = null;
        this.partitions = null;
        this.multiOnPlaneQuads = null;
        this.reuseData = null;
        this.nativeNodeIndex = nativeNodeIndex;
    }

    private BSPNode(int nativeNodeIndex, NativeNodeKind nativeNodeKind, int quadA, int quadB, int[] quads) {
        this(nativeNodeIndex, nativeNodeKind, quadA, quadB, quads, null, null, null,
                InnerPartitionBSPNode.UNALIGNED_AXIS, Float.NaN, null, null, null,
                null, null, null, null);
    }

    private BSPNode(int nativeNodeIndex, NativeNodeKind nativeNodeKind, int quadA, int quadB, int[] quads,
            BSPNode fixedFirst, BSPNode fixedSecond, Vector3fc planeNormal, int axis, float planeDistance,
            BSPNode inside, BSPNode outside, int[] onPlaneQuads, float[] planeDistances, BSPNode[] partitions,
            int[][] multiOnPlaneQuads, InnerPartitionBSPNode.NodeReuseData reuseData) {
        this.nativeNodeKind = nativeNodeKind;
        this.quadA = quadA;
        this.quadB = quadB;
        this.quads = quads;
        this.fixedFirst = fixedFirst;
        this.fixedSecond = fixedSecond;
        this.planeNormal = planeNormal;
        this.axis = axis;
        this.planeDistance = planeDistance;
        this.inside = inside;
        this.outside = outside;
        this.onPlaneQuads = onPlaneQuads;
        this.planeDistances = planeDistances;
        this.partitions = partitions;
        this.multiOnPlaneQuads = multiOnPlaneQuads;
        this.reuseData = reuseData;
        this.nativeNodeIndex = nativeNodeIndex;
    }

    static BSPNode nativeLeafSingle(BSPWorkspace workspace, int quad) {
        return new BSPNode(workspace.addNativeLeafSingle(quad), NativeNodeKind.LEAF_SINGLE, quad, NO_QUAD, null);
    }

    static BSPNode nativeLeafDouble(BSPWorkspace workspace, int quadA, int quadB) {
        return new BSPNode(workspace.addNativeLeafDouble(quadA, quadB), NativeNodeKind.LEAF_DOUBLE, quadA, quadB, null);
    }

    static BSPNode nativeLeafMulti(BSPWorkspace workspace, int[] quads) {
        return new BSPNode(workspace.addNativeLeafMulti(quads), NativeNodeKind.LEAF_MULTI, NO_QUAD, NO_QUAD, quads);
    }

    static BSPNode nativeFixedDoubleFromParts(BSPWorkspace workspace, IntArrayList indexes, int depth,
            BSPNode oldNode, IntArrayList first, IntArrayList second) {
        BSPNode firstOldNode = null;
        BSPNode secondOldNode = null;
        if (oldNode != null && oldNode.nativeNodeKind == NativeNodeKind.FIXED_DOUBLE) {
            firstOldNode = oldNode.fixedFirst;
            secondOldNode = oldNode.fixedSecond;
        }

        BSPNode firstNode = BSPNode.build(workspace, first, depth, firstOldNode);
        BSPNode secondNode = BSPNode.build(workspace, second, depth, secondOldNode);
        InnerPartitionBSPNode.NodeReuseData reuseData = InnerPartitionBSPNode.prepareNodeReuse(workspace, indexes, depth);
        return new BSPNode(workspace.addNativeFixedDouble(NativeBspTree.Remap.NONE, firstNode, secondNode),
                NativeNodeKind.FIXED_DOUBLE, NO_QUAD, NO_QUAD, null, firstNode, secondNode, null,
                InnerPartitionBSPNode.UNALIGNED_AXIS, Float.NaN, null, null, null,
                null, null, null, reuseData);
    }

    static BSPNode nativeBinaryFromPartitions(BSPWorkspace workspace, IntArrayList indexes, int depth,
            BSPNode oldNode, Partition inside, Partition outside, int axis) {
        float partitionDistance = inside.distance();
        workspace.addAlignedPartitionPlane(axis, partitionDistance);

        BSPNode oldInsideNode = null;
        BSPNode oldOutsideNode = null;
        if (isMatchingNativeBinary(oldNode, axis, ModelQuadFacing.ALIGNED_NORMALS[axis], partitionDistance)) {
            oldInsideNode = oldNode.inside;
            oldOutsideNode = oldNode.outside;
        }

        BSPNode insideNode = null;
        BSPNode outsideNode = null;
        if (inside.quadsBefore() != null) {
            insideNode = BSPNode.build(workspace, inside.quadsBefore(), depth, oldInsideNode);
        }
        if (outside != null) {
            outsideNode = BSPNode.build(workspace, outside.quadsBefore(), depth, oldOutsideNode);
        }
        int[] onPlane = inside.quadsOn() == null ? null : BSPNode.copyIndexes(inside.quadsOn());
        InnerPartitionBSPNode.NodeReuseData reuseData = InnerPartitionBSPNode.prepareNodeReuse(workspace, indexes, depth);
        Vector3fc planeNormal = ModelQuadFacing.ALIGNED_NORMALS[axis];

        return new BSPNode(workspace.addNativeBinary(NativeBspTree.Remap.NONE, planeNormal, partitionDistance,
                insideNode, outsideNode, onPlane), NativeNodeKind.BINARY, NO_QUAD, NO_QUAD, null, null, null,
                planeNormal, axis, partitionDistance, insideNode, outsideNode, onPlane,
                null, null, null, reuseData);
    }

    static BSPNode nativeBinaryFromParts(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode,
            IntArrayList inside, IntArrayList outside, IntArrayList onPlane, int axis, Vector3fc planeNormal,
            float partitionDistance) {
        if (axis == InnerPartitionBSPNode.UNALIGNED_AXIS) {
            workspace.addUnalignedPartitionPlane(planeNormal, partitionDistance);
        } else {
            workspace.addAlignedPartitionPlane(axis, Math.abs(partitionDistance));
        }

        BSPNode oldInsideNode = null;
        BSPNode oldOutsideNode = null;
        if (isMatchingNativeBinary(oldNode, axis, planeNormal, partitionDistance)) {
            oldInsideNode = oldNode.inside;
            oldOutsideNode = oldNode.outside;
        }

        BSPNode insideNode = null;
        BSPNode outsideNode = null;
        if (inside != null) {
            insideNode = BSPNode.build(workspace, inside, depth, oldInsideNode);
        }
        if (outside != null) {
            outsideNode = BSPNode.build(workspace, outside, depth, oldOutsideNode);
        }
        int[] onPlaneArr = BSPNode.copyIndexes(onPlane);
        InnerPartitionBSPNode.NodeReuseData reuseData = InnerPartitionBSPNode.prepareNodeReuse(workspace, indexes, depth);

        return new BSPNode(workspace.addNativeBinary(NativeBspTree.Remap.NONE, planeNormal, partitionDistance,
                insideNode, outsideNode, onPlaneArr), NativeNodeKind.BINARY, NO_QUAD, NO_QUAD, null, null, null,
                planeNormal, axis, partitionDistance, insideNode, outsideNode, onPlaneArr,
                null, null, null, reuseData);
    }

    private static boolean isMatchingNativeBinary(BSPNode oldNode, int axis, Vector3fc planeNormal,
            float partitionDistance) {
        return oldNode != null
                && oldNode.nativeNodeKind == NativeNodeKind.BINARY
                && oldNode.axis == axis
                && (axis != InnerPartitionBSPNode.UNALIGNED_AXIS || oldNode.planeNormal.equals(planeNormal))
                && oldNode.planeDistance == partitionDistance;
    }

    static BSPNode nativeMultiPartitionFromPartitions(BSPWorkspace workspace, IntArrayList indexes, int depth,
            BSPNode oldNode, ReferenceArrayList<Partition> partitions, int axis, boolean endsWithPlane) {
        int planeCount = endsWithPlane ? partitions.size() : partitions.size() - 1;
        float[] planeDistances = new float[planeCount];
        BSPNode[] partitionNodes = new BSPNode[planeCount + 1];
        int[][] onPlaneQuads = new int[planeCount][];

        BSPNode[] oldPartitionNodes = null;
        float[] oldPlaneDistances = null;
        int oldChildIndex = 0;
        float oldPartitionDistance = 0;
        if (oldNode != null
                && oldNode.nativeNodeKind == NativeNodeKind.MULTI_PARTITION
                && oldNode.axis == axis
                && oldNode.partitions.length > 0) {
            oldPartitionNodes = oldNode.partitions;
            oldPlaneDistances = oldNode.planeDistances;
            oldPartitionDistance = oldNode.planeDistances[0];
        }

        // write the partition planes and nodes
        for (int i = 0, count = partitions.size(); i < count; i++) {
            var partition = partitions.get(i);

            // if the partition actually has a plane
            float partitionDistance = Float.NaN;
            if (endsWithPlane || i < count - 1) {
                partitionDistance = partition.distance();
                workspace.addAlignedPartitionPlane(axis, partitionDistance);

                // NOTE: sanity check
                if (Float.isNaN(partitionDistance)) {
                    throw new IllegalStateException("partition distance not set");
                }

                planeDistances[i] = partitionDistance;
            }

            if (partition.quadsBefore() != null) {
                BSPNode oldChild = null;

                if (oldPartitionNodes != null) {
                    // if there's a node that matches the partition's distance, use it as the old
                    // node. Search forwards through the old plane distances to find a candidate
                    while (oldChildIndex < oldPartitionNodes.length && oldPartitionDistance < partitionDistance) {
                        oldChildIndex++;
                        oldPartitionDistance = oldChildIndex < oldPlaneDistances.length
                                ? oldPlaneDistances[oldChildIndex]
                                : Float.NaN;
                    }
                    if (oldChildIndex < oldPartitionNodes.length
                            && (oldPartitionDistance == partitionDistance || Float.isNaN(partitionDistance) && Float.isNaN(oldPartitionDistance))) {
                        oldChild = oldPartitionNodes[oldChildIndex];
                    }
                }

                partitionNodes[i] = BSPNode.build(workspace, partition.quadsBefore(), depth, oldChild);
            }
            if (partition.quadsOn() != null) {
                onPlaneQuads[i] = BSPNode.copyIndexes(partition.quadsOn());
            }
        }

        InnerPartitionBSPNode.NodeReuseData reuseData = InnerPartitionBSPNode.prepareNodeReuse(workspace, indexes, depth);
        Vector3fc planeNormal = ModelQuadFacing.ALIGNED_NORMALS[axis];
        return new BSPNode(workspace.addNativeMultiPartition(NativeBspTree.Remap.NONE,
                planeNormal, planeDistances, partitionNodes, onPlaneQuads),
                NativeNodeKind.MULTI_PARTITION, NO_QUAD, NO_QUAD, null, null, null,
                planeNormal, axis, Float.NaN, null, null, null,
                planeDistances, partitionNodes, onPlaneQuads, reuseData);
    }

    int addTo(NativeBspTree.Builder builder) {
        return switch (this.nativeNodeKind) {
            case LEAF_SINGLE -> builder.addLeafSingle(this.quadA);
            case LEAF_DOUBLE -> builder.addLeafDouble(this.quadA, this.quadB);
            case LEAF_MULTI -> builder.addLeafMulti(this.quads);
            case FIXED_DOUBLE -> builder.addFixedDouble(this.nativeFixedDoubleRemap(),
                    this.fixedFirst.addTo(builder), this.fixedSecond.addTo(builder));
            case BINARY -> {
                int insideIndex = this.inside == null ? NativeBspTree.NULL_NODE : this.inside.addTo(builder);
                int outsideIndex = this.outside == null ? NativeBspTree.NULL_NODE : this.outside.addTo(builder);
                yield builder.addBinary(this.nativeBinaryRemap(), this.planeNormal, this.planeDistance,
                        insideIndex, outsideIndex, this.onPlaneQuads);
            }
            case MULTI_PARTITION -> {
                int[] partitionIndexes = new int[this.partitions.length];
                for (int index = 0; index < this.partitions.length; index++) {
                    partitionIndexes[index] = this.partitions[index] == null
                            ? NativeBspTree.NULL_NODE
                            : this.partitions[index].addTo(builder);
                }
                yield builder.addMultiPartition(this.nativeMultiPartitionRemap(), this.planeNormal,
                        this.planeDistances, partitionIndexes, this.multiOnPlaneQuads);
            }
            case NONE -> throw new IllegalStateException("BSP node does not implement native export");
        };
    }

    static BSPNode attemptNativeFixedDoubleReuse(BSPWorkspace workspace, IntArrayList newIndexes, BSPNode oldNode) {
        if (oldNode == null || oldNode.nativeNodeKind != NativeNodeKind.FIXED_DOUBLE || oldNode.reuseData == null) {
            return null;
        }

        NativeBspTree.Remap remap = InnerPartitionBSPNode.prepareNativeNodeReuse(workspace, newIndexes,
                oldNode.reuseData);
        if (remap == null) {
            return null;
        }

        oldNode.applyNativeFixedDoubleRemap(remap);
        oldNode.rebuildNativeNode(workspace);
        return oldNode;
    }

    static BSPNode attemptNativeMultiPartitionReuse(BSPWorkspace workspace, IntArrayList newIndexes, BSPNode oldNode) {
        if (oldNode == null || oldNode.nativeNodeKind != NativeNodeKind.MULTI_PARTITION || oldNode.reuseData == null) {
            return null;
        }

        NativeBspTree.Remap remap = InnerPartitionBSPNode.prepareNativeNodeReuse(workspace, newIndexes,
                oldNode.reuseData);
        if (remap == null) {
            return null;
        }

        oldNode.applyNativeMultiPartitionRemap(remap);
        oldNode.addPartitionPlanes(workspace);
        oldNode.rebuildNativeNode(workspace);
        return oldNode;
    }

    static BSPNode attemptNativeBinaryReuse(BSPWorkspace workspace, IntArrayList newIndexes, BSPNode oldNode) {
        if (oldNode == null || oldNode.nativeNodeKind != NativeNodeKind.BINARY || oldNode.reuseData == null) {
            return null;
        }

        NativeBspTree.Remap remap = InnerPartitionBSPNode.prepareNativeNodeReuse(workspace, newIndexes,
                oldNode.reuseData);
        if (remap == null) {
            return null;
        }

        oldNode.applyNativeBinaryRemap(remap);
        oldNode.addPartitionPlanes(workspace);
        oldNode.rebuildNativeNode(workspace);
        return oldNode;
    }

    void addPartitionPlanes(BSPWorkspace workspace) {
        switch (this.nativeNodeKind) {
            case BINARY -> {
                if (this.axis == InnerPartitionBSPNode.UNALIGNED_AXIS) {
                    workspace.addUnalignedPartitionPlane(this.planeNormal, this.planeDistance);
                } else {
                    workspace.addAlignedPartitionPlane(this.axis, this.planeDistance);
                }

                if (this.inside != null) {
                    this.inside.addPartitionPlanes(workspace);
                }
                if (this.outside != null) {
                    this.outside.addPartitionPlanes(workspace);
                }
            }
            case MULTI_PARTITION -> {
                for (float distance : this.planeDistances) {
                    workspace.addAlignedPartitionPlane(this.axis, distance);
                }

                for (BSPNode partition : this.partitions) {
                    if (partition != null) {
                        partition.addPartitionPlanes(workspace);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void applyNativeFixedDoubleRemap(NativeBspTree.Remap remap) {
        this.indexMap = remap.indexMap();
        this.fixedIndexOffset = remap.kind() == 1
                ? remap.fixedIndexOffset()
                : InnerPartitionBSPNode.NO_FIXED_OFFSET;
    }

    private NativeBspTree.Remap nativeFixedDoubleRemap() {
        return InnerPartitionBSPNode.nativeRemap(this.reuseData, this.indexMap, this.fixedIndexOffset);
    }

    private void applyNativeBinaryRemap(NativeBspTree.Remap remap) {
        this.indexMap = remap.indexMap();
        this.fixedIndexOffset = remap.kind() == 1
                ? remap.fixedIndexOffset()
                : InnerPartitionBSPNode.NO_FIXED_OFFSET;
    }

    private NativeBspTree.Remap nativeBinaryRemap() {
        return InnerPartitionBSPNode.nativeRemap(this.reuseData, this.indexMap, this.fixedIndexOffset);
    }

    private void applyNativeMultiPartitionRemap(NativeBspTree.Remap remap) {
        this.indexMap = remap.indexMap();
        this.fixedIndexOffset = remap.kind() == 1
                ? remap.fixedIndexOffset()
                : InnerPartitionBSPNode.NO_FIXED_OFFSET;
    }

    private NativeBspTree.Remap nativeMultiPartitionRemap() {
        return InnerPartitionBSPNode.nativeRemap(this.reuseData, this.indexMap, this.fixedIndexOffset);
    }

    final int nativeNodeIndex() {
        if (this.nativeNodeIndex == NativeBspTree.NULL_NODE) {
            throw new IllegalStateException("BSP node has not been added to the native tree");
        }
        return this.nativeNodeIndex;
    }

    final void rebuildNativeNode(BSPWorkspace workspace) {
        this.nativeNodeIndex = this.addTo(workspace.nativeTreeBuilder());
    }

    public static NativeBspBuildResult buildBSP(TQuad[] quads, SectionPos sectionPos, BSPNode oldRoot,
            boolean prepareNodeReuse, QuadSplittingMode quadSplittingMode) {
        // throw if there's too many quads
        InnerPartitionBSPNode.validateQuadCount(quads.length);

        // create a workspace and then the nodes figure out the recursive building.
        // throws if the BSP can't be built, null if none is necessary
        try (var workspace = new BSPWorkspace(quads, sectionPos, prepareNodeReuse, quadSplittingMode)) {
            // initialize the indexes to all quads
            int[] initialIndexes = new int[quads.length];
            for (int i = 0; i < quads.length; i++) {
                initialIndexes[i] = i;
            }
            var allIndexes = new IntArrayList(initialIndexes);

            var rootNode = BSPNode.build(workspace, allIndexes, -1, oldRoot);
            NativeUpdatedQuads updatedQuads = workspace.getFinalizedUpdatedQuads();
            int indexQuadCount = updatedQuads == null ? quads.length : updatedQuads.getIndexQuadCount();
            NativeBspBuildResult result = workspace.result;
            result.finish(rootNode, indexQuadCount, updatedQuads, workspace.finishNativeTree(rootNode, indexQuadCount));
            workspace.releaseResult();
            return result;
        }
    }

    static BSPNode build(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode) {
        depth++;

        // pick which type of node to create for the given workspace
        if (indexes.isEmpty()) {
            return null;
        } else if (indexes.size() == 1) {
            return nativeLeafSingle(workspace, indexes.getInt(0));
        } else if (indexes.size() == 2) {
            var quadIndexA = indexes.getInt(0);
            var quadIndexB = indexes.getInt(1);
            if (workspace.doubleLeafPossible(quadIndexA, quadIndexB, workspace.canSplitQuads())) {
                return nativeLeafDouble(workspace, quadIndexA, quadIndexB);
            }
        }

        return InnerPartitionBSPNode.build(workspace, indexes, depth, oldNode);
    }

    static int[] copyIndexes(IntArrayList indexes) {
        return copyIndexes(indexes, true);
    }

    static int[] copyIndexes(IntArrayList indexes, boolean doSort) {
        int[] output = indexes.toIntArray();
        if (doSort) {
            Arrays.sort(output);
        }
        return output;
    }

    static int[] copyIndexes(int[] indexes, boolean doSort) {
        int[] output = Arrays.copyOf(indexes, indexes.length);
        if (doSort) {
            Arrays.sort(output);
        }
        return output;
    }
}
