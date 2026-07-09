package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

/**
 * Partitions quads into multiple child BSP nodes with multiple parallel
 * partition planes. This is uses less memory and time than constructing a
 * binary BSP tree through more partitioning passes.
 *
 * Implementation note: Detecting and avoiding the double array when possible
 * brings no performance benefit in sorting speed, only a building speed
 * detriment.
 */
class InnerMultiPartitionBSPNode extends InnerPartitionBSPNode {
    private final float[] planeDistances; // one less than there are partitions

    private final BSPNode[] partitions;
    private final int[][] onPlaneQuads;

    InnerMultiPartitionBSPNode(NodeReuseData reuseData, int axis, float[] planeDistances,
            BSPNode[] partitions, int[][] onPlaneQuads) {
        super(reuseData, axis);
        this.planeDistances = planeDistances;
        this.partitions = partitions;
        this.onPlaneQuads = onPlaneQuads;
    }

    @Override
    void addPartitionPlanes(BSPWorkspace workspace) {
        for (int i = 0; i < this.planeDistances.length; i++) {
            workspace.addAlignedPartitionPlane(this.axis, this.planeDistances[i]);
        }

        // recurse on children to also add their planes
        for (var partition : this.partitions) {
            if (partition instanceof InnerPartitionBSPNode inner) {
                inner.addPartitionPlanes(workspace);
            }
        }
    }

    @Override
    int addTo(NativeBspTree.Builder builder) {
        int[] partitionIndexes = new int[this.partitions.length];
        for (int index = 0; index < this.partitions.length; index++) {
            partitionIndexes[index] = this.partitions[index] == null
                    ? NativeBspTree.NULL_NODE
                    : this.partitions[index].addTo(builder);
        }

        return builder.addMultiPartition(this.nativeRemap(), this.planeNormal,
                this.planeDistances, partitionIndexes, this.onPlaneQuads);
    }

    static BSPNode buildFromPartitions(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode,
            ReferenceArrayList<Partition> partitions, int axis, boolean endsWithPlane) {
        int planeCount = endsWithPlane ? partitions.size() : partitions.size() - 1;
        float[] planeDistances = new float[planeCount];
        BSPNode[] partitionNodes = new BSPNode[planeCount + 1];
        int[][] onPlaneQuads = new int[planeCount][];

        BSPNode[] oldPartitionNodes = null;
        float[] oldPlaneDistances = null;
        int oldChildIndex = 0;
        float oldPartitionDistance = 0;
        if (oldNode instanceof InnerMultiPartitionBSPNode multiNode
                && multiNode.axis == axis && multiNode.partitions.length > 0) {
            oldPartitionNodes = multiNode.partitions;
            oldPlaneDistances = multiNode.planeDistances;
            oldPartitionDistance = multiNode.planeDistances[0];
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

        return new InnerMultiPartitionBSPNode(prepareNodeReuse(workspace, indexes, depth),
                axis, planeDistances, partitionNodes, onPlaneQuads);
    }
}
