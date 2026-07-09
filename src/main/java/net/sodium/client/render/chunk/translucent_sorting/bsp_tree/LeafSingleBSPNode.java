package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

/**
 * A leaf node of a BSP tree that contains a single quad.
 */
class LeafSingleBSPNode extends BSPNode {
    private final int quad;

    LeafSingleBSPNode(int quad) {
        this.quad = quad;
    }

    @Override
    int addTo(NativeBspTree.Builder builder) {
        return builder.addLeafSingle(this.quad);
    }
}
