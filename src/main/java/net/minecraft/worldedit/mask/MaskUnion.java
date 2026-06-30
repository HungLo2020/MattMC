package net.minecraft.worldedit.mask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * A mask that matches when any child mask matches.
 */
public class MaskUnion implements Mask {
    private final List<Mask> masks = new ArrayList<>();

    public MaskUnion(Mask... masks) {
        this.masks.addAll(Arrays.asList(masks));
    }

    @Override
    public boolean test(Extent extent, BlockVector3 position) {
        for (Mask mask : masks) {
            if (mask != null && mask.test(extent, position)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean test(BlockState state) {
        for (Mask mask : masks) {
            if (mask != null && mask.test(state)) {
                return true;
            }
        }
        return false;
    }
}
