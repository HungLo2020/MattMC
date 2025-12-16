package net.minecraft.client.renderer.sodium.render.chunk.compile;

import net.minecraft.client.renderer.chunk.advanced.RenderSection;
import net.minecraft.client.renderer.chunk.advanced.data.BuiltSectionInfo;
import net.minecraft.client.renderer.chunk.advanced.data.BuiltSectionMeshParts;
import net.minecraft.client.renderer.chunk.advanced.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.chunk.advanced.translucent_sorting.data.TranslucentData;

import java.util.Map;

/**
 * The result of a chunk rebuild task which contains any and all data that needs
 * to be processed or uploaded on the main thread. If a task is cancelled after
 * finishing its work and not before the result is processed, the result will
 * instead be discarded.
 */
public class ChunkBuildOutput extends ChunkSortOutput {
    public final BuiltSectionInfo info;
    public final TranslucentData translucentData;
    public final Map<TerrainRenderPass, BuiltSectionMeshParts> meshes;

    public ChunkBuildOutput(RenderSection render, int buildTime, TranslucentData translucentData, BuiltSectionInfo info,
            Map<TerrainRenderPass, BuiltSectionMeshParts> meshes) {
        super(render, buildTime);

        this.info = info;
        this.translucentData = translucentData;
        this.meshes = meshes;
    }

    public BuiltSectionMeshParts getMesh(TerrainRenderPass pass) {
        return this.meshes.get(pass);
    }

    @Override
    public void destroy() {
        super.destroy();

        for (BuiltSectionMeshParts data : this.meshes.values()) {
            data.getVertexData().free();
        }
    }

    private long getMeshSize() {
        long size = 0;
        for (var data : this.meshes.values()) {
            size += data.getVertexData().getLength();
        }
        return size;
    }

    @Override
    public long calculateResultSize() {
        return super.calculateResultSize() + this.getMeshSize();
    }
}
