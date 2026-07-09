package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

/**
 * A leaf node of a BSP tree that contains a set of quads.
 */
class LeafMultiBSPNode extends BSPNode {
    private final int[] quads;

    LeafMultiBSPNode(int[] quads) {
        this.quads = quads;
    }

    @Override
    int addTo(NativeBspTree.Builder builder) {
        return builder.addLeafMulti(this.quads);
    }
}
