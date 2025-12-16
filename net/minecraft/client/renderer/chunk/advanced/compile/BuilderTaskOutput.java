package net.minecraft.client.renderer.chunk.advanced.compile;

import net.minecraft.client.renderer.chunk.advanced.RenderSection;
import net.minecraft.client.renderer.chunk.advanced.compile.estimation.MeshResultSize;

public abstract class BuilderTaskOutput {
    public final RenderSection render;
    public final int submitTime;
    private long resultSize = MeshResultSize.NO_DATA;

    public BuilderTaskOutput(RenderSection render, int buildTime) {
        this.render = render;
        this.submitTime = buildTime;
    }

    public void destroy() {
    }

    protected abstract long calculateResultSize();

    public long getResultSize() {
        if (this.resultSize == MeshResultSize.NO_DATA) {
            this.resultSize = this.calculateResultSize();
        }
        return this.resultSize;
    }
}
