package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity.WobbleStyle;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DecoratedPotRenderState extends BlockEntityRenderState {
	public float yRot;
	@Nullable
	public WobbleStyle wobbleStyle;
	public float wobbleProgress;
	public PotDecorations decorations = PotDecorations.EMPTY;
	public Direction direction = Direction.NORTH;
}
