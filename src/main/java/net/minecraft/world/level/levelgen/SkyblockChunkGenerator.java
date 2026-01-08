package net.minecraft.world.level.levelgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.util.valueproviders.ConstantInt;

public class SkyblockChunkGenerator extends ChunkGenerator {
	public static final MapCodec<SkyblockChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(skyblockChunkGenerator -> skyblockChunkGenerator.biomeSource))
			.apply(instance, instance.stable(SkyblockChunkGenerator::new))
	);
	protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final int PLATFORM_Y = 64;
	private static final int PLATFORM_SIZE = 3;
	
	public SkyblockChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureManager, RandomState randomState, ChunkAccess chunkAccess) {
	}

	@Override
	public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureManager structureManager) {
		ChunkPos chunkPos = chunkAccess.getPos();
		int chunkX = chunkPos.x;
		int chunkZ = chunkPos.z;
		
		// Only place tree if this chunk contains the center position (0, 0)
		if (chunkX == 0 && chunkZ == 0) {
			BlockPos treePos = new BlockPos(0, PLATFORM_Y + 3, 0); // On top of the platform
			
			// Create a simple oak tree configuration
			TreeConfiguration treeConfig = new TreeConfiguration.TreeConfigurationBuilder(
				BlockStateProvider.simple(Blocks.OAK_LOG),
				new StraightTrunkPlacer(4, 2, 0),
				BlockStateProvider.simple(Blocks.OAK_LEAVES),
				new BlobFoliagePlacer(ConstantInt.of(2), ConstantInt.of(0), 3),
				new TwoLayersFeatureSize(1, 0, 1)
			).ignoreVines().build();
			
			// Place the tree - need to pass this (ChunkGenerator) instead of structureManager
			Feature.TREE.place(treeConfig, worldGenLevel, this, RandomSource.create(), treePos);
		}
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunkAccess) {
		ChunkPos chunkPos = chunkAccess.getPos();
		int chunkX = chunkPos.x;
		int chunkZ = chunkPos.z;
		
		// Only generate platform if this chunk contains the center position (0, 0)
		if (chunkX == 0 && chunkZ == 0) {
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
			
			// Place 3x3 platform centered at (0, 0)
			// Bottom 2 layers: dirt at Y=64 and Y=65
			// Top layer: grass at Y=66
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					// 2 layers of dirt
					chunkAccess.setBlockState(mutableBlockPos.set(x, PLATFORM_Y, z), Blocks.DIRT.defaultBlockState());
					chunkAccess.setBlockState(mutableBlockPos.set(x, PLATFORM_Y + 1, z), Blocks.DIRT.defaultBlockState());
					// Top layer of grass
					chunkAccess.setBlockState(mutableBlockPos.set(x, PLATFORM_Y + 2, z), Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
		}
		
		return CompletableFuture.completedFuture(chunkAccess);
	}

	@Override
	public int getBaseHeight(int i, int j, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
		return levelHeightAccessor.getMinY();
	}

	@Override
	public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
		return new NoiseColumn(levelHeightAccessor.getMinY(), new BlockState[0]);
	}

	@Override
	public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
	}

	@Override
	public void applyCarvers(
		WorldGenRegion worldGenRegion, long l, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess
	) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
	}

	@Override
	public int getMinY() {
		return -64;
	}

	@Override
	public int getGenDepth() {
		return 384;
	}

	@Override
	public int getSeaLevel() {
		return 63;
	}
}
