package mattmc.client.renderer.chunk;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.state.BlockState;
import mattmc.world.level.block.state.properties.Direction;

/**
 * Extracts rotation information from block states.
 * Converts block state properties (like "facing") into Y-axis rotation degrees
 * for blocks that don't use blockstate JSON variants.
 * 
 * This is separated from ModelElementRenderer to isolate block-specific rotation logic,
 * making it easier to extend for new block types.
 */
public class BlockRotationExtractor {
    
    /**
     * Get Y-axis rotation from blockstate for rotatable blocks.
     * Returns 0 if no rotation is needed.
     * 
     * This is a fallback for blocks that don't have blockstate JSON with rotation info.
     * Most blocks should use blockstate JSON variants instead.
     * 
     * @param block The block instance
     * @param state The block state containing properties
     * @return Y-axis rotation in degrees (0, 90, 180, or 270)
     */
    public static int getYRotationFromState(Block block, BlockState state) {
        if (state == null) {
            return 0;
        }
        
        // Handle wall torches and similar blocks that rotate based on facing
        if (block instanceof mattmc.world.level.block.WallTorchBlock) {
            Direction facing = state.getDirection("facing");
            if (facing != null) {
                return switch (facing) {
                    case NORTH -> 270;
                    case SOUTH -> 90;
                    case WEST -> 180;
                    case EAST -> 0;
                    default -> 0;
                };
            }
        }
        
        // Handle stairs rotation
        if (block instanceof mattmc.world.level.block.StairsBlock) {
            Direction facing = state.getDirection("facing");
            if (facing != null) {
                return switch (facing) {
                    case NORTH -> 0;
                    case SOUTH -> 180;
                    case WEST -> 90;
                    case EAST -> 270;
                    default -> 0;
                };
            }
        }
        
        return 0;
    }
}
