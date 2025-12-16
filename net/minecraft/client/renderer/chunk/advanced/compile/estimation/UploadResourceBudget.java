package net.minecraft.client.renderer.chunk.advanced.compile.estimation;

public interface UploadResourceBudget {
    boolean isAvailable();

    void consume(long duration, long size);
}
