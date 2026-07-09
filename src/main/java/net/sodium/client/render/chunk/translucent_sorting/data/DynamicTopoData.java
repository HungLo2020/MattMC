package net.sodium.client.render.chunk.translucent_sorting.data;

import net.sodium.client.render.chunk.translucent_sorting.NativeTranslucentSectionGeometry;
import net.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;

import java.util.Objects;

/**
 * Performs dynamic topo sorting and falls back to distance sorting as
 * necessary. This class implements a number of heuristics to attempt to upgrade
 * distance-based sorting back to topo sorting when possible as topo sorting
 * generally needs to happen far less often.
 * <p>
 * Triggering is performed when the quads' planes crossed along their normal
 * direction (unidirectional).
 * <p>
 * Implementation note:
 * - Reusing the output of previous distance sorting job doesn't make a
 * difference or makes things slower in some cases. It's unclear why exactly
 * this happens, I suspect weird memory behavior or the reuse is not actually
 * that helpful to the sorting algorithm.
 */
public class DynamicTopoData extends DynamicData {
    private static final int MAX_TOPO_SORT_QUADS = 1000;

    private boolean GFNITrigger = true;
    private boolean directTrigger = false;
    private int consecutiveTopoSortFailures = 0;

    private double directTriggerKey = -1;
    private boolean pendingTriggerIsDirect;

    private final NativeTranslucentSectionGeometry nativeGeometry;

    private DynamicTopoData(SectionPos sectionPos, int quadCount,
                            GeometryPlanes geometryPlanes, Vector3dc initialCameraPos,
                            NativeTranslucentSectionGeometry nativeGeometry) {
        super(sectionPos, quadCount, geometryPlanes, initialCameraPos);
        this.nativeGeometry = Objects.requireNonNull(nativeGeometry, "nativeGeometry");

        if (this.getInputQuadCount() > MAX_TOPO_SORT_QUADS) {
            this.directTrigger = true;
            this.GFNITrigger = false;
        }
    }

    @Override
    public DynamicSorter getSorter() {
        return new DynamicTopoSorter(this.getInputQuadCount(), this, this.pendingTriggerIsDirect, this.consecutiveTopoSortFailures, this.GFNITrigger, this.directTrigger);
    }

    @Override
    public void close() {
        if (this.nativeGeometry != null) {
            this.nativeGeometry.close();
        }
    }

    public boolean GFNITriggerEnabled() {
        return this.GFNITrigger;
    }

    public boolean directTriggerEnabled() {
        return this.directTrigger;
    }

    public double getDirectTriggerKey() {
        return this.directTriggerKey;
    }

    public void setDirectTriggerKey(double key) {
        this.directTriggerKey = key;
    }

    public boolean isMatchingSorter(DynamicTopoSorter sorter) {
        return sorter.parent == this;
    }

    public boolean checkAndApplyGFNITriggerOff(DynamicTopoSorter sorter) {
        if (this.GFNITrigger && !sorter.GFNITrigger) {
            this.GFNITrigger = false;
            return true;
        }
        return false;
    }

    public boolean checkAndApplyDirectTriggerOff(DynamicTopoSorter sorter) {
        if (this.directTrigger && !sorter.directTrigger) {
            this.directTrigger = false;
            return true;
        }
        return false;
    }

    public boolean checkAndApplyDirectTriggerOn(DynamicTopoSorter sorter) {
        if (!this.directTrigger && sorter.directTrigger) {
            this.directTrigger = true;
            return true;
        }
        return false;
    }

    public void applyTopoSortFailureCounterChange(DynamicTopoSorter sorter) {
        if (sorter.hasSortFailureReset()) {
            this.consecutiveTopoSortFailures = 0;
        } else if (sorter.hasSortFailureIncrement()) {
            this.consecutiveTopoSortFailures++;
        }
    }

    private void copyStateFrom(DynamicTopoSorter sorter) {
        this.GFNITrigger = sorter.GFNITrigger;
        this.directTrigger = sorter.directTrigger;
        this.consecutiveTopoSortFailures = sorter.consecutiveTopoSortFailuresNew;
    }

    @Override
    public void prepareTrigger(boolean isDirectTrigger) {
        this.pendingTriggerIsDirect = isDirectTrigger;
    }

    public class DynamicTopoSorter extends DynamicSorter {
        private final DynamicTopoData parent;
        private final boolean isDirectTrigger;
        private final int consecutiveTopoSortFailures;

        private boolean directTrigger;
        private boolean GFNITrigger;
        private int consecutiveTopoSortFailuresNew;

        private DynamicTopoSorter(int quadCount, DynamicTopoData parent, boolean isDirectTrigger, int consecutiveTopoSortFailures, boolean GFNITrigger, boolean directTrigger) {
            super(quadCount);
            this.parent = parent;
            this.isDirectTrigger = isDirectTrigger;
            this.consecutiveTopoSortFailures = consecutiveTopoSortFailures;
            this.consecutiveTopoSortFailuresNew = consecutiveTopoSortFailures;
            this.GFNITrigger = GFNITrigger;
            this.directTrigger = directTrigger;
        }

        private boolean hasSortFailureReset() {
            return this.consecutiveTopoSortFailuresNew < this.consecutiveTopoSortFailures;
        }

        private boolean hasSortFailureIncrement() {
            return this.consecutiveTopoSortFailuresNew > this.consecutiveTopoSortFailures;
        }

        @Override
        void writeSort(CombinedCameraPos cameraPos, boolean initial) {
            var result = DynamicTopoData.this.nativeGeometry.writeDynamicSortedIndexBuffer(this.getIndexBuffer(),
                    cameraPos.getRelativeCameraPos(), initial, this.isDirectTrigger, this.GFNITrigger,
                    this.directTrigger, this.consecutiveTopoSortFailuresNew);
            this.GFNITrigger = result.gfniTrigger();
            this.directTrigger = result.directTrigger();
            this.consecutiveTopoSortFailuresNew = result.consecutiveTopoSortFailures();

            if (initial) {
                DynamicTopoData.this.copyStateFrom(this);
            }
        }
    }

    public static DynamicTopoData fromMesh(CombinedCameraPos cameraPos, TQuad[] quads, SectionPos sectionPos,
            GeometryPlanes geometryPlanes, NativeTranslucentSectionGeometry nativeGeometry) {
        geometryPlanes.prepareIntegration();

        try {
            return new DynamicTopoData(sectionPos, quads.length, geometryPlanes, cameraPos.getAbsoluteCameraPos(),
                    nativeGeometry);
        } catch (RuntimeException exception) {
            if (nativeGeometry != null) {
                nativeGeometry.close();
            }
            throw exception;
        }
    }
}
