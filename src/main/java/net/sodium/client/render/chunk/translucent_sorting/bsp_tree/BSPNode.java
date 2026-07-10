package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.ints.IntArrayList;
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

    private enum NativeLeafKind {
        NONE,
        SINGLE,
        DOUBLE,
        MULTI
    }

    private final NativeLeafKind nativeLeafKind;
    private final int quadA;
    private final int quadB;
    private final int[] quads;

    BSPNode() {
        this.nativeLeafKind = NativeLeafKind.NONE;
        this.quadA = NO_QUAD;
        this.quadB = NO_QUAD;
        this.quads = null;
    }

    private BSPNode(NativeLeafKind nativeLeafKind, int quadA, int quadB, int[] quads) {
        this.nativeLeafKind = nativeLeafKind;
        this.quadA = quadA;
        this.quadB = quadB;
        this.quads = quads;
    }

    static BSPNode nativeLeafSingle(int quad) {
        return new BSPNode(NativeLeafKind.SINGLE, quad, NO_QUAD, null);
    }

    static BSPNode nativeLeafDouble(int quadA, int quadB) {
        return new BSPNode(NativeLeafKind.DOUBLE, quadA, quadB, null);
    }

    static BSPNode nativeLeafMulti(int[] quads) {
        return new BSPNode(NativeLeafKind.MULTI, NO_QUAD, NO_QUAD, quads);
    }

    int addTo(NativeBspTree.Builder builder) {
        return switch (this.nativeLeafKind) {
            case SINGLE -> builder.addLeafSingle(this.quadA);
            case DOUBLE -> builder.addLeafDouble(this.quadA, this.quadB);
            case MULTI -> builder.addLeafMulti(this.quads);
            case NONE -> throw new IllegalStateException("BSP node does not implement native export");
        };
    }

    public static BSPResult buildBSP(TQuad[] quads, SectionPos sectionPos, BSPNode oldRoot,
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
            var result = workspace.result;
            result.setRootNode(rootNode);
            result.setUpdatedQuadIndexes(workspace.getFinalizedUpdatedQuads());
            return result;
        }
    }

    static BSPNode build(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode) {
        depth++;

        // pick which type of node to create for the given workspace
        if (indexes.isEmpty()) {
            return null;
        } else if (indexes.size() == 1) {
            return nativeLeafSingle(indexes.getInt(0));
        } else if (indexes.size() == 2) {
            var quadIndexA = indexes.getInt(0);
            var quadIndexB = indexes.getInt(1);
            if (workspace.doubleLeafPossible(quadIndexA, quadIndexB, workspace.canSplitQuads())) {
                return nativeLeafDouble(quadIndexA, quadIndexB);
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
