package mattmc.world.level.block.state;

import mattmc.world.level.block.state.properties.*;
import mattmc.client.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for stairs blockstate lookup.
 */
@DisplayName("Stairs Integration Tests")
public class StairsIntegrationTest {
    
    @BeforeAll
    static void setup() {
        // Initialize resource manager if needed
        // Note: This may not work in headless CI if resources aren't properly loaded
    }
    
    @Test
    @DisplayName("Variant string format matches blockstate JSON keys")
    void testVariantStringMatchesJson() {
        // Test that our variant strings match the format in birch_stairs.json
        
        // Expected keys from the JSON:
        String[] expectedKeys = {
            "facing=east,half=bottom,shape=straight",
            "facing=north,half=bottom,shape=straight",
            "facing=south,half=bottom,shape=straight",
            "facing=west,half=bottom,shape=straight",
            "facing=east,half=top,shape=straight",
            "facing=north,half=top,shape=straight"
        };
        
        // Create states and verify they produce matching variant strings
        for (String expected : expectedKeys) {
            // Parse the expected key to get values
            String[] parts = expected.split(",");
            Direction dir = null;
            Half half = null;
            StairsShape shape = null;
            
            for (String part : parts) {
                String[] kv = part.split("=");
                switch (kv[0]) {
                    case "facing":
                        dir = Direction.valueOf(kv[1].toUpperCase());
                        break;
                    case "half":
                        half = Half.valueOf(kv[1].toUpperCase());
                        break;
                    case "shape":
                        shape = StairsShape.valueOf(kv[1].toUpperCase());
                        break;
                }
            }
            
            // Create the blockstate
            BlockState state = new BlockState();
            state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
            state.setValue(BlockStateProperties.HALF, half);
            state.setValue(BlockStateProperties.STAIRS_SHAPE, shape);
            
            String actual = state.toVariantString();
            
            System.out.println("Expected: " + expected);
            System.out.println("Actual:   " + actual);
            
            assertEquals(expected, actual, "Variant string should match JSON key");
        }
    }
    
    @Test
    @DisplayName("Blockstate lookup should find correct variant")
    void testBlockstateLookup() {
        // Try to load the blockstate JSON
        mattmc.client.resources.model.BlockState blockStateJson = ResourceManager.loadBlockState("birch_stairs");
        
        if (blockStateJson == null) {
            System.out.println("WARNING: Could not load birch_stairs.json - skipping integration test");
            return;
        }
        
        // Create a state for north-facing stairs
        BlockState state = new BlockState();
        state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        state.setValue(BlockStateProperties.HALF, Half.BOTTOM);
        state.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
        
        String variantString = state.toVariantString();
        System.out.println("Looking up variant: " + variantString);
        
        List<mattmc.client.resources.model.BlockStateVariant> variants = 
            blockStateJson.getVariantsForState(variantString);
        
        assertNotNull(variants, "Should find variants for " + variantString);
        assertFalse(variants.isEmpty(), "Variants list should not be empty");
        
        mattmc.client.resources.model.BlockStateVariant variant = variants.get(0);
        System.out.println("Found variant:");
        System.out.println("  Model: " + variant.getModel());
        System.out.println("  X: " + variant.getX());
        System.out.println("  Y: " + variant.getY());
        System.out.println("  UVLock: " + variant.getUvlock());
        
        // For north-facing stairs, Y rotation should be 270
        assertEquals(Integer.valueOf(270), variant.getY(), "North-facing stairs should have Y=270");
    }
}
