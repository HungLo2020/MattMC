package mattmc.world.level.block;

import mattmc.world.level.block.state.BlockState;
import mattmc.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SlabBlock class.
 * Validates slab placement, double slab creation, and canBeReplaced logic.
 */
public class SlabBlockTest {
    
    @Test
    public void testSlabPlacementBottom() {
        SlabBlock slab = new SlabBlock();
        
        // Clicking on top face should place bottom slab
        BlockState state = slab.getPlacementState(0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        assertNotNull(state, "Placement state should not be null");
        assertEquals(SlabType.BOTTOM, state.getValue("type"), "Clicking top face should place bottom slab");
    }
    
    @Test
    public void testSlabPlacementTop() {
        SlabBlock slab = new SlabBlock();
        
        // Clicking on bottom face should place top slab
        BlockState state = slab.getPlacementState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertNotNull(state, "Placement state should not be null");
        assertEquals(SlabType.TOP, state.getValue("type"), "Clicking bottom face should place top slab");
    }
    
    @Test
    public void testSlabPlacementSideLowerHalf() {
        SlabBlock slab = new SlabBlock();
        
        // Clicking on lower half of side face (Y < 0.5) should place bottom slab
        BlockState state = slab.getPlacementState(0, 0, 0, 0, 0, 0, 2, 0, 0.25f, 0);
        assertNotNull(state, "Placement state should not be null");
        assertEquals(SlabType.BOTTOM, state.getValue("type"), "Clicking lower half of side face should place bottom slab");
    }
    
    @Test
    public void testSlabPlacementSideUpperHalf() {
        SlabBlock slab = new SlabBlock();
        
        // Clicking on upper half of side face (Y > 0.5) should place top slab
        BlockState state = slab.getPlacementState(0, 0, 0, 0, 0, 0, 2, 0, 0.75f, 0);
        assertNotNull(state, "Placement state should not be null");
        assertEquals(SlabType.TOP, state.getValue("type"), "Clicking upper half of side face should place top slab");
    }
    
    @Test
    public void testDoubleSlabState() {
        SlabBlock slab = new SlabBlock();
        
        BlockState doubleState = slab.getDoubleSlabState();
        assertNotNull(doubleState, "Double slab state should not be null");
        assertEquals(SlabType.DOUBLE, doubleState.getValue("type"), "Double slab state should have DOUBLE type");
    }
    
    @Test
    public void testCanBeReplacedBottomSlabWithTopPlacement() {
        SlabBlock slab = new SlabBlock();
        
        BlockState bottomSlabState = new BlockState();
        bottomSlabState.setValue("type", SlabType.BOTTOM);
        
        // Clicking bottom face of a bottom slab should allow combining (placing top half)
        assertTrue(slab.canBeReplacedBy(bottomSlabState, 0, 0.75f), 
            "Bottom slab should be replaceable when clicking bottom face (places top half)");
    }
    
    @Test
    public void testCanBeReplacedTopSlabWithBottomPlacement() {
        SlabBlock slab = new SlabBlock();
        
        BlockState topSlabState = new BlockState();
        topSlabState.setValue("type", SlabType.TOP);
        
        // Clicking top face of a top slab should allow combining (placing bottom half)
        assertTrue(slab.canBeReplacedBy(topSlabState, 1, 0.25f), 
            "Top slab should be replaceable when clicking top face (places bottom half)");
    }
    
    @Test
    public void testCannotReplaceDoubleSlabs() {
        SlabBlock slab = new SlabBlock();
        
        BlockState doubleSlabState = new BlockState();
        doubleSlabState.setValue("type", SlabType.DOUBLE);
        
        // Double slabs cannot be replaced
        assertFalse(slab.canBeReplacedBy(doubleSlabState, 0, 0), 
            "Double slab should not be replaceable");
        assertFalse(slab.canBeReplacedBy(doubleSlabState, 1, 0), 
            "Double slab should not be replaceable");
    }
    
    @Test
    public void testCannotReplaceSameHalf() {
        SlabBlock slab = new SlabBlock();
        
        BlockState bottomSlabState = new BlockState();
        bottomSlabState.setValue("type", SlabType.BOTTOM);
        
        // Clicking top face of a bottom slab should NOT allow combining
        // (would try to place another bottom half)
        assertFalse(slab.canBeReplacedBy(bottomSlabState, 1, 0.25f), 
            "Bottom slab should not be replaceable when clicking would place another bottom half");
    }
    
    @Test
    public void testSlabHasCustomRendering() {
        SlabBlock slab = new SlabBlock();
        assertTrue(slab.hasCustomRendering(), "Slabs should use custom rendering");
    }
    
    @Test
    public void testSlabIsNotSolid() {
        SlabBlock slab = new SlabBlock();
        assertFalse(slab.isSolid(), "Slabs should not be fully solid (for rendering purposes)");
    }
    
    @Test
    public void testSlabHasCollisionShape() {
        SlabBlock slab = new SlabBlock();
        assertNotNull(slab.getCollisionShape(), "Slab should have a collision shape");
    }
    
    @Test
    public void testCanBeReplacedWithStringType() {
        SlabBlock slab = new SlabBlock();
        
        // Test with string type value (as might come from NBT)
        BlockState bottomSlabState = new BlockState();
        bottomSlabState.setValue("type", "BOTTOM");
        
        // Should still work with string types
        assertTrue(slab.canBeReplacedBy(bottomSlabState, 0, 0.75f), 
            "Should handle string slab type from NBT");
    }
}
