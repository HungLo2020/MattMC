package net.minecraft.client.renderer.chunk;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.MeshData;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CompiledSectionMesh implements SectionMesh {
	public static final SectionMesh UNCOMPILED = new SectionMesh() {
		@Override
		public boolean facesCanSeeEachother(Direction direction, Direction direction2) {
			return false;
		}
	};
	public static final SectionMesh EMPTY = new SectionMesh() {
		@Override
		public boolean facesCanSeeEachother(Direction direction, Direction direction2) {
			return true;
		}
	};
	private final List<BlockEntity> renderableBlockEntities;
	private final VisibilitySet visibilitySet;
	@Nullable
	private final MeshData.SortState transparencyState;
	@Nullable
	private TranslucencyPointOfView translucencyPointOfView;
	private final Map<ChunkSectionLayer, SectionBuffers> buffers = new EnumMap(ChunkSectionLayer.class);

	public CompiledSectionMesh(TranslucencyPointOfView translucencyPointOfView, SectionCompiler.Results results) {
		this.translucencyPointOfView = translucencyPointOfView;
		this.visibilitySet = results.visibilitySet;
		this.renderableBlockEntities = results.blockEntities;
		this.transparencyState = results.transparencyState;
	}

	public void setTranslucencyPointOfView(TranslucencyPointOfView translucencyPointOfView) {
		this.translucencyPointOfView = translucencyPointOfView;
	}

	@Override
	public boolean isDifferentPointOfView(TranslucencyPointOfView translucencyPointOfView) {
		return !translucencyPointOfView.equals(this.translucencyPointOfView);
	}

	@Override
	public boolean hasRenderableLayers() {
		return !this.buffers.isEmpty();
	}

	@Override
	public boolean isEmpty(ChunkSectionLayer chunkSectionLayer) {
		return !this.buffers.containsKey(chunkSectionLayer);
	}

	@Override
	public List<BlockEntity> getRenderableBlockEntities() {
		return this.renderableBlockEntities;
	}

	@Override
	public boolean facesCanSeeEachother(Direction direction, Direction direction2) {
		return this.visibilitySet.visibilityBetween(direction, direction2);
	}

	@Nullable
	@Override
	public SectionBuffers getBuffers(ChunkSectionLayer chunkSectionLayer) {
		return (SectionBuffers)this.buffers.get(chunkSectionLayer);
	}

	@Override
	public boolean hasTranslucentGeometry() {
		return this.buffers.containsKey(ChunkSectionLayer.TRANSLUCENT);
	}

	@Nullable
	public MeshData.SortState getTransparencyState() {
		return this.transparencyState;
	}

	@Override
	public void close() {
		this.buffers.values().forEach(SectionBuffers::close);
		this.buffers.clear();
	}
}
