package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.Hash;
import java.util.Objects;

/**
 * FerriteCore optimization #7: Hash strategy for ArrayVoxelShape to enable deduplication
 */
public class ArrayVoxelShapeHash implements Hash.Strategy<ArrayVoxelShape> {
    public static final ArrayVoxelShapeHash INSTANCE = new ArrayVoxelShapeHash();

    @Override
    public int hashCode(ArrayVoxelShape shape) {
        if (shape == null) {
            return 0;
        }
        int result = 31 * Objects.hashCode(shape.getCoords(net.minecraft.core.Direction.Axis.X));
        result = 31 * result + Objects.hashCode(shape.getCoords(net.minecraft.core.Direction.Axis.Y));
        result = 31 * result + Objects.hashCode(shape.getCoords(net.minecraft.core.Direction.Axis.Z));
        result = 31 * result + DiscreteVoxelShapeHash.INSTANCE.hashCode(shape.shape);
        return result;
    }

    @Override
    public boolean equals(ArrayVoxelShape a, ArrayVoxelShape b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getCoords(net.minecraft.core.Direction.Axis.X), b.getCoords(net.minecraft.core.Direction.Axis.X)) &&
               Objects.equals(a.getCoords(net.minecraft.core.Direction.Axis.Y), b.getCoords(net.minecraft.core.Direction.Axis.Y)) &&
               Objects.equals(a.getCoords(net.minecraft.core.Direction.Axis.Z), b.getCoords(net.minecraft.core.Direction.Axis.Z)) &&
               DiscreteVoxelShapeHash.INSTANCE.equals(a.shape, b.shape);
    }
}
