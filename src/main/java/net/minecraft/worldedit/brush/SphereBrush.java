package net.minecraft.worldedit.brush;

import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.Pattern;

/**
 * Sphere brush - places spheres of blocks
 */
public class SphereBrush implements Brush {
    private final boolean hollow;
    
    public SphereBrush(boolean hollow) {
        this.hollow = hollow;
    }
    
    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) {
        int radius = (int) Math.floor(size);
        int radiusSquared = radius * radius;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSquared = x * x + y * y + z * z;
                    
                    if (distSquared <= radiusSquared) {
                        if (hollow) {
                            // Only place blocks on the surface
                            int innerRadiusSquared = (radius - 1) * (radius - 1);
                            if (distSquared > innerRadiusSquared) {
                                BlockVector3 pos = position.add(x, y, z);
                                editSession.setBlock(pos, pattern.apply(pos));
                            }
                        } else {
                            BlockVector3 pos = position.add(x, y, z);
                            editSession.setBlock(pos, pattern.apply(pos));
                        }
                    }
                }
            }
        }
    }
}
