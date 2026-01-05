package net.minecraft.worldedit.brush;

import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.Pattern;

/**
 * Interface for all brush types
 */
public interface Brush {
    /**
     * Build the brush shape at the given position
     * 
     * @param editSession The edit session to use
     * @param position The center position
     * @param pattern The pattern to use
     * @param size The size/radius of the brush
     */
    void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size);
}
