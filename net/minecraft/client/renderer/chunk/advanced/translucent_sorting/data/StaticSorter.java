package net.minecraft.client.renderer.chunk.advanced.translucent_sorting.data;

class StaticSorter extends PresentSorter {
    StaticSorter(int quadCount) {
        this.initBufferWithQuadLength(quadCount);
    }

    @Override
    public void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial) {
        // no-op
    }
}
