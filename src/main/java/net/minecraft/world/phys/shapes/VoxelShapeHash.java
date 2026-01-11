package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.Hash;

/**
 * FerriteCore optimization #7: Overall hash strategy for VoxelShape to enable deduplication
 */
public class VoxelShapeHash implements Hash.Strategy<VoxelShape> {
    public static final VoxelShapeHash INSTANCE = new VoxelShapeHash();

    @Override
    public int hashCode(VoxelShape shape) {
        if (shape == null) {
            return 0;
        }
        
        if (shape instanceof ArrayVoxelShape arrayShape) {
            return ArrayVoxelShapeHash.INSTANCE.hashCode(arrayShape);
        } else if (shape instanceof CubeVoxelShape) {
            return DiscreteVoxelShapeHash.INSTANCE.hashCode(shape.shape);
        } else {
            return shape.hashCode();
        }
    }

    @Override
    public boolean equals(VoxelShape a, VoxelShape b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.getClass() != b.getClass()) {
            return false;
        }
        
        if (a instanceof ArrayVoxelShape arrayA && b instanceof ArrayVoxelShape arrayB) {
            return ArrayVoxelShapeHash.INSTANCE.equals(arrayA, arrayB);
        } else if (a instanceof CubeVoxelShape) {
            return DiscreteVoxelShapeHash.INSTANCE.equals(a.shape, b.shape);
        } else {
            return a.equals(b);
        }
    }
}
