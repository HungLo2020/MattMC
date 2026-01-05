package net.minecraft.worldedit.brush;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.Pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * Smooth brush - smooths terrain by averaging heights
 */
public class SmoothBrush implements Brush {
    private final int iterations;
    
    public SmoothBrush(int iterations) {
        this.iterations = iterations;
    }
    
    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) {
        int radius = (int) Math.floor(size);
        
        for (int iter = 0; iter < iterations; iter++) {
            Map<BlockVector3, BlockState> changes = new HashMap<>();
            
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z <= radius * radius) {
                        BlockVector3 col = position.add(x, 0, z);
                        
                        // Find the top block in this column
                        int topY = findTopBlock(editSession, col);
                        if (topY == -1) continue;
                        
                        // Calculate average height of neighbors
                        int avgY = calculateAverageHeight(editSession, position, x, z, radius);
                        
                        if (avgY != topY) {
                            // Move blocks to match average
                            if (avgY > topY) {
                                // Fill up
                                for (int y = topY + 1; y <= avgY; y++) {
                                    BlockState below = editSession.getBlock(col.add(0, topY, 0));
                                    changes.put(col.add(0, y, 0), below);
                                }
                            } else {
                                // Remove blocks
                                for (int y = avgY + 1; y <= topY; y++) {
                                    changes.put(col.add(0, y, 0), 
                                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                                }
                            }
                        }
                    }
                }
            }
            
            // Apply changes
            for (Map.Entry<BlockVector3, BlockState> entry : changes.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
        }
    }
    
    private int findTopBlock(EditSession session, BlockVector3 col) {
        for (int y = 255; y >= 0; y--) {
            BlockState state = session.getBlock(col.add(0, y, 0));
            if (state != null && !state.isAir()) {
                return y;
            }
        }
        return -1;
    }
    
    private int calculateAverageHeight(EditSession session, BlockVector3 center, int x, int z, int radius) {
        int sum = 0;
        int count = 0;
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = x + dx;
                int nz = z + dz;
                
                if (nx * nx + nz * nz <= radius * radius) {
                    int height = findTopBlock(session, center.add(nx, 0, nz));
                    if (height != -1) {
                        sum += height;
                        count++;
                    }
                }
            }
        }
        
        return count > 0 ? sum / count : -1;
    }
}
