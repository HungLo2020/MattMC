package net.minecraft.client.renderer.chunk.advanced.lists;

public interface CoordinateSectionVisitor {
    void visit(int x, int y, int z);
}
