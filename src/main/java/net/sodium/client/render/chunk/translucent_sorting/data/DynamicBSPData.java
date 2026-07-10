package net.sodium.client.render.chunk.translucent_sorting.data;

import net.sodium.client.render.chunk.translucent_sorting.QuadSplittingMode;
import net.sodium.client.render.chunk.translucent_sorting.SortType;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.NativeBspBuildResult;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.NativeUpdatedQuads;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPNode;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;

/**
 * Constructs a BSP tree of the quads and sorts them dynamically.
 * <p>
 * Triggering is performed when the BSP tree's partition planes are crossed in
 * any direction (bidirectional).
 */
public class DynamicBSPData extends DynamicData {
    private static final int NODE_REUSE_MIN_GENERATION = 1;

    private final int indexQuadCount;
    private final BSPNode rootNode;
    private final NativeBspBuildResult buildResult;
    private final int generation;

    private DynamicBSPData(SectionPos sectionPos, int inputQuadCount, NativeBspBuildResult result,
            Vector3dc initialCameraPos, int generation, long geometryPlanesHandle) {
        super(sectionPos, inputQuadCount, geometryPlanesHandle, initialCameraPos);
        this.rootNode = result.rootNode();
        this.generation = generation;
        this.buildResult = result;
        this.indexQuadCount = result.indexQuadCount();
    }

    private class DynamicBSPSorter extends DynamicSorter {
        private DynamicBSPSorter(int quadCount) {
            super(quadCount);
        }

        @Override
        void writeSort(CombinedCameraPos cameraPos, boolean initial) {
            DynamicBSPData.this.buildResult.writeIndexBuffer(this.getIndexBuffer(), cameraPos.getRelativeCameraPos());
        }
    }

    @Override
    public void close() {
        super.close();
        this.buildResult.close();
    }

    @Override
    public boolean oldDataMatches(TranslucentGeometryCollector collector, SortType sortType, TQuad[] quads) {
        // don't reuse data if we need to rewrite the mesh because of quad splitting
        return !this.meshesWereModified() && super.oldDataMatches(collector, sortType, quads);
    }

    @Override
    public int getIndexQuadCount() {
        return this.indexQuadCount;
    }

    @Override
    public DynamicSorter getSorter() {
        return new DynamicBSPSorter(this.getIndexQuadCount()); // index quad count
    }

    @Override
    public NativeUpdatedQuads getUpdatedQuads() {
        return this.buildResult.updatedQuads();
    }

    public static DynamicBSPData fromMesh(CombinedCameraPos cameraPos, TQuad[] quads, SectionPos sectionPos,
                                          TranslucentData oldData, QuadSplittingMode quadSplittingMode) {
        BSPNode oldRoot = null;
        int generation = 0;
        boolean prepareNodeReuse = false;
        if (oldData instanceof DynamicBSPData oldBSPData) {
            generation = oldBSPData.generation + 1;
            oldRoot = oldBSPData.rootNode;

            // only enable partial updates after a certain number of generations
            // (times the section has been built)
            prepareNodeReuse = generation >= NODE_REUSE_MIN_GENERATION;
        }
        NativeBspBuildResult result = BSPNode.buildBSP(quads, sectionPos, oldRoot, prepareNodeReuse,
                quadSplittingMode);
        try {
            long geometryPlanesHandle = result.takeGeometryPlanesHandle();
            return new DynamicBSPData(sectionPos, quads.length, result, cameraPos.getAbsoluteCameraPos(),
                    generation, geometryPlanesHandle);
        } catch (RuntimeException exception) {
            result.close();
            throw exception;
        }
    }
}
