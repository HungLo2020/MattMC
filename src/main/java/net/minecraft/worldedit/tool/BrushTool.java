package net.minecraft.worldedit.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Brush tool for painting with blocks.
 */
public class BrushTool implements Tool {
    private final String type;
    private final BlockState material;
    private final int radius;
    
    public BrushTool(String type, BlockState material, int radius) {
        this.type = type;
        this.material = material;
        this.radius = radius;
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
        
        switch (type) {
            case "sphere":
                // Create a sphere of blocks
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            double distance = Math.sqrt(x * x + y * y + z * z);
                            if (distance <= radius) {
                                BlockVector3 pos = center.add(x, y, z);
                                editSession.setBlock(pos, material);
                            }
                        }
                    }
                }
                break;
                
            // Future: Add more brush types (cylinder, smooth, etc.)
        }
        
        // Remember for undo
        if (editSession.getBlockChangeCount() > 0) {
            session.remember(editSession);
        }
    }
}
