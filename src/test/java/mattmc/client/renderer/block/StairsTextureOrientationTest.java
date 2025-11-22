package mattmc.client.renderer.block;

import mattmc.client.resources.ResourceManager;
import mattmc.client.resources.model.BlockState;
import mattmc.client.resources.model.BlockStateVariant;
import mattmc.world.level.block.state.properties.Direction;
import mattmc.world.level.block.state.properties.Half;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic test for stairs texture orientation.
 * Tests all cardinal directions and both half positions to detect vertical texture issues.
 * 
 * This test analyzes the blockstate JSON configuration to identify which stairs variants
 * might have texture orientation problems based on their rotation settings.
 */
public class StairsTextureOrientationTest {
    
    private static class TextureOrientationResult {
        Direction facing;
        Half half;
        int xRotation;
        int yRotation;
        boolean uvlock;
        String variantKey;
        
        TextureOrientationResult(Direction facing, Half half, int xRotation, int yRotation, 
                                boolean uvlock, String variantKey) {
            this.facing = facing;
            this.half = half;
            this.xRotation = xRotation;
            this.yRotation = yRotation;
            this.uvlock = uvlock;
            this.variantKey = variantKey;
        }
        
        @Override
        public String toString() {
            return String.format("%-6s %-7s | X:%3d° Y:%3d° | uvlock:%5s | Key: %s", 
                facing, half, xRotation, yRotation, uvlock, variantKey);
        }
    }
    
    /**
     * Test stairs in all cardinal directions and both halves.
     * Reports configuration details for each variant.
     */
    @Test
    public void testStairsTextureOrientationAllDirections() {
        System.out.println("\n=== STAIRS TEXTURE ORIENTATION DIAGNOSTIC TEST ===\n");
        System.out.println("This test reports the rotation settings for all stairs configurations.");
        System.out.println("If textures appear vertical when they should be horizontal, the uvlock");
        System.out.println("or rotation settings may need adjustment.\n");
        
        List<TextureOrientationResult> results = new ArrayList<>();
        
        // Load the birch stairs blockstate
        BlockState blockState = ResourceManager.loadBlockState("birch_stairs");
        
        if (blockState == null) {
            System.out.println("ERROR: Could not load birch_stairs blockstate");
            return;
        }
        
        // Test all combinations of direction and half
        for (Direction facing : Direction.values()) {
            for (Half half : Half.values()) {
                TextureOrientationResult result = analyzeStairsConfiguration(blockState, facing, half);
                if (result != null) {
                    results.add(result);
                }
            }
        }
        
        // Print table header
        System.out.println("FACING HALF    | ROTATIONS     | UVLOCK  | BLOCKSTATE KEY");
        System.out.println("-------|--------|---------------|---------|---------------------------------------");
        
        // Print all results
        for (TextureOrientationResult result : results) {
            System.out.println(result);
        }
        
        // Summary analysis
        System.out.println("\n=== ANALYSIS ===");
        System.out.println(String.format("Total configurations: %d", results.size()));
        
        long uvlockTrue = results.stream().filter(r -> r.uvlock).count();
        long uvlockFalse = results.size() - uvlockTrue;
        System.out.println(String.format("With uvlock=true:  %d", uvlockTrue));
        System.out.println(String.format("With uvlock=false: %d", uvlockFalse));
        
        // Group by rotation
        System.out.println("\nY-Rotation distribution:");
        results.stream()
            .collect(java.util.stream.Collectors.groupingBy(r -> r.yRotation))
            .forEach((rotation, list) -> 
                System.out.println(String.format("  Y=%3d°: %d variants", rotation, list.size())));
        
        System.out.println("\n=== INSTRUCTIONS FOR MANUAL TESTING ===");
        System.out.println("To test texture orientation in game:");
        System.out.println("1. Place birch stairs facing NORTH (default placement)");
        System.out.println("2. Check if wood grain is horizontal or vertical on side faces");
        System.out.println("3. Rotate and place stairs in SOUTH, EAST, WEST directions");
        System.out.println("4. For each direction, try both BOTTOM and TOP half positions");
        System.out.println("5. Note which configurations show vertical texture (incorrect)");
        System.out.println("\n=== END OF DIAGNOSTIC TEST ===\n");
    }
    
    /**
     * Analyze the configuration for stairs with given facing and half.
     */
    private TextureOrientationResult analyzeStairsConfiguration(BlockState blockState, 
                                                                 Direction facing, Half half) {
        // Build variant key: "facing=north,half=bottom,shape=straight"
        String facingStr = facing.toString().toLowerCase();
        String halfStr = half.toString().toLowerCase();
        String variantKey = String.format("facing=%s,half=%s,shape=straight", facingStr, halfStr);
        
        // Get variants for this state
        List<BlockStateVariant> variants = blockState.getVariantsForState(variantKey);
        
        if (variants == null || variants.isEmpty()) {
            System.out.println("WARNING: No variant found for " + variantKey);
            return null;
        }
        
        // Use first variant
        BlockStateVariant variant = variants.get(0);
        
        int xRotation = variant.getX() != null ? variant.getX() : 0;
        int yRotation = variant.getY() != null ? variant.getY() : 0;
        boolean uvlock = variant.getUvlock() != null ? variant.getUvlock() : false;
        
        return new TextureOrientationResult(facing, half, xRotation, yRotation, uvlock, variantKey);
    }
}
