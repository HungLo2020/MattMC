package net.minecraft.client.renderer.sodium.render.chunk.lists;

public interface CoordinateSectionVisitor {
    void visit(int x, int y, int z);
}
