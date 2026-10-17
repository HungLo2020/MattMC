package net.minecraft.world.level.levelgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public abstract class ScatteredFeaturePiece extends StructurePiece {
	protected final int width;
	protected final int height;
	protected final int depth;
	protected int heightPosition = -1;

	protected ScatteredFeaturePiece(StructurePieceType structurePieceType, int i, int j, int k, int l, int m, int n, Direction direction) {
		super(structurePieceType, 0, StructurePiece.makeBoundingBox(i, j, k, direction, l, m, n));
		this.width = l;
		this.height = m;
		this.depth = n;
		this.setOrientation(direction);
	}

	/** A piece Rust planned ({@code structure/small_plan.rs}): its box and direction, no draws. */
	protected ScatteredFeaturePiece(StructurePieceType structurePieceType, BoundingBox boundingBox, Direction direction, int width, int height, int depth) {
		super(structurePieceType, 0, boundingBox);
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.setOrientation(direction);
	}

	/**
	 * Rebuilds one {@code small_plan.rs} scattered piece's words ({@code [kind, genDepth, box (6), orientation,
	 * fieldCount, width, height, depth, heightPosition]}) with {@code factory}, checking the planned size and position.
	 */
	public static StructurePiece nativePiece(int[] words, int kind, int width, int height, int depth,
		java.util.function.BiFunction<BoundingBox, Direction, ScatteredFeaturePiece> factory) {
		if (words.length != 14 || words[0] != kind || words[1] != 0 || words[9] != 4 || words[8] < 0 || words[8] > 3
			|| words[10] != width || words[11] != height || words[12] != depth || words[13] != -1) {
			throw new IllegalArgumentException("Bad native scattered piece words for kind " + kind);
		}
		BoundingBox box = new BoundingBox(words[2], words[3], words[4], words[5], words[6], words[7]);
		return factory.apply(box, Direction.from2DDataValue(words[8]));
	}

	protected ScatteredFeaturePiece(StructurePieceType structurePieceType, CompoundTag compoundTag) {
		super(structurePieceType, compoundTag);
		this.width = compoundTag.getIntOr("Width", 0);
		this.height = compoundTag.getIntOr("Height", 0);
		this.depth = compoundTag.getIntOr("Depth", 0);
		this.heightPosition = compoundTag.getIntOr("HPos", 0);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext structurePieceSerializationContext, CompoundTag compoundTag) {
		compoundTag.putInt("Width", this.width);
		compoundTag.putInt("Height", this.height);
		compoundTag.putInt("Depth", this.depth);
		compoundTag.putInt("HPos", this.heightPosition);
	}

	/** {@link NativeStructurePieces}: the header and width, height, depth and height position. */
	protected long[] nativeScatteredWords(int kind, int extra) {
		long[] words = NativeStructurePieces.header(this, kind, 4 + extra);
		words[9] = this.width;
		words[10] = this.height;
		words[11] = this.depth;
		words[12] = this.heightPosition;
		return words;
	}

	protected void nativeScatteredUpdate(long[] words) {
		this.boundingBox = NativeStructurePieces.box(words);
		this.heightPosition = (int)words[12];
	}

	protected boolean updateAverageGroundHeight(LevelAccessor levelAccessor, BoundingBox boundingBox, int i) {
		if (this.heightPosition >= 0) {
			return true;
		} else {
			int j = 0;
			int k = 0;
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

			for (int l = this.boundingBox.minZ(); l <= this.boundingBox.maxZ(); l++) {
				for (int m = this.boundingBox.minX(); m <= this.boundingBox.maxX(); m++) {
					mutableBlockPos.set(m, 64, l);
					if (boundingBox.isInside(mutableBlockPos)) {
						j += levelAccessor.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableBlockPos).getY();
						k++;
					}
				}
			}

			if (k == 0) {
				return false;
			} else {
				this.heightPosition = j / k;
				this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + i, 0);
				return true;
			}
		}
	}

	protected boolean updateHeightPositionToLowestGroundHeight(LevelAccessor levelAccessor, int i) {
		if (this.heightPosition >= 0) {
			return true;
		} else {
			int j = levelAccessor.getMaxY() + 1;
			boolean bl = false;
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

			for (int k = this.boundingBox.minZ(); k <= this.boundingBox.maxZ(); k++) {
				for (int l = this.boundingBox.minX(); l <= this.boundingBox.maxX(); l++) {
					mutableBlockPos.set(l, 0, k);
					j = Math.min(j, levelAccessor.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableBlockPos).getY());
					bl = true;
				}
			}

			if (!bl) {
				return false;
			} else {
				this.heightPosition = j;
				this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + i, 0);
				return true;
			}
		}
	}
}
