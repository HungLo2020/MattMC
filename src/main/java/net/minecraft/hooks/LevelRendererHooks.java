package net.minecraft.hooks;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Hook interface for LevelRenderer rendering events.
 * Allows mods to inject custom logic during world rendering.
 */
public interface LevelRendererHooks {
    /**
     * Called at the beginning of renderLevel before any rendering occurs.
     * This allows mods to set up rendering state or perform custom rendering.
     *
     * @param camera The camera being used for rendering
     * @param positionMatrix The model-view matrix
     * @param projectionMatrix The projection matrix
     */
    default void onBeforeRenderLevel(Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
    }

    /**
     * Called at the beginning of prepareChunkRenders before chunk culling occurs.
     * This allows mods to set up rendering state or perform custom LOD rendering.
     *
     * @param modelViewMatrix The model-view matrix
     * @param camX Camera X position
     * @param camY Camera Y position
     * @param camZ Camera Z position
     */
    default void onBeforePrepareChunkRenders(Matrix4fc modelViewMatrix, double camX, double camY, double camZ) {
    }
}
