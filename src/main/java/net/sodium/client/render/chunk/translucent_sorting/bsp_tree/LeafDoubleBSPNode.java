package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

/**
 * A leaf node of a BSP tree that contains two quads.
 */
public class LeafDoubleBSPNode extends BSPNode {
    private final int quadA;
    private final int quadB;

    LeafDoubleBSPNode(int quadA, int quadB) {
        this.quadA = quadA;
        this.quadB = quadB;
    }

    @Override
    int addTo(NativeBspTree.Builder builder) {
        return builder.addLeafDouble(this.quadA, this.quadB);
    }
}
