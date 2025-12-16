package net.minecraft.client.renderer.chunk.advanced.lists;

import net.minecraft.client.renderer.chunk.advanced.RenderSection;

public interface RenderSectionVisitor {
    void visit(RenderSection section);
}
