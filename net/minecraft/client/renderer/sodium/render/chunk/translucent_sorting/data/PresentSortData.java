package net.minecraft.client.renderer.sodium.render.chunk.translucent_sorting.data;

import net.minecraft.client.renderer.sodium.util.NativeBuffer;

import java.nio.IntBuffer;

public interface PresentSortData {
    NativeBuffer getIndexBuffer();

    default IntBuffer getIntBuffer() {
        return this.getIndexBuffer().getDirectBuffer().asIntBuffer();
    }
}
