package net.minecraft.client.renderer.chunk;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RenderSectionRegion implements BlockAndTintGetter {
	public static final int RADIUS = 1;
	public static final int SIZE = 3;
	private final int minSectionX;
	private final int minSectionY;
	private final int minSectionZ;
	private final SectionCopy[] sections;
	private final Level level;

	RenderSectionRegion(Level level, int i, int j, int k, SectionCopy[] sectionCopys) {
		this.level = level;
		this.minSectionX = i;
		this.minSectionY = j;
		this.minSectionZ = k;
		this.sections = sectionCopys;
	}

	public BlockState getBlockState(BlockPos blockPos) {
		return this.getSection(
				SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getY()), SectionPos.blockToSectionCoord(blockPos.getZ())
			)
			.getBlockState(blockPos);
	}

	public FluidState getFluidState(BlockPos blockPos) {
		return this.getSection(
				SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getY()), SectionPos.blockToSectionCoord(blockPos.getZ())
			)
			.getBlockState(blockPos)
			.getFluidState();
	}

	public float getShade(Direction direction, boolean bl) {
		return this.level.getShade(direction, bl);
	}

	public LevelLightEngine getLightEngine() {
		return this.level.getLightEngine();
	}

	@Nullable
	public BlockEntity getBlockEntity(BlockPos blockPos) {
		return this.getSection(
				SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getY()), SectionPos.blockToSectionCoord(blockPos.getZ())
			)
			.getBlockEntity(blockPos);
	}

	private SectionCopy getSection(int i, int j, int k) {
		return this.sections[index(this.minSectionX, this.minSectionY, this.minSectionZ, i, j, k)];
	}

	public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
		return this.level.getBlockTint(blockPos, colorResolver);
	}

	public int getMinY() {
		return this.level.getMinY();
	}

	public int getHeight() {
		return this.level.getHeight();
	}

	public static int index(int i, int j, int k, int l, int m, int n) {
		return l - i + (m - j) * 3 + (n - k) * 3 * 3;
	}
}
