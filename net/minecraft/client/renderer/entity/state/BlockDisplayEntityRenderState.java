package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.Display.BlockDisplay.BlockRenderState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockDisplayEntityRenderState extends DisplayEntityRenderState {
	@Nullable
	public BlockRenderState blockRenderState;

	@Override
	public boolean hasSubState() {
		return this.blockRenderState != null;
	}
}
