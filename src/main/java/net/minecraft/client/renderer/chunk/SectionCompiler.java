package net.minecraft.client.renderer.chunk;

import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.blaze3d.vertex.MeshData;

/**
 * Sodium: Stub class - functionality moved to Sodium's chunk rendering system.
 * Kept for compilation compatibility with SectionRenderDispatcher.RebuildTask and CompiledSectionMesh.
 * This class is never instantiated since compileSections() is stubbed.
 */
@Environment(EnvType.CLIENT)
class SectionCompiler {
@Environment(EnvType.CLIENT)
public static class Results {
public VisibilitySet visibilitySet = new VisibilitySet();
public List<BlockEntity> blockEntities = List.of();
public MeshData.SortState transparencyState = null;

public void release() {
// Stub - no-op
}

public java.util.Map<ChunkSectionLayer, MeshData> renderedLayers = java.util.Map.of();
}
}
