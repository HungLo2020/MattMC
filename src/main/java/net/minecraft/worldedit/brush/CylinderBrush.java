package net.minecraft.worldedit.brush;

import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.Pattern;

/**
 * Cylinder brush - places vertical cylinders
 */
public class CylinderBrush implements Brush {
    private final int height;
    private final boolean hollow;
    
    public CylinderBrush(int height, boolean hollow) {
        this.height = height;
        this.hollow = hollow;
    }
    
    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) {
        int radius = (int) Math.floor(size);
        int radiusSquared = radius * radius;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int distSquared = x * x + z * z;
                
                if (distSquared <= radiusSquared) {
                    if (hollow) {
                        int innerRadiusSquared = (radius - 1) * (radius - 1);
                        if (distSquared > innerRadiusSquared) {
                            for (int y = 0; y < height; y++) {
                                BlockVector3 pos = position.add(x, y, z);
                                editSession.setBlock(pos, pattern.apply(pos));
                            }
                        }
                    } else {
                        for (int y = 0; y < height; y++) {
                            BlockVector3 pos = position.add(x, y, z);
                            editSession.setBlock(pos, pattern.apply(pos));
                        }
                    }
                }
            }
        }
    }
}
