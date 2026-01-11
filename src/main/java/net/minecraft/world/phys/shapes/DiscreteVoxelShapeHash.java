package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.Hash;

/**
 * FerriteCore optimization #7: Hash strategy for DiscreteVoxelShape to enable deduplication
 */
public class DiscreteVoxelShapeHash implements Hash.Strategy<DiscreteVoxelShape> {
    public static final DiscreteVoxelShapeHash INSTANCE = new DiscreteVoxelShapeHash();

    @Override
    public int hashCode(DiscreteVoxelShape shape) {
        if (shape == null) {
            return 0;
        }
        // Hash based on size and a sample of the voxel data
        int result = shape.getXSize();
        result = 31 * result + shape.getYSize();
        result = 31 * result + shape.getZSize();
        
        // Sample a few voxels to avoid expensive full scan
        int xSize = shape.getXSize();
        int ySize = shape.getYSize();
        int zSize = shape.getZSize();
        
        if (xSize > 0 && ySize > 0 && zSize > 0) {
            result = 31 * result + (shape.isFull(0, 0, 0) ? 1 : 0);
            if (xSize > 1 && ySize > 1 && zSize > 1) {
                result = 31 * result + (shape.isFull(xSize - 1, ySize - 1, zSize - 1) ? 1 : 0);
            }
        }
        
        return result;
    }

    @Override
    public boolean equals(DiscreteVoxelShape a, DiscreteVoxelShape b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        
        // Check sizes first
        if (a.getXSize() != b.getXSize() || a.getYSize() != b.getYSize() || a.getZSize() != b.getZSize()) {
            return false;
        }
        
        // Check all voxels
        int xSize = a.getXSize();
        int ySize = a.getYSize();
        int zSize = a.getZSize();
        
        for (int x = 0; x < xSize; x++) {
            for (int y = 0; y < ySize; y++) {
                for (int z = 0; z < zSize; z++) {
                    if (a.isFull(x, y, z) != b.isFull(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
}
