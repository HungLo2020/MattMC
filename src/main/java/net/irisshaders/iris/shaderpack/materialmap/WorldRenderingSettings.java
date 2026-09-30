package net.irisshaders.iris.shaderpack.materialmap;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class WorldRenderingSettings {
	public static final WorldRenderingSettings INSTANCE = new WorldRenderingSettings();

	private boolean reloadRequired;
	private Object2IntMap<BlockState> blockStateIds;
	private Map<Block, BlockRenderType> blockTypeIds;
	private Object2IntFunction<NamespacedId> entityIds;
	private Object2IntFunction<NamespacedId> itemIds;
	private float ambientOcclusionLevel;
	private boolean disableDirectionalShading;
	private boolean hasVillagerConversionId;
	private boolean useSeparateAo;
	private boolean separateEntityDraws;
	private boolean voxelizeLightBlocks;
	private ChunkVertexType chunkVertexFormat;

	public WorldRenderingSettings() {
		reloadRequired = false;
		blockStateIds = null;
		blockTypeIds = null;
		ambientOcclusionLevel = 1.0F;
		disableDirectionalShading = false;
		useSeparateAo = false;
		chunkVertexFormat = ChunkMeshFormats.COMPACT;
		separateEntityDraws = false;
		voxelizeLightBlocks = false;
		hasVillagerConversionId = false;
	}

	@Nullable
	public Object2IntMap<BlockState> getBlockStateIds() {
		return blockStateIds;
	}

	@Nullable
	public Object2IntFunction<NamespacedId> getEntityIds() {
		return entityIds;
	}

	@Nullable
	public Object2IntFunction<NamespacedId> getItemIds() {
		return itemIds;
	}

	public float getAmbientOcclusionLevel() {
		return ambientOcclusionLevel;
	}

	public boolean shouldUseSeparateAo() {
		return useSeparateAo;
	}

	public ChunkVertexType getVertexFormat() {
		return chunkVertexFormat;
	}

	public boolean hasVillagerConversionId() {
		return hasVillagerConversionId;
	}
}
