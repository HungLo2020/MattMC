package net.minecraft.client.renderer.sodium.render.chunk.lists;

import net.minecraft.client.renderer.chunk.advanced.RenderSection;

public interface RenderSectionVisitor {
    void visit(RenderSection section);
}
