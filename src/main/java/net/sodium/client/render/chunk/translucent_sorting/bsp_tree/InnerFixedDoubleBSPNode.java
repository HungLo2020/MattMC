package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import it.unimi.dsi.fastutil.ints.IntArrayList;

public class InnerFixedDoubleBSPNode extends InnerPartitionBSPNode {
    private final BSPNode first;
    private final BSPNode second;

    InnerFixedDoubleBSPNode(NodeReuseData reuseData, BSPNode first, BSPNode second) {
        super(reuseData, 0);
        this.first = first;
        this.second = second;
    }

    @Override
    int addTo(NativeBspTree.Builder builder) {
        return builder.addFixedDouble(this.nativeRemap(), this.first.addTo(builder), this.second.addTo(builder));
    }

    @Override
    void addPartitionPlanes(BSPWorkspace workspace) {
        // no-op
    }

    static BSPNode buildFromParts(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode, IntArrayList first, IntArrayList second) {
        BSPNode firstOldNode = null;
        BSPNode secondOldNode = null;
        if (oldNode instanceof InnerFixedDoubleBSPNode old) {
            firstOldNode = old.first;
            secondOldNode = old.second;
        }

        var firstNode = BSPNode.build(workspace, first, depth, firstOldNode);
        var secondNode = BSPNode.build(workspace, second, depth, secondOldNode);

        return new InnerFixedDoubleBSPNode(
                prepareNodeReuse(workspace, indexes, depth),
                firstNode, secondNode);
    }
}
