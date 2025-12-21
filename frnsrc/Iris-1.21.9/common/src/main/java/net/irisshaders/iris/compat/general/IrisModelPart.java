package frnsrc.Iris;

import net.minecraft.world.level.block.state.BlockState;

public interface IrisModelPart {
	default BlockState getBlockAppearance() {
		return null;
	}
}
