package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class MineshaftStructure extends Structure {
	public static final MapCodec<MineshaftStructure> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				settingsCodec(instance), MineshaftStructure.Type.CODEC.fieldOf("mineshaft_type").forGetter(mineshaftStructure -> mineshaftStructure.type)
			)
			.apply(instance, MineshaftStructure::new)
	);
	private final MineshaftStructure.Type type;

	public MineshaftStructure(Structure.StructureSettings structureSettings, MineshaftStructure.Type type) {
		super(structureSettings);
		this.type = type;
	}

	@Override
	public int[] nativeStub() {
		return new int[]{7, 0, 0};
	}

	@Override
	public int[] nativeStubParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager,
		ChunkGenerator chunkGenerator, net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor) {
		return new int[]{this.type == MineshaftStructure.Type.MESA ? 1 : 0, chunkGenerator.getMinY()};
	}

	@Override
	public void nativeStubPieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext, BlockPos stub,
		net.minecraft.world.level.block.Rotation rotation, long[] extra, int[] pieces) {
		// Rust planned and moved every piece: [kind, depth, box, direction, fields, entrances, entrance boxes].
		for (int i = 0; i < pieces.length; ) {
			int kind = pieces[i], depth = pieces[i + 1];
			var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(pieces[i + 2], pieces[i + 3], pieces[i + 4], pieces[i + 5], pieces[i + 6], pieces[i + 7]);
			net.minecraft.core.Direction direction = pieces[i + 8] < 0 ? null : net.minecraft.core.Direction.from2DDataValue(pieces[i + 8]);
			int entrances = pieces[i + 12];
			java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = new java.util.ArrayList<>(entrances);
			for (int e = 0; e < entrances; e++) {
				int at = i + 13 + e * 6;
				boxes.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(pieces[at], pieces[at + 1], pieces[at + 2], pieces[at + 3], pieces[at + 4], pieces[at + 5]));
			}
			structurePiecesBuilder.addPiece(switch (kind) {
				case 0 -> new MineshaftPieces.MineShaftCorridor(depth, box, direction, this.type, pieces[i + 9] != 0, pieces[i + 10] != 0, pieces[i + 11]);
				case 1 -> new MineshaftPieces.MineShaftCrossing(depth, box, direction, this.type);
				case 2 -> new MineshaftPieces.MineShaftRoom(depth, box, this.type, boxes);
				case 3 -> new MineshaftPieces.MineShaftStairs(depth, box, direction, this.type);
				default -> throw new IllegalStateException("Unknown native mineshaft piece " + kind);
			});
			i += 13 + entrances * 6;
		}
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext generationContext) {
		generationContext.random().nextDouble();
		ChunkPos chunkPos = generationContext.chunkPos();
		BlockPos blockPos = new BlockPos(chunkPos.getMiddleBlockX(), 50, chunkPos.getMinBlockZ());
		StructurePiecesBuilder structurePiecesBuilder = new StructurePiecesBuilder();
		int i = this.generatePiecesAndAdjust(structurePiecesBuilder, generationContext);
		return Optional.of(new Structure.GenerationStub(blockPos.offset(0, i, 0), Either.right(structurePiecesBuilder)));
	}

	private int generatePiecesAndAdjust(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext) {
		ChunkPos chunkPos = generationContext.chunkPos();
		WorldgenRandom worldgenRandom = generationContext.random();
		ChunkGenerator chunkGenerator = generationContext.chunkGenerator();
		MineshaftPieces.MineShaftRoom mineShaftRoom = new MineshaftPieces.MineShaftRoom(0, worldgenRandom, chunkPos.getBlockX(2), chunkPos.getBlockZ(2), this.type);
		structurePiecesBuilder.addPiece(mineShaftRoom);
		mineShaftRoom.addChildren(mineShaftRoom, structurePiecesBuilder, worldgenRandom);
		int i = chunkGenerator.getSeaLevel();
		if (this.type == MineshaftStructure.Type.MESA) {
			BlockPos blockPos = structurePiecesBuilder.getBoundingBox().getCenter();
			int j = chunkGenerator.getBaseHeight(
				blockPos.getX(), blockPos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, generationContext.heightAccessor(), generationContext.randomState()
			);
			int k = j <= i ? i : Mth.randomBetweenInclusive(worldgenRandom, i, j);
			int l = k - blockPos.getY();
			structurePiecesBuilder.offsetPiecesVertically(l);
			return l;
		} else {
			return structurePiecesBuilder.moveBelowSeaLevel(i, chunkGenerator.getMinY(), worldgenRandom, 10);
		}
	}

	@Override
	public StructureType<?> type() {
		return StructureType.MINESHAFT;
	}

	public static enum Type implements StringRepresentable {
		NORMAL("normal", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_FENCE),
		MESA("mesa", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE);

		public static final Codec<MineshaftStructure.Type> CODEC = StringRepresentable.fromEnum(MineshaftStructure.Type::values);
		private static final IntFunction<MineshaftStructure.Type> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
		private final String name;
		private final BlockState woodState;
		private final BlockState planksState;
		private final BlockState fenceState;

		private Type(final String string2, final Block block, final Block block2, final Block block3) {
			this.name = string2;
			this.woodState = block.defaultBlockState();
			this.planksState = block2.defaultBlockState();
			this.fenceState = block3.defaultBlockState();
		}

		public String getName() {
			return this.name;
		}

		public static MineshaftStructure.Type byId(int i) {
			return (MineshaftStructure.Type)BY_ID.apply(i);
		}

		public BlockState getWoodState() {
			return this.woodState;
		}

		public BlockState getPlanksState() {
			return this.planksState;
		}

		public BlockState getFenceState() {
			return this.fenceState;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
