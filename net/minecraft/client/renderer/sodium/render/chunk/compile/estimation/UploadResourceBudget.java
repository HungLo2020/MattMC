package net.minecraft.client.renderer.sodium.render.chunk.compile.estimation;

public interface UploadResourceBudget {
    boolean isAvailable();

    void consume(long duration, long size);
}
