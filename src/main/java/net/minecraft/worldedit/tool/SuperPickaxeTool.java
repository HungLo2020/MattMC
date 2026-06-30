package net.minecraft.worldedit.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Super pickaxe tool for instant block breaking.
 */
public class SuperPickaxeTool implements Tool {
    private final String mode;
    private final int range;
    
    public SuperPickaxeTool(String mode, int range) {
        this.mode = mode;
        this.range = range;
    }
    
    @Override
    public boolean actPrimary(ServerPlayer player) {
        // Super pickaxe activates on left click (block breaking)
        return true;
    }
    
    @Override
    public boolean actSecondary(ServerPlayer player) {
        return false;
    }
    
    @Override
    public boolean canUse(ServerPlayer player) {
        return WorldEdit.getInstance().getPlatform().hasPermission(player, "worldedit.superpickaxe");
    }
    
    /**
     * Break blocks with super pickaxe.
     */
    public void breakBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        EditSession editSession = session.createEditSession(world);
        
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockVector3 center = BlockVector3.from(pos);
        
        int count = 0;
        
        switch (mode) {
            case "single":
                // Break just the clicked block
                if (editSession.setBlock(center, air)) {
                    count++;
                }
                break;
                
            case "area":
                // Break blocks in a cubic area
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        for (int dz = -range; dz <= range; dz++) {
                            BlockVector3 targetPos = center.add(dx, dy, dz);
                            if (editSession.setBlock(targetPos, air)) {
                                count++;
                            }
                        }
                    }
                }
                break;
                
            case "recursive":
                // Break connected blocks of the same type (flood fill)
                BlockState targetBlock = world.getBlockState(pos);
                if (!targetBlock.isAir()) {
                    recursiveBreak(editSession, center, targetBlock, range * range * range);
                }
                break;
        }
        
        // Remember for undo
        if (editSession.getBlockChangeCount() > 0) {
            session.remember(editSession);
        }
    }
    
    /**
     * Recursively break connected blocks.
     */
    private void recursiveBreak(EditSession editSession, BlockVector3 pos, BlockState targetBlock, int remaining) {
        if (remaining <= 0) return;
        
        BlockState current = editSession.getBlock(pos);
        if (!current.equals(targetBlock)) return;
        
        BlockState air = Blocks.AIR.defaultBlockState();
        if (!editSession.setBlock(pos, air)) return;
        
        // Break adjacent blocks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    recursiveBreak(editSession, pos.add(dx, dy, dz), targetBlock, remaining - 1);
                }
            }
        }
    }
}
