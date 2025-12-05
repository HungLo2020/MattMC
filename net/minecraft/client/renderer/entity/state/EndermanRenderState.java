package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EndermanRenderState extends HumanoidRenderState {
	public boolean isCreepy;
	@Nullable
	public BlockState carriedBlock;
}
