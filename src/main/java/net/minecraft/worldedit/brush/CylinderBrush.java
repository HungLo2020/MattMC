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
        editSession.makeCylinder(position, pattern, size, height, !hollow);
    }
}
