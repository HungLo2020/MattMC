package mattmc.client.renderer.block;

import mattmc.client.resources.ResourceManager;
import mattmc.client.resources.model.BlockState;
import mattmc.client.resources.model.BlockStateVariant;
import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.ModelElement;
import mattmc.world.level.block.state.properties.Direction;
import mattmc.world.level.block.state.properties.Half;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic test for stairs texture orientation.
 * Tests all cardinal directions and both half positions to detect vertical texture issues.
 * 
 * This test analyzes the actual UV coordinates from the model to determine if textures
 * will appear vertical (incorrect) or horizontal (correct) for each stairs variant.
 */
public class StairsTextureOrientationTest {
    
    private static class TextureOrientationResult {
        Direction facing;
        Half half;
        int xRotation;
        int yRotation;
        boolean uvlock;
        String variantKey;
        String textureOrientation; // "CORRECT", "VERTICAL", or "UNKNOWN"
        String analysisDetails;
        
        TextureOrientationResult(Direction facing, Half half, int xRotation, int yRotation, 
                                boolean uvlock, String variantKey, String textureOrientation, String analysisDetails) {
            this.facing = facing;
            this.half = half;
            this.xRotation = xRotation;
            this.yRotation = yRotation;
            this.uvlock = uvlock;
            this.variantKey = variantKey;
            this.textureOrientation = textureOrientation;
            this.analysisDetails = analysisDetails;
        }
        
        @Override
        public String toString() {
            return String.format("%-6s %-7s | X:%3d° Y:%3d° | uvlock:%5s | %-8s | %s", 
                facing, half, xRotation, yRotation, uvlock, textureOrientation, analysisDetails);
        }
    }
    
    /**
     * Test stairs in all cardinal directions and both halves.
     * Analyzes UV coordinates to determine if textures are oriented correctly.
     */
    @Test
    public void testStairsTextureOrientationAllDirections() {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║        STAIRS TEXTURE ORIENTATION DIAGNOSTIC TEST                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("This test checks if wood plank textures on stairs appear HORIZONTAL (✓)");
        System.out.println("or VERTICAL (✗). The wood grain should run horizontally, not vertically.");
        System.out.println();
        
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
        
        // Count results by type
        long correctCount = results.stream().filter(r -> "CORRECT".equals(r.textureOrientation)).count();
        long mixedCount = results.stream().filter(r -> "MIXED".equals(r.textureOrientation)).count();
        long verticalCount = results.stream().filter(r -> "VERTICAL".equals(r.textureOrientation)).count();
        
        // Print results grouped by status
        System.out.println("┌────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ TEST RESULTS BY STAIR DIRECTION                                        │");
        System.out.println("└────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        // Show correct configurations
        if (correctCount > 0) {
            System.out.println("✓ CORRECT - Textures are HORIZONTAL (wood grain runs left-right):");
            System.out.println("  ┌──────────────────────────────────────────────────────────┐");
            results.stream()
                .filter(r -> "CORRECT".equals(r.textureOrientation))
                .forEach(r -> System.out.println(String.format("  │  ✓ %-10s %-6s  (Rotation: Y=%3d°)              │", 
                    r.facing, r.half, r.yRotation)));
            System.out.println("  └──────────────────────────────────────────────────────────┘");
            System.out.println();
        }
        
        // Show mixed/incorrect configurations
        if (mixedCount > 0 || verticalCount > 0) {
            System.out.println("✗ INCORRECT - Textures are VERTICAL (wood grain runs up-down):");
            System.out.println("  ┌──────────────────────────────────────────────────────────┐");
            results.stream()
                .filter(r -> "MIXED".equals(r.textureOrientation) || "VERTICAL".equals(r.textureOrientation))
                .forEach(r -> System.out.println(String.format("  │  ✗ %-10s %-6s  (Rotation: Y=%3d°)  ← WRONG    │", 
                    r.facing, r.half, r.yRotation)));
            System.out.println("  └──────────────────────────────────────────────────────────┘");
            System.out.println();
        }
        
        // Print summary
        System.out.println("┌────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ SUMMARY                                                                │");
        System.out.println("└────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println(String.format("  Total stair configurations tested: %d", results.size()));
        System.out.println(String.format("  ✓ Correct (horizontal texture):   %d", correctCount));
        System.out.println(String.format("  ✗ Wrong (vertical texture):       %d", mixedCount + verticalCount));
        System.out.println();
        
        if (correctCount == results.size()) {
            System.out.println("  ╔══════════════════════════════════════════════════════════╗");
            System.out.println("  ║  ✓✓✓ ALL STAIRS HAVE CORRECT TEXTURE ORIENTATION! ✓✓✓  ║");
            System.out.println("  ╚══════════════════════════════════════════════════════════╝");
        } else {
            System.out.println("  ╔══════════════════════════════════════════════════════════╗");
            System.out.println("  ║  ✗ PROBLEM FOUND: Some stairs have vertical textures    ║");
            System.out.println("  ╚══════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("  EXPLANATION:");
            System.out.println("  - Stairs facing EAST/WEST are correct (Y-rotation 0°/180°)");
            System.out.println("  - Stairs facing NORTH/SOUTH are wrong (Y-rotation 90°/270°)");
            System.out.println("  - The uvlock implementation needs adjustment for 90°/270° rotations");
        }
        
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();
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
        
        // Load the model to analyze UV coordinates
        String modelPath = variant.getModel();
        if (modelPath == null) {
            modelPath = "birch_stairs";
        }
        
        // Analyze the texture orientation based on the model and rotation
        String orientation = analyzeTextureOrientation(modelPath, xRotation, yRotation, uvlock);
        String details = getOrientationDetails(modelPath, xRotation, yRotation, uvlock);
        
        return new TextureOrientationResult(facing, half, xRotation, yRotation, uvlock, variantKey, orientation, details);
    }
    
    /**
     * Analyze texture orientation based on model UV coordinates and rotation.
     * 
     * For stairs side faces (north, south, east, west), we expect:
     * - Horizontal planks: UV width > UV height (correct)
     * - Vertical planks: UV height > UV width (incorrect)
     */
    private String analyzeTextureOrientation(String modelPath, int xRotation, int yRotation, boolean uvlock) {
        // Load the model
        BlockModel model = ResourceManager.loadBlockModel(modelPath);
        if (model == null || model.getElements() == null) {
            return "UNKNOWN";
        }
        
        // Analyze side face UVs from the model elements
        // For stairs, element 0 is the bottom slab, element 1 is the top step
        // We care about vertical faces (north, south, east, west)
        
        int verticalFaceCount = 0;
        int correctFaceCount = 0;
        
        for (int elemIdx = 0; elemIdx < model.getElements().size(); elemIdx++) {
            ModelElement element = model.getElements().get(elemIdx);
            Map<String, ModelElement.ElementFace> faces = element.getFaces();
            if (faces == null) continue;
            
            // Check each vertical face
            for (String faceDir : new String[]{"north", "south", "east", "west"}) {
                ModelElement.ElementFace face = faces.get(faceDir);
                if (face == null) continue;
                
                List<Float> uv = face.getUv();
                if (uv == null || uv.size() < 4) continue;
                
                // Get UV dimensions
                float u0 = uv.get(0);
                float v0 = uv.get(1);
                float u1 = uv.get(2);
                float v1 = uv.get(3);
                float uvWidth = Math.abs(u1 - u0);
                float uvHeight = Math.abs(v1 - v0);
                
                // Simulate the uvlock rotation effect
                // When uvlock=true and yRotation!=0, UVs get rotated by yRotation for vertical faces
                int effectiveRotation = (face.getRotation() != null) ? face.getRotation() : 0;
                if (uvlock && yRotation != 0) {
                    effectiveRotation = (effectiveRotation + yRotation) % 360;
                }
                
                // Check if rotation swaps width and height
                boolean isRotated90or270 = (effectiveRotation % 180) == 90;
                
                // Determine final orientation
                boolean isVertical;
                if (isRotated90or270) {
                    // 90° or 270° rotation swaps U and V, so width becomes height
                    isVertical = uvWidth > uvHeight; // After rotation, this becomes vertical
                } else {
                    // 0° or 180° rotation maintains orientation
                    isVertical = uvHeight > uvWidth;
                }
                
                if (isVertical) {
                    verticalFaceCount++;
                } else {
                    correctFaceCount++;
                }
            }
        }
        
        // Determine overall orientation
        if (verticalFaceCount == 0 && correctFaceCount > 0) {
            return "CORRECT";
        } else if (verticalFaceCount > 0 && correctFaceCount == 0) {
            return "VERTICAL";
        } else if (verticalFaceCount > 0 && correctFaceCount > 0) {
            return "MIXED";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Get detailed analysis information for a configuration.
     */
    private String getOrientationDetails(String modelPath, int xRotation, int yRotation, boolean uvlock) {
        BlockModel model = ResourceManager.loadBlockModel(modelPath);
        if (model == null || model.getElements() == null) {
            return "Model not found";
        }
        
        List<String> wrongFaces = new ArrayList<>();
        
        for (int elemIdx = 0; elemIdx < model.getElements().size(); elemIdx++) {
            ModelElement element = model.getElements().get(elemIdx);
            Map<String, ModelElement.ElementFace> faces = element.getFaces();
            if (faces == null) continue;
            
            for (String faceDir : new String[]{"north", "south", "east", "west"}) {
                ModelElement.ElementFace face = faces.get(faceDir);
                if (face == null) continue;
                
                List<Float> uv = face.getUv();
                if (uv == null || uv.size() < 4) continue;
                
                float u0 = uv.get(0);
                float v0 = uv.get(1);
                float u1 = uv.get(2);
                float v1 = uv.get(3);
                float uvWidth = Math.abs(u1 - u0);
                float uvHeight = Math.abs(v1 - v0);
                
                int effectiveRotation = (face.getRotation() != null) ? face.getRotation() : 0;
                if (uvlock && yRotation != 0) {
                    effectiveRotation = (effectiveRotation + yRotation) % 360;
                }
                
                boolean isRotated90or270 = (effectiveRotation % 180) == 90;
                boolean isVertical;
                if (isRotated90or270) {
                    isVertical = uvWidth > uvHeight;
                } else {
                    isVertical = uvHeight > uvWidth;
                }
                
                if (isVertical) {
                    wrongFaces.add(String.format("E%d:%s", elemIdx, faceDir));
                }
            }
        }
        
        if (wrongFaces.isEmpty()) {
            return "All faces correct";
        } else {
            return "Wrong: " + String.join(",", wrongFaces);
        }
    }
}
