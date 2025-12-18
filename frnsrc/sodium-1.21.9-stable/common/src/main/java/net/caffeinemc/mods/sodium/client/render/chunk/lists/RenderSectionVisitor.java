package frnsrc.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public interface RenderSectionVisitor {
    void visit(RenderSection section);
}
