package mattmc.world.level.block.state;

import mattmc.world.level.block.state.properties.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the stairs rendering flow works correctly.
 */
@DisplayName("Stairs Render Flow Tests")
public class StairsRenderFlowTest {
    
    @Test
    @DisplayName("Stairs placement should create correct variant string")
    void testStairsPlacement() {
        // Simulate what StairsBlock.getPlacementState does
        BlockState state = new BlockState();
        
        // Test all four directions
        for (Direction dir : Direction.values()) {
            state = new BlockState();
            state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
            state.setValue(BlockStateProperties.HALF, Half.BOTTOM);
            state.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
            
            String variant = state.toVariantString();
            String expected = "facing=" + dir.name().toLowerCase() + ",half=bottom,shape=straight";
            
            System.out.println("Direction " + dir + " -> variant: " + variant);
            assertEquals(expected, variant, "Variant string for " + dir + " is incorrect");
        }
    }
    
    @Test
    @DisplayName("getValue should return the same direction that was set")
    void testGetValueReturnsSetValue() {
        for (Direction dir : Direction.values()) {
            BlockState state = new BlockState();
            state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
            
            Direction retrieved = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            
            System.out.println("Set " + dir + ", got " + retrieved);
            assertEquals(dir, retrieved, "getValue should return the same direction that was set");
        }
    }
    
    @Test
    @DisplayName("Properties map should contain correct key and value")
    void testPropertiesMapContents() {
        BlockState state = new BlockState();
        state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        state.setValue(BlockStateProperties.HALF, Half.TOP);
        state.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
        
        // Check the internal map via toVariantString
        String variant = state.toVariantString();
        System.out.println("Full variant: " + variant);
        
        // Should be alphabetically sorted: facing, half, shape
        assertTrue(variant.startsWith("facing="), "Should start with facing");
        assertTrue(variant.contains(",half="), "Should contain half");
        assertTrue(variant.contains(",shape="), "Should contain shape");
        
        assertEquals("facing=east,half=top,shape=straight", variant);
    }
    
    @Test
    @DisplayName("Variant string should match blockstate JSON keys exactly")
    void testVariantMatchesJsonKeys() {
        // The blockstate JSON has keys like:
        // "facing=east,half=bottom,shape=straight"
        // "facing=north,half=top,shape=inner_left"
        
        BlockState state = new BlockState();
        state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        state.setValue(BlockStateProperties.HALF, Half.BOTTOM);
        state.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
        
        String variant = state.toVariantString();
        assertEquals("facing=east,half=bottom,shape=straight", variant);
        
        // Test with INNER_LEFT shape
        state = new BlockState();
        state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        state.setValue(BlockStateProperties.HALF, Half.TOP);
        state.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_LEFT);
        
        variant = state.toVariantString();
        assertEquals("facing=north,half=top,shape=inner_left", variant);
    }
}
