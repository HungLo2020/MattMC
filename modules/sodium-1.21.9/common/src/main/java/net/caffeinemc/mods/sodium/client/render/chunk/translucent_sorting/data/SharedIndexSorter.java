package net.minecraft.client.renderer.chunk.advanced.translucent_sorting.data;

import net.minecraft.client.renderer.sodium.util.NativeBuffer;

import java.nio.IntBuffer;

public record SharedIndexSorter(int quadCount) implements Sorter {
    @Override
    public NativeBuffer getIndexBuffer() {
        return null;
    }

    @Override
    public IntBuffer getIntBuffer() {
        return null;
    }

    @Override
    public void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }
}
