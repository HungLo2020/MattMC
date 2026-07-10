package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentGeometryAnalyzer;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.translucent_sorting.quad.NativeFullTQuad;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.api.util.MathUtil;
import net.sodium.client.util.sorting.RadixSort;
import net.minecraft.util.Mth;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.Random;

/**
 * Performs aligned BSP partitioning of many nodes and constructs appropriate
 * BSP nodes based on the result.
 * <p>
 * Implementation notes:
 * - Presorting the points in block-sized buckets doesn't help. It seems the
 * sort algorithm is just fast enough to handle this.
 * - Eliminating the use of partition objects doesn't help. Since there's
 * usually just very few partitions, it's not worth it, it seems.
 * - Using fastutil's LongArrays sorting options (radix and quicksort) is slower
 * than using Arrays.sort (which uses DualPivotQuicksort internally), even on
 * worlds with player-built structures.
 * - A simple attempt at lazily writing index data to the buffer didn't yield a
 * performance improvement. Maybe applying it to the multi partition node would
 * be more effective (but also much more complex and slower).
 * <p>
 * The encoding doesn't currently support negative distances (nor does such
 * support appear to be required). Their ordering is wrong when sorting them by
 * their binary representation. To fix this: "XOR all positive numbers with
 * 0x8000... and negative numbers with 0xffff... This should flip the sign bit
 * on both (so negative numbers go first), and then reverse the ordering on
 * negative numbers." from <a href="https://stackoverflow.com/q/43299299">StackOverflow</a>
 * <p>
 * When aligned partitioning fails the geometry is checked for intersection. If
 * there is intersection it means the section is unsortable and an approximation
 * is used instead. When it doesn't intersect but is not aligned partitionable,
 * it either requires unaligned partitioning (a hard problem not solved here)
 * or it's unpartitionable. It would be possible to insert a topo sorting node
 * here, but it's not worth the implementation effort unless it's found to be a
 * reasonable and common use case (I haven't been able to determine that it is).
 */
abstract class InnerPartitionBSPNode extends BSPNode {
    private static final int NODE_REUSE_THRESHOLD = 30;
    private static final int MAX_INTERSECTION_ATTEMPTS = 500;
    static final int NO_FIXED_OFFSET = Integer.MIN_VALUE;
    protected static final int UNALIGNED_AXIS = -1;

    final Vector3fc planeNormal;
    final int axis;

    int[] indexMap;
    int fixedIndexOffset = NO_FIXED_OFFSET;
    final NodeReuseData reuseData; // nullable

    /**
     * Stores data required for testing if the node can be re-used. This data is
     * only generated for select candidate nodes.
     * <p>
     * It only stores the set of indexes that this node was constructed from and
     * their extents since the BSP construction only cares about the "opaque" quad
     * geometry and not the normal or facing.
     * <p>
     * Since the indexes might be compressed, the count needs to be stored
     * separately from before compression.
     */
    record NodeReuseData(float[][] quadExtents, int[] indexes, int indexCount, int maxIndex) {
    }

    InnerPartitionBSPNode(NodeReuseData reuseData, int axis) {
        this.planeNormal = ModelQuadFacing.ALIGNED_NORMALS[axis];
        this.axis = axis;
        this.reuseData = reuseData;
    }

    InnerPartitionBSPNode(NodeReuseData reuseData, Vector3fc planeNormal) {
        this.planeNormal = planeNormal;
        this.axis = UNALIGNED_AXIS;
        this.reuseData = reuseData;
    }

    abstract void addPartitionPlanes(BSPWorkspace workspace);

    static NodeReuseData prepareNodeReuse(BSPWorkspace workspace, IntArrayList indexes, int depth) {
        // if node reuse is enabled, only enable on the first level of children (not the
        // root node and not anything deeper than its children)
        if (workspace.prepareNodeReuse && depth == 1 && indexes.size() > NODE_REUSE_THRESHOLD) {
            // collect the extents of the indexed quads and hash them
            var quadExtents = new float[indexes.size()][];
            int maxIndex = -1;
            for (int i = 0; i < indexes.size(); i++) {
                var index = indexes.getInt(i);
                var quad = workspace.get(index);
                var extents = quad.getExtents();
                quadExtents[i] = extents;
                maxIndex = Math.max(maxIndex, index);
            }

            // compress indexes but without sorting them, as the order needs to be the same
            // for the extents comparison loop to work
            return new NodeReuseData(
                    quadExtents,
                    BSPNode.copyIndexes(indexes, false),
                    indexes.size(),
                    maxIndex);
        }
        return null;
    }

    private static class IndexRemapper implements IntConsumer {
        private final int[] indexMap;
        private final IntArrayList newIndexes;
        private int index = 0;
        private int firstOffset = 0;

        private static final int OFFSET_CHANGED = Integer.MIN_VALUE;

        IndexRemapper(int length, IntArrayList newIndexes) {
            this.indexMap = new int[length];
            this.newIndexes = newIndexes;
        }

        @Override
        public void accept(int oldIndex) {
            var newIndex = this.newIndexes.getInt(this.index);
            this.indexMap[oldIndex] = newIndex;
            var newOffset = newIndex - oldIndex;
            if (this.index == 0) {
                this.firstOffset = newOffset;
            } else if (this.firstOffset != newOffset) {
                this.firstOffset = OFFSET_CHANGED;
            }
            this.index++;
        }

        boolean hasFixedOffset() {
            return this.firstOffset != OFFSET_CHANGED;
        }
    }

    static InnerPartitionBSPNode attemptNodeReuse(BSPWorkspace workspace, IntArrayList newIndexes, InnerPartitionBSPNode oldNode) {
        if (oldNode == null) {
            return null;
        }

        oldNode.indexMap = null;
        oldNode.fixedIndexOffset = NO_FIXED_OFFSET;

        var reuseData = oldNode.reuseData;
        if (reuseData == null) {
            return null;
        }

        var oldExtents = reuseData.quadExtents;
        if (oldExtents.length != newIndexes.size()) {
            return null;
        }

        for (int i = 0; i < newIndexes.size(); i++) {
            if (!workspace.get(newIndexes.getInt(i)).extentsEqual(oldExtents[i])) {
                return null;
            }
        }

        // reuse old node and either apply a fixed offset or calculate an index map to
        // map from old to new indices
        var remapper = new IndexRemapper(reuseData.maxIndex + 1, newIndexes);
        for (int index : reuseData.indexes) {
            remapper.accept(index);
        }

        // use a fixed offset if possible (if all old indices differ from the new ones
        // by the same amount)
        if (remapper.hasFixedOffset()) {
            oldNode.fixedIndexOffset = remapper.firstOffset;
        } else {
            oldNode.indexMap = remapper.indexMap;
        }

        // import the triggering data from the old node to ensure it still triggers at
        // the right time
        oldNode.addPartitionPlanes(workspace);

        return oldNode;
    }

    NativeBspTree.Remap nativeRemap() {
        if (this.indexMap != null) {
            return new NativeBspTree.Remap(2, this.reuseData.indexCount(), 0, this.indexMap);
        }
        if (this.fixedIndexOffset != NO_FIXED_OFFSET) {
            return new NativeBspTree.Remap(1, this.reuseData.indexCount(), this.fixedIndexOffset, null);
        }
        return NativeBspTree.Remap.NONE;
    }

    /**
     * Encoding with {@link MathUtil#floatToComparableInt(float)} is necessary here to ensure negative distances are not sorted backwards. Simply converting potentially negative floats to int bits using {@link Float#floatToRawIntBits(float)} would sort negative floats backwards amongst themselves.
     * <p>
     * Note that negative floats convert to negative integers with this method which is ok, since it yields an overall negative long that gets sorted correctly before the longs that encode positive floats as distances.
     */
    private static long encodeIntervalPoint(float distance, int quadIndex, int type) {
        return ((long) MathUtil.floatToComparableInt(distance) << 32) | ((long) type << 30) | quadIndex;
    }

    private static float decodeDistance(long encoded) {
        return MathUtil.comparableIntToFloat((int) (encoded >>> 32));
    }

    private static int decodeQuadIndex(long encoded) {
        return (int) (encoded & 0x3FFFFFFF);
    }

    private static int decodeType(long encoded) {
        return (int) (encoded >>> 30) & 0b11;
    }

    public static void validateQuadCount(int quadCount) {
        if (quadCount * 2 > 0x3FFFFFFF) {
            throw new IllegalArgumentException("Too many quads: " + quadCount);
        }
    }

    // the indices of the type are chosen such that tie-breaking items that have the
    // same distance with the type ascending yields a beneficial sort order
    // (END of the current interval, on-edge quads, then the START of the next
    // interval)

    // the start of a quad's extent in this direction
    private static final int INTERVAL_START = 2;

    // the end of a quad's extent in this direction
    private static final int INTERVAL_END = 0;

    // looking at a quad from the side where it has zero thickness
    private static final int INTERVAL_SIDE = 1;

    static BSPNode build(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode) {
        // attempt reuse of the old node if possible
        if (oldNode instanceof InnerPartitionBSPNode oldInnerNode) {
            var reusedNode = InnerPartitionBSPNode.attemptNodeReuse(workspace, indexes, oldInnerNode);
            if (reusedNode != null) {
                return reusedNode;
            }
        }

        ReferenceArrayList<Partition> partitions = new ReferenceArrayList<>();
        LongArrayList points = new LongArrayList((int) (indexes.size() * 1.5));

        // keep track of global best splitting group for splitting quads if enabled
        IntArrayList bestSplittingGroup = null;
        IntArrayList splittingGroup = null;
        boolean canSplitQuads = workspace.canSplitQuads();
        if (canSplitQuads) {
            bestSplittingGroup = new IntArrayList(5);
            splittingGroup = new IntArrayList(5);
        }

        // find any aligned partition, search each axis
        for (int axisCount = 0; axisCount < 3; axisCount++) {
            int axis = (axisCount + depth + 1) % 3;
            var oppositeDirection = axis + 3;
            int alignedFacingBitmap = 0;
            boolean onlyIntervalSide = true;

            // collect all the geometry's start and end points in this direction
            points.clear();
            for (int i = 0, size = indexes.size(); i < size; i++) {
                int quadIndex = indexes.getInt(i);
                var quad = workspace.get(quadIndex);
                var extents = quad.getExtents();
                var posExtent = extents[axis];
                var negExtent = extents[oppositeDirection];
                if (posExtent == negExtent) {
                    points.add(encodeIntervalPoint(posExtent, quadIndex, INTERVAL_SIDE));
                } else {
                    points.add(encodeIntervalPoint(posExtent, quadIndex, INTERVAL_END));
                    points.add(encodeIntervalPoint(negExtent, quadIndex, INTERVAL_START));
                    onlyIntervalSide = false;
                }

                alignedFacingBitmap |= 1 << quad.getFacing().ordinal();
            }

            // simplified SNR heuristic as seen in TranslucentGeometryCollector#sortTypeHeuristic (case D)
            if (!ModelQuadFacing.bitmapHasUnassigned(alignedFacingBitmap)) {
                int alignedNormalCount = Integer.bitCount(alignedFacingBitmap);
                if (alignedNormalCount == 1 || alignedNormalCount == 2 && ModelQuadFacing.bitmapIsOpposingAligned(alignedFacingBitmap)) {
                    // this can be handled with SNR instead of partitioning,
                    // instead create a fixed order node that uses SNR sorting

                    // check if the geometry is aligned to the axis
                    if (onlyIntervalSide) {
                        // this means the already generated points array can be used
                        return buildSNRLeafNodeFromPoints(workspace, points);
                    } else {
                        return buildSNRLeafNodeFromQuads(workspace, indexes);
                    }
                }
            }

            // sort interval points by distance ascending and then by type. Sorting the
            // longs directly has the same effect because of the encoding.
            Arrays.sort(points.elements(), 0, points.size());

            // the current partition plane distance (dot product),
            // updated when an interval ends or a side is encountered, used to add quads to quadsOn
            float distance = Float.NaN;

            // set of quads that are within the partition
            IntArrayList quadsBefore = null;

            // set of quads that are on the partition plane
            IntArrayList quadsOn = null;

            // number of overlapping intervals along the projection axis
            int thickness = 0;

            // lazily generate partitions by keeping track of the current interval thickness and the quads that are on the partition plane
            partitions.clear();
            if (canSplitQuads) {
                splittingGroup.clear();
            }
            float splitDistance = Float.NaN;
            for (int i = 0, size = points.size(); i < size; i++) {
                long point = points.getLong(i);
                switch (decodeType(point)) {
                    case INTERVAL_START -> {
                        // unless at the start, flush if there's a gap
                        if (thickness == 0 && (quadsBefore != null || quadsOn != null)) {
                            partitions.add(new Partition(distance, quadsBefore, quadsOn));
                            distance = Float.NaN;
                            quadsBefore = null;
                            quadsOn = null;
                        }

                        thickness++;

                        // flush to partition if still writing last partition
                        if (quadsOn != null) {
                            if (Float.isNaN(distance)) {
                                throw new IllegalStateException("distance not set");
                            }
                            partitions.add(new Partition(distance, quadsBefore, quadsOn));
                            distance = Float.NaN;
                            quadsOn = null;
                        }
                        if (quadsBefore == null) {
                            quadsBefore = new IntArrayList();
                        }
                        quadsBefore.add(decodeQuadIndex(point));
                    }
                    case INTERVAL_END -> {
                        thickness--;
                        if (quadsOn == null) {
                            distance = decodeDistance(point);
                        }
                    }
                    case INTERVAL_SIDE -> {
                        // if this point in a gap, it can be put on the plane itself
                        int pointQuadIndex = decodeQuadIndex(point);
                        if (thickness == 0) {
                            float pointDistance = decodeDistance(point);
                            if (quadsOn == null) {
                                // no partition end created yet, set here
                                quadsOn = new IntArrayList();
                                distance = pointDistance;
                            } else if (distance != pointDistance) {
                                // partition end has passed already, flush for new partition plane distance
                                partitions.add(new Partition(distance, quadsBefore, quadsOn));
                                distance = pointDistance;
                                quadsBefore = null;
                                quadsOn = new IntArrayList();
                            }
                            quadsOn.add(pointQuadIndex);
                        } else {
                            // add this point quad to the quads before the partition plane
                            if (quadsBefore == null) {
                                throw new IllegalStateException("there must be started intervals here");
                            }
                            quadsBefore.add(pointQuadIndex);

                            // update the splitting group if the distance didn't change
                            if (canSplitQuads) {
                                var ownDistance = decodeDistance(point);
                                if (ownDistance == splitDistance || Float.isNaN(splitDistance)) {
                                    splittingGroup.add(pointQuadIndex);
                                } else {
                                    flushBestSplittingGroup(splittingGroup, bestSplittingGroup, axis);
                                }
                                splitDistance = ownDistance;
                            }
                        }
                    }
                }
            }

            // check if the splitting group needs to be flushed
            if (canSplitQuads) {
                flushBestSplittingGroup(splittingGroup, bestSplittingGroup, axis);
            }

            // check a different axis if everything is in one quadsBefore,
            // which means there are no gaps
            if (quadsBefore != null && quadsBefore.size() == indexes.size()) {
                continue;
            }

            // check if there's a trailing plane. Otherwise, the last plane has distance -1
            // since it just holds the trailing quads
            boolean endsWithPlane = quadsOn != null;

            // flush the last partition, use the -1 distance to indicate the end if it
            // doesn't use quadsOn (which requires a certain distance to be given)
            if (quadsBefore != null || quadsOn != null) {
                partitions.add(new Partition(endsWithPlane ? distance : Float.NaN, quadsBefore, quadsOn));
            }

            // check if this can be turned into a binary partition node
            // (if there's at most two partitions and one plane)
            if (partitions.size() <= 2) {
                // get the two partitions
                var inside = partitions.get(0);
                var outside = partitions.size() == 2 ? partitions.get(1) : null;
                if (outside == null || !endsWithPlane) {
                    return InnerBinaryPartitionBSPNode.buildFromPartitions(workspace, indexes, depth, oldNode,
                            inside, outside, axis);
                }
            }

            // create a multi-partition node
            return InnerMultiPartitionBSPNode.buildFromPartitions(workspace, indexes, depth, oldNode,
                    partitions, axis, endsWithPlane);
        }

        if (canSplitQuads) {
            // try static topo sorting first because splitting quads is even more expensive
            var multiLeafNode = buildTopoMultiLeafNode(workspace, indexes, true);
            if (multiLeafNode != null) {
                return multiLeafNode;
            }

            // perform quad splitting to get a sortable result whether it's intersecting or just unsortable as-is
            return handleUnsortableBySplitting(workspace, indexes, depth, oldNode, bestSplittingGroup);
        } else {
            var intersectingHandling = handleIntersecting(workspace, indexes, depth, oldNode);
            if (intersectingHandling != null) {
                return intersectingHandling;
            }

            // attempt topo sorting on the geometry if intersection handling failed
            var multiLeafNode = buildTopoMultiLeafNode(workspace, indexes, false);
            if (multiLeafNode == null) {
                throw new BSPBuildFailureException("No partition found but not intersecting and can't be statically topo sorted");
            }
            return multiLeafNode;
        }
    }

    static void flushBestSplittingGroup(IntArrayList splittingGroup, IntArrayList bestSplittingGroup, int axis) {
        var currentSize = splittingGroup.size();
        var newSize = bestSplittingGroup.size();

        // use new splitting group if it's bigger but prefer y-axis for splitting if they're the same size
        if (currentSize > newSize || currentSize == newSize && axis == 1) {
            bestSplittingGroup.clear();
            bestSplittingGroup.addAll(splittingGroup);
        }
        splittingGroup.clear();
    }

    static private BSPNode handleUnsortableBySplitting(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode, IntArrayList splittingGroup) {
        // pick the first quad if there's no prepared splitting group
        int representativeIndex;
        if (splittingGroup.isEmpty()) {
            representativeIndex = indexes.getInt(0);
            splittingGroup.add(representativeIndex);
        } else {
            representativeIndex = splittingGroup.getInt(0);
        }
        var representative = (NativeFullTQuad) workspace.get(representativeIndex);
        var representativeFacing = representative.getFacing();
        int initialSplittingGroupSize = splittingGroup.size();

        // split all quads by the splitting group's plane
        var splitPlane = representative.getVeryAccurateNormal();
        var splitDistance = representative.getAccurateDotProduct();

        IntArrayList inside = new IntArrayList();
        IntArrayList outside = new IntArrayList();

        for (int candidateIndex : indexes) {
            // eliminate quads that are already in the splitting group
            var isInSplittingGroup = false;
            for (int i = 0; i < initialSplittingGroupSize; i++) {
                if (candidateIndex == splittingGroup.getInt(i)) {
                    isInSplittingGroup = true;
                }
            }
            if (isInSplittingGroup) {
                continue;
            }

            var insideQuad = (NativeFullTQuad) workspace.get(candidateIndex);
            var quadFacing = insideQuad.getFacing();

            // eliminate quads that lie in the split plane
            if (quadFacing == representativeFacing && insideQuad.getAccurateDotProduct() == splitDistance &&
                    (representativeFacing != ModelQuadFacing.UNASSIGNED ||
                            insideQuad.getVeryAccurateNormal().equals(splitPlane))) {
                splittingGroup.add(candidateIndex);
                continue;
            }

            // split the geometry with the plane
            splitCandidate(workspace, splittingGroup, candidateIndex, insideQuad, splitPlane, splitDistance, outside, inside);
        }

        ModelQuadFacing facing;
        Vector3fc normal;
        float dotProduct;
        if (workspace.quantizeTriggerNormals) {
            facing = representative.useQuantizedFacing();
            normal = representative.getQuantizedNormal();
            dotProduct = representative.getQuantizedDotProduct();
        } else {
            facing = representativeFacing;
            normal = splitPlane;
            dotProduct = splitDistance;
        }
        int axis = UNALIGNED_AXIS;
        if (facing.isAligned()) {
            axis = facing.getAxis();
        }

        return InnerBinaryPartitionBSPNode.buildFromParts(
                workspace, indexes, depth, oldNode, inside, outside, splittingGroup, axis,
                normal, dotProduct);
    }

    private static void splitCandidate(BSPWorkspace workspace, IntArrayList splittingGroup, int candidateIndex, NativeFullTQuad insideQuad, Vector3fc splitPlane, float splitDistance, IntArrayList outside, IntArrayList inside) {
        // Lines or points (2 or 1 vertices) should have been filtered out
        var uniqueVertexMap = insideQuad.getUniqueVertexMap();
        var uniqueVertices = Integer.bitCount(uniqueVertexMap);
        if (uniqueVertices < 3) {
            throw new IllegalStateException("Unexpected quad with less than 3 unique vertices");
        }

        // calculate inside/outside for each vertex
        int[] classification = insideQuad.classifyAgainst(splitPlane, splitDistance);
        int insideMapUnmasked = classification[0];
        int onPlaneMapUnmasked = classification[1];

        // filter out the vertices that are duplicated to handle triangles
        var insideMap = insideMapUnmasked & uniqueVertexMap;
        var onPlaneMap = onPlaneMapUnmasked & uniqueVertexMap;

        // Quads or triangles that are fully on the plane are added to the splitting group.
        // Bent quads are not dealt with by this and will simply produce unexpected behavior.
        // Adding quads with three unique vertices on the plane to the splitting group doesn't work when floating point errors
        // cause quads to have effectively three unique vertices without signaling so in their uniqueVertexMap.
        if (onPlaneMap == uniqueVertexMap) {
            splittingGroup.add(candidateIndex);
            return;
        }

        // the quad is outside if all vertices are either outside or on the plane
        if (insideMap == 0) {
            outside.add(candidateIndex);
            return;
        }

        // the quad is inside if all vertices are either inside or on the plane
        if ((insideMap | onPlaneMap) == uniqueVertexMap) { // 0b1111 for quads
            inside.add(candidateIndex);
            return;
        }

        var onPlaneCount = Integer.bitCount(onPlaneMap);
        var insideCount = Integer.bitCount(insideMap);

        // cancel splitting after handling special cases if the new geometry limit has been reached
        if (!workspace.canSplitQuads()) {
            // put on, inside, outside based on which side has the most vertices
            var outsideCount = 4 - insideCount - onPlaneCount;
            if (onPlaneCount >= insideCount && onPlaneCount >= outsideCount) {
                splittingGroup.add(candidateIndex);
            } else if (insideCount >= outsideCount) {
                inside.add(candidateIndex);
            } else {
                outside.add(candidateIndex);
            }
            return;
        }

        NativeFullTQuad outsideQuad = NativeFullTQuad.splittingCopy(insideQuad);
        NativeFullTQuad secondOutsideQuad = null;
        NativeFullTQuad secondInsideQuad = null;

        if (uniqueVertices == 3) {
            // TODO: deal with the rare and weird case where opposite vertices are identical (i.e. the quad is folded in half)

            // a vertex is on the split plane
            var sameVertexMap = insideQuad.getSameVertexMap();
            if (onPlaneCount == 1) {
                int duplicateIndex = -1;
                boolean duplicateIsInside = false;

                // the duplicate vertex is inside or outside, find its index and side.
                // if the duplicate vertex is on the plane, don't move it.
                if ((onPlaneMapUnmasked & sameVertexMap) == 0) {
                    duplicateIsInside = (sameVertexMap & insideMapUnmasked) != 0;
                    duplicateIndex = Integer.numberOfTrailingZeros(sameVertexMap);
                }

                var insideIndex = Integer.numberOfTrailingZeros(insideMap);
                var outsideIndex = Integer.numberOfTrailingZeros(~(insideMap | onPlaneMap) & uniqueVertexMap);
                splitTriangleVertex(insideIndex, outsideIndex, duplicateIndex, duplicateIsInside, insideQuad, outsideQuad, splitPlane, splitDistance);
            }

            // even splitting if the two equal vertices are the corner that's being split off.
            // at this point onPlaneCount == 0
            else if (Integer.bitCount(insideMapUnmasked) == 2) {
                splitQuadEven(insideMapUnmasked, insideQuad, outsideQuad, splitPlane, splitDistance);
            }

            // a single vertex is inside or outside (with the other three, including one duplicate, being on the other side)
            else if (insideCount == 1) {
                var cornerIndex = Integer.numberOfTrailingZeros(insideMap);
                splitTriangleCorner(cornerIndex, insideQuad, outsideQuad, splitPlane, splitDistance);
            } else {
                var cornerIndex = Integer.numberOfTrailingZeros(~insideMapUnmasked);
                splitTriangleCorner(cornerIndex, outsideQuad, insideQuad, splitPlane, splitDistance);
            }
        } else { // uniqueVertices == 4, masked == unmasked
            // after dealing with the other cases, now two vertices being on the plane implies the quad is split exactly along its diagonal
            // insideCount is 2, onPlaneMap is 0b0101 or 0b1010
            if (onPlaneCount == 2) {
                // this case can be treated like even splitting if the two on-plane vertices are declared as each part of one of the sides
                if (onPlaneMap == 0b0101) {
                    insideMap |= 0b0001;
                } else {
                    insideMap |= 0b0010;
                }
                insideCount = 2;
            }

            // one vertex being on the plane now implies the quad is split on a vertex and through an edge.
            // if there is one vertex inside (and two outside), move the on-plane vertex inside to produce an even split case.
            // in the other case nothing needs to be done since for splitting the 0-bits in the insideMap are treated as outside.
            else if (onPlaneCount == 1 && insideCount == 1) {
                insideMap |= onPlaneMap;
                insideCount = 2;
            }

            // split evenly with two quads or three quads (corner chopped off) depending on the orientation
            if (insideCount == 2) {
                splitQuadEven(insideMap, insideQuad, outsideQuad, splitPlane, splitDistance);
            } else if (insideCount == 3) {
                var cornerIndex = Integer.numberOfTrailingZeros(~insideMap);
                secondInsideQuad = NativeFullTQuad.splittingCopy(insideQuad);

                splitQuadOdd(cornerIndex, outsideQuad, secondInsideQuad, insideQuad, splitPlane, splitDistance);
            } else { // insideCount == 1
                var cornerIndex = Integer.numberOfTrailingZeros(insideMap);
                secondOutsideQuad = NativeFullTQuad.splittingCopy(insideQuad);

                splitQuadOdd(cornerIndex, insideQuad, secondOutsideQuad, outsideQuad, splitPlane, splitDistance);
            }
        }

        addQuadIndex(inside, workspace.updateQuad(insideQuad, candidateIndex));
        addQuadIndex(outside, workspace.pushQuad(outsideQuad));
        addQuadIndex(inside, workspace.pushQuad(secondInsideQuad));
        addQuadIndex(outside, workspace.pushQuad(secondOutsideQuad));
    }

    static private void addQuadIndex(IntArrayList list, int index) {
        if (index >= 0) {
            list.add(index);
        }
    }

    static private void splitQuadEven(int vertexInsideMap, NativeFullTQuad insideQuad, NativeFullTQuad outsideQuad, Vector3fc splitPlane, float splitDistance) {
        // the quad is split through two of its opposing edges, producing two regular quads
        NativeFullTQuad.splitEven(vertexInsideMap, insideQuad, outsideQuad, splitPlane, splitDistance);
    }

    static private void splitQuadOdd(int cornerIndex, NativeFullTQuad cornerQuad, NativeFullTQuad cutQuad, NativeFullTQuad bulkQuad, Vector3fc splitPlane, float splitDistance) {
        // the quad is split through two of its adjacent edges, producing three quads (two triangles and one quad)
        NativeFullTQuad.splitOdd(cornerIndex, cornerQuad, cutQuad, bulkQuad, splitPlane, splitDistance);
    }

    static private void splitTriangleCorner(int cornerIndex, NativeFullTQuad cornerQuad, NativeFullTQuad bulkQuad, Vector3fc splitPlane, float splitDistance) {
        // the triangle (degenerate quad) is split through two edges, producing two quads (one triangle and one quad)
        NativeFullTQuad.splitTriangleCorner(cornerIndex, cornerQuad, bulkQuad, splitPlane, splitDistance);
    }

    static private void splitTriangleVertex(int insideIndex, int outsideIndex, int duplicateIndex, boolean duplicateIsInside, NativeFullTQuad insideQuad, NativeFullTQuad outsideQuad, Vector3fc splitPlane, float splitDistance) {
        // the triangle (degenerate quad) is split through one edge, producing two triangles
        NativeFullTQuad.splitTriangleVertex(insideIndex, outsideIndex, duplicateIndex, duplicateIsInside,
                insideQuad, outsideQuad, splitPlane, splitDistance);
    }

    static private BSPNode handleIntersecting(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode) {
        Int2IntOpenHashMap intersectionCounts = null;
        IntOpenHashSet primaryIntersectorIndexes = null;
        int primaryIntersectorThreshold = Mth.clamp(indexes.size() / 2, 2, 4);

        int i = -1;
        int j = 0;
        final int quadCount = indexes.size();
        int stepSize = Math.max(1, (quadCount * (quadCount - 1) / 2) / MAX_INTERSECTION_ATTEMPTS);
        int variance = 0;

        // if doing random stepping, subtract some and calculate the variance to apply
        Random random = null;
        if (stepSize > 1) {
            int half = stepSize / 2;
            stepSize = Math.max(1, stepSize - half);
            variance = stepSize;
            random = new Random();
        }

        while (true) {
            // pick indexes in serial fashion without repeating pairs (i < j always holds)
            i += stepSize;
            if (variance > 0) {
                i += random.nextInt(variance);
            }

            // step i and j until they're valid indexes with i < j
            while (i >= j) {
                i -= j;
                j++;
            }

            // stop if we're out of indexes
            if (j >= indexes.size()) {
                break;
            }

            var quadA = workspace.get(indexes.getInt(i));
            var quadB = workspace.get(indexes.getInt(j));

            // aligned quads intersect if their bounding boxes intersect
            if (TQuad.extentsIntersect(quadA, quadB)) {
                if (intersectionCounts == null) {
                    intersectionCounts = new Int2IntOpenHashMap();
                }

                int aCount = intersectionCounts.get(i) + 1;
                intersectionCounts.put(i, aCount);
                int bCount = intersectionCounts.get(j) + 1;
                intersectionCounts.put(j, bCount);

                if (aCount >= primaryIntersectorThreshold) {
                    if (primaryIntersectorIndexes == null) {
                        primaryIntersectorIndexes = new IntOpenHashSet(2);
                    }
                    primaryIntersectorIndexes.add(i);
                }
                if (bCount >= primaryIntersectorThreshold) {
                    if (primaryIntersectorIndexes == null) {
                        primaryIntersectorIndexes = new IntOpenHashSet(2);
                    }
                    primaryIntersectorIndexes.add(j);
                }

                // cancel primary intersector search if they all intersect with each other
                if (primaryIntersectorIndexes != null && primaryIntersectorIndexes.size() == indexes.size()) {
                    // return multi leaf node as this is impossible to sort
                    return BSPNode.nativeLeafMulti(BSPNode.copyIndexes(indexes));
                }
            }
        }

        if (primaryIntersectorIndexes != null) {
            // put the primary intersectors in a separate node that's always rendered last
            var nonPrimaryIntersectors = new IntArrayList(indexes.size() - primaryIntersectorIndexes.size());
            var primaryIntersectorQuadIndexes = new IntArrayList(primaryIntersectorIndexes.size());
            for (int k = 0; k < indexes.size(); k++) {
                if (primaryIntersectorIndexes.contains(k)) {
                    primaryIntersectorQuadIndexes.add(indexes.getInt(k));
                } else {
                    nonPrimaryIntersectors.add(indexes.getInt(k));
                }
            }
            return InnerFixedDoubleBSPNode.buildFromParts(workspace, indexes, depth, oldNode,
                    nonPrimaryIntersectors, primaryIntersectorQuadIndexes);
        }

        // this means we didn't manage to find primary intersectors
        return null;
    }

    static private BSPNode buildTopoMultiLeafNode(BSPWorkspace workspace, IntArrayList indexes, boolean failOnIntersection) {
        var quadCount = indexes.size();

        if (quadCount > TranslucentGeometryCollector.STATIC_TOPO_UNKNOWN_FALLBACK_LIMIT) {
            return null;
        }

        var quads = new TQuad[quadCount];
        var activeToRealIndex = new int[quadCount];
        for (int i = 0; i < indexes.size(); i++) {
            var quadIndex = indexes.getInt(i);
            quads[i] = workspace.get(quadIndex);
            activeToRealIndex[i] = quadIndex;
        }

        var sortedIndexes = NativeTranslucentGeometryAnalyzer.topoGraphSort(quads, quads.length, activeToRealIndex,
                failOnIntersection);
        if (sortedIndexes == null) {
            return null;
        }

        // no need to add the geometry to the workspace's trigger registry
        // since it's being sorted statically and the sort order won't change based on the camera position

        return BSPNode.nativeLeafMulti(BSPNode.copyIndexes(sortedIndexes, false));
    }

    static private BSPNode buildSNRLeafNodeFromQuads(BSPWorkspace workspace, IntArrayList indexes) {
        final var indexBuffer = indexes.elements();
        final var indexCount = indexes.size();

        final var keys = new int[indexCount];
        final var perm = new int[indexCount];

        for (int i = 0; i < indexCount; i++) {
            TQuad quad = workspace.get(indexBuffer[i]);
            keys[i] = MathUtil.floatToComparableInt(quad.getAccurateDotProduct());
            perm[i] = i;
        }

        RadixSort.sortIndirect(perm, keys, false);

        for (int i = 0; i < indexCount; i++) {
            perm[i] = indexBuffer[perm[i]];
        }

        return BSPNode.nativeLeafMulti(BSPNode.copyIndexes(IntArrayList.wrap(perm), false));
    }

    static private BSPNode buildSNRLeafNodeFromPoints(BSPWorkspace workspace, LongArrayList points) {
        // also sort by ascending encoded point but then process as an SNR result
        Arrays.sort(points.elements(), 0, points.size());

        // since the quads are aligned and are all INTERVAL_SIDE, there's no issues with duplicates.
        // the length of the array is exactly how many quads there are.
        int[] quadIndexes = new int[points.size()];
        int forwards = 0;
        int backwards = quadIndexes.length - 1;
        for (int i = 0; i < points.size(); i++) {
            // based one each quad's facing, order them forwards or backwards,
            // this means forwards is written from the start and backwards is written from the end
            var quadIndex = decodeQuadIndex(points.getLong(i));
            if (workspace.get(quadIndex).getFacing().getSign() == 1) {
                quadIndexes[forwards++] = quadIndex;
            } else {
                quadIndexes[backwards--] = quadIndex;
            }
        }

        return BSPNode.nativeLeafMulti(BSPNode.copyIndexes(IntArrayList.wrap(quadIndexes), false));
    }
}
