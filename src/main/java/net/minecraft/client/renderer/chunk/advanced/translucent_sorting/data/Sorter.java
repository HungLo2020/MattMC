package net.minecraft.client.renderer.chunk.advanced.translucent_sorting.data;

public interface Sorter extends PresentSortData {
    void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial);

    void destroy();
}
