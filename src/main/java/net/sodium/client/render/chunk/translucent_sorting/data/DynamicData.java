package net.sodium.client.render.chunk.translucent_sorting.data;

import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.trigger.NativeGfniTriggers;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;

public abstract class DynamicData extends PresentTranslucentData {
    private long geometryPlanesHandle;
    private final Vector3dc initialCameraPos;

    DynamicData(SectionPos sectionPos, int inputQuadCount, long geometryPlanesHandle, Vector3dc initialCameraPos) {
        super(sectionPos, inputQuadCount);
        if (geometryPlanesHandle == 0) {
            throw new IllegalArgumentException("Native geometry plane collector handle must not be null");
        }
        this.geometryPlanesHandle = geometryPlanesHandle;
        this.initialCameraPos = initialCameraPos;
    }

    @Override
    public SortType getSortType() {
        return SortType.DYNAMIC;
    }

    public abstract DynamicSorter getSorter();

    public long getGeometryPlanesHandle() {
        if (this.geometryPlanesHandle == 0) {
            throw new IllegalStateException("Native geometry plane collector has already been discarded");
        }
        return this.geometryPlanesHandle;
    }

    public void discardGeometryPlanes() {
        if (this.geometryPlanesHandle != 0) {
            NativeGfniTriggers.destroyGeometryPlanes(this.geometryPlanesHandle);
            this.geometryPlanesHandle = 0;
        }
    }

    public Vector3dc getInitialCameraPos() {
        return this.initialCameraPos;
    }

    @Override
    public void close() {
        this.discardGeometryPlanes();
    }
}
