package net.minecraft.worldedit.mask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * A mask that only matches when every child mask matches.
 */
public class MaskIntersection implements Mask {
    private final List<Mask> masks = new ArrayList<>();

    public MaskIntersection(Mask... masks) {
        this.masks.addAll(Arrays.asList(masks));
    }

    public void add(Mask mask) {
        masks.add(mask);
    }

    @Override
    public boolean test(Extent extent, BlockVector3 position) {
        for (Mask mask : masks) {
            if (mask != null && !mask.test(extent, position)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean test(BlockState state) {
        for (Mask mask : masks) {
            if (mask != null && !mask.test(state)) {
                return false;
            }
        }
        return true;
    }
}
