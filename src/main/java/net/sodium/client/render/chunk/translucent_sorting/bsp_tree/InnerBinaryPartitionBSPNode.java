package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Partitions quads into two sides, each its own BSP node, of a partition plane
 * and a set of quads that lie on the plane.
 */
class InnerBinaryPartitionBSPNode extends InnerPartitionBSPNode {
    private final float planeDistance;

    // side towards which the normal points
    private final BSPNode inside; // nullable
    private final BSPNode outside; // nullable
    private final int[] onPlaneQuads;

    InnerBinaryPartitionBSPNode(NodeReuseData reuseData, float planeDistance, int axis,
                                BSPNode inside, BSPNode outside, int[] onPlaneQuads) {
        super(reuseData, axis);
        this.planeDistance = planeDistance;
        this.inside = inside;
        this.outside = outside;
        this.onPlaneQuads = onPlaneQuads;
    }

    InnerBinaryPartitionBSPNode(NodeReuseData reuseData, float planeDistance, Vector3fc planeNormal,
                                BSPNode inside, BSPNode outside, int[] onPlaneQuads) {
        super(reuseData, planeNormal);
        this.planeDistance = planeDistance;
        this.inside = inside;
        this.outside = outside;
        this.onPlaneQuads = onPlaneQuads;
    }

    @Override
    void addPartitionPlanes(BSPWorkspace workspace) {
        if (this.axis == UNALIGNED_AXIS) {
            workspace.addUnalignedPartitionPlane(this.planeNormal, this.planeDistance);
        } else {
            workspace.addAlignedPartitionPlane(this.axis, this.planeDistance);
        }

        // also add the planes of the children
        if (this.inside instanceof InnerPartitionBSPNode insideChild) {
            insideChild.addPartitionPlanes(workspace);
        }
        if (this.outside instanceof InnerPartitionBSPNode outsideChild) {
            outsideChild.addPartitionPlanes(workspace);
        }
    }

    @Override
    int addTo(NativeBspTree.Builder builder) {
        int insideIndex = this.inside == null ? NativeBspTree.NULL_NODE : this.inside.addTo(builder);
        int outsideIndex = this.outside == null ? NativeBspTree.NULL_NODE : this.outside.addTo(builder);
        return builder.addBinary(this.nativeRemap(), this.planeNormal, this.planeDistance,
                insideIndex, outsideIndex, this.onPlaneQuads);
    }

    static BSPNode buildFromPartitions(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode,
            Partition inside, Partition outside, int axis) {
        var partitionDistance = inside.distance();
        workspace.addAlignedPartitionPlane(axis, partitionDistance);

        BSPNode oldInsideNode = null;
        BSPNode oldOutsideNode = null;
        if (oldNode instanceof InnerBinaryPartitionBSPNode binaryNode
                && binaryNode.axis == axis
                && binaryNode.planeDistance == partitionDistance) {
            oldInsideNode = binaryNode.inside;
            oldOutsideNode = binaryNode.outside;
        }

        BSPNode insideNode = null;
        BSPNode outsideNode = null;
        if (inside.quadsBefore() != null) {
            insideNode = BSPNode.build(workspace, inside.quadsBefore(), depth, oldInsideNode);
        }
        if (outside != null) {
            outsideNode = BSPNode.build(workspace, outside.quadsBefore(), depth, oldOutsideNode);
        }
        var onPlane = inside.quadsOn() == null ? null : BSPNode.copyIndexes(inside.quadsOn());

        return new InnerBinaryPartitionBSPNode(
                prepareNodeReuse(workspace, indexes, depth),
                partitionDistance, axis,
                insideNode, outsideNode, onPlane);
    }

    static BSPNode buildFromParts(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode,
                                  IntArrayList inside, IntArrayList outside, IntArrayList onPlane, int axis, Vector3fc planeNormal, float partitionDistance) {
        if (axis == UNALIGNED_AXIS) {
            workspace.addUnalignedPartitionPlane(planeNormal, partitionDistance);
        } else {
            workspace.addAlignedPartitionPlane(axis, Math.abs(partitionDistance));
        }

        BSPNode oldInsideNode = null;
        BSPNode oldOutsideNode = null;
        if (oldNode instanceof InnerBinaryPartitionBSPNode binaryNode &&
                binaryNode.axis == axis &&
                (axis == UNALIGNED_AXIS || binaryNode.planeNormal.equals(planeNormal)) &&
                binaryNode.planeDistance == partitionDistance) {
            oldInsideNode = binaryNode.inside;
            oldOutsideNode = binaryNode.outside;
        }

        BSPNode insideNode = null;
        BSPNode outsideNode = null;
        if (inside != null) {
            insideNode = BSPNode.build(workspace, inside, depth, oldInsideNode);
        }
        if (outside != null) {
            outsideNode = BSPNode.build(workspace, outside, depth, oldOutsideNode);
        }
        var onPlaneArr = BSPNode.copyIndexes(onPlane);

        // always use the correct plane normal here because just specifying the axis causes the constructor to use a wrong and unsigned normal
        return new InnerBinaryPartitionBSPNode(
                prepareNodeReuse(workspace, indexes, depth),
                partitionDistance, planeNormal,
                insideNode, outsideNode, onPlaneArr);
    }
}
