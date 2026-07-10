package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import net.sodium.client.render.chunk.translucent_sorting.trigger.NativeGfniTriggers;
import org.joml.Vector3fc;

/**
 * The result of a BSP building operation. Building a BSP returns the root node
 * along with the partition planes that need to be added to the trigger system.
 */
public class BSPResult implements AutoCloseable {
    private long geometryPlanesHandle = NativeGfniTriggers.createGeometryPlanes();
    private BSPNode rootNode;
    private UpdatedQuadsList updatedQuadsList;

    public BSPNode getRootNode() {
        return this.rootNode;
    }

    public void setRootNode(BSPNode rootNode) {
        this.rootNode = rootNode;
    }

    public long takeGeometryPlanesHandle() {
        long geometryPlanesHandle = this.geometryPlanesHandle;
        if (geometryPlanesHandle == 0) {
            throw new IllegalStateException("BSP geometry planes have already been transferred");
        }
        this.geometryPlanesHandle = 0;
        return geometryPlanesHandle;
    }

    void addDoubleSidedAlignedPlane(int axis, float distance) {
        NativeGfniTriggers.addDoubleSidedAlignedGeometryPlane(this.geometryPlanesHandle, axis, distance);
    }

    void addDoubleSidedUnalignedPlane(Vector3fc normal, float distance) {
        NativeGfniTriggers.addDoubleSidedUnalignedGeometryPlane(this.geometryPlanesHandle,
                normal.x(), normal.y(), normal.z(), distance);
    }

    public UpdatedQuadsList getUpdatedQuadsList() {
        return this.updatedQuadsList;
    }

    public void setUpdatedQuadIndexes(UpdatedQuadsList updatedQuadsList) {
        this.updatedQuadsList = updatedQuadsList;
    }

    @Override
    public void close() {
        if (this.geometryPlanesHandle != 0) {
            NativeGfniTriggers.destroyGeometryPlanes(this.geometryPlanesHandle);
            this.geometryPlanesHandle = 0;
        }
    }
}
