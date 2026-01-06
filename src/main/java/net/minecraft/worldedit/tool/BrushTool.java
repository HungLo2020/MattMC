package net.minecraft.worldedit.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.brush.Brush;
import net.minecraft.worldedit.brush.SphereBrush;
import net.minecraft.worldedit.brush.CylinderBrush;
import net.minecraft.worldedit.brush.SmoothBrush;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.Pattern;
import net.minecraft.worldedit.pattern.SingleBlockPattern;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Brush tool for painting with blocks.
 */
public class BrushTool implements Tool {
    private final String type;
    private final BlockState material;
    private final int radius;
    private Brush brush;
    private Pattern pattern;
    
    public BrushTool(String type, BlockState material, int radius) {
        this.type = type;
        this.material = material;
        this.radius = radius;
        this.pattern = new SingleBlockPattern(material);
        
        // Initialize brush based on type
        switch (type) {
            case "sphere":
                this.brush = new SphereBrush(false);
                break;
            case "smooth":
                this.brush = new SmoothBrush(3);
                break;
            case "cylinder":
                this.brush = new CylinderBrush(radius, false);
                break;
            default:
                this.brush = new SphereBrush(false);
        }
    }
    
    @Override
    public boolean actPrimary(ServerPlayer player) {
        return false; // Brush uses right-click
    }
    
    @Override
    public boolean actSecondary(ServerPlayer player) {
        // Apply brush at target location
        net.minecraft.world.phys.HitResult hit = player.pick(100, 0, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            applyBrush(player, blockHit.getBlockPos());
            return true;
        }
        return false;
    }
    
    @Override
    public boolean canUse(ServerPlayer player) {
        return WorldEdit.getInstance().getPlatform().hasPermission(player, "worldedit.brush." + type);
    }
    
    /**
     * Apply the brush at a location.
     */
    private void applyBrush(ServerPlayer player, BlockPos target) {
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        EditSession editSession = new EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        BlockVector3 center = BlockVector3.from(target);
        
        // Use the brush interface for flexible brush types
        brush.build(editSession, center, pattern, radius);
        
        // Remember for undo
        if (editSession.getBlockChangeCount() > 0) {
            session.remember(editSession);
        }
    }
}
