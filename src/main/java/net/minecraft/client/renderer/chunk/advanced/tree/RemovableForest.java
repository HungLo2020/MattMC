package net.minecraft.client.renderer.chunk.advanced.tree;

public interface RemovableForest extends TraversableForest {
    void remove(int x, int y, int z);
}
