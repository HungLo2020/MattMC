package net.minecraft.client.renderer.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record BlockOutlineRenderState(
	BlockPos pos,
	boolean isTranslucent,
	boolean highContrast,
	VoxelShape shape,
	@Nullable VoxelShape collisionShape,
	@Nullable VoxelShape occlusionShape,
	@Nullable VoxelShape interactionShape
) {
	public BlockOutlineRenderState(BlockPos blockPos, boolean bl, boolean bl2, VoxelShape voxelShape) {
		this(blockPos, bl, bl2, voxelShape, null, null, null);
	}
}
