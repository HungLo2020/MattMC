package mattmc.world.level.block.state.properties;

import mattmc.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the type-safe property system.
 * Validates Property interface implementations.
 * 
 * Note: The BlockState class uses a string-based API for setValue/getValue.
 * The type-safe Property classes are available for property metadata and validation,
 * but BlockState integration uses property names as strings.
 */
@DisplayName("Property System Tests")
public class PropertyTest {
    
    // ==================== EnumProperty Tests ====================
    
    @Test
    @DisplayName("EnumProperty should enumerate all enum values")
    void testEnumPropertyAllValues() {
        EnumProperty<Direction> facing = EnumProperty.create("facing", Direction.class);
        
        assertEquals("facing", facing.getName());
        assertEquals(Direction.class, facing.getValueClass());
        assertEquals(4, facing.getValueCount());
        assertTrue(facing.getPossibleValues().contains(Direction.NORTH));
        assertTrue(facing.getPossibleValues().contains(Direction.SOUTH));
        assertTrue(facing.getPossibleValues().contains(Direction.EAST));
        assertTrue(facing.getPossibleValues().contains(Direction.WEST));
    }
    
    @Test
    @DisplayName("EnumProperty should parse string values")
    void testEnumPropertyParsing() {
        EnumProperty<Direction> facing = EnumProperty.create("facing", Direction.class);
        
        assertEquals(Optional.of(Direction.NORTH), facing.parseValue("NORTH"));
        assertEquals(Optional.of(Direction.NORTH), facing.parseValue("north"));
        assertEquals(Optional.of(Direction.SOUTH), facing.parseValue("SOUTH"));
        assertEquals(Optional.empty(), facing.parseValue("invalid"));
        assertEquals(Optional.empty(), facing.parseValue(null));
    }
    
    @Test
    @DisplayName("EnumProperty should serialize values to lowercase")
    void testEnumPropertySerialization() {
        EnumProperty<Direction> facing = EnumProperty.create("facing", Direction.class);
        
        assertEquals("north", facing.getName(Direction.NORTH));
        assertEquals("south", facing.getName(Direction.SOUTH));
        assertEquals("east", facing.getName(Direction.EAST));
        assertEquals("west", facing.getName(Direction.WEST));
    }
    
    @Test
    @DisplayName("EnumProperty should validate values")
    void testEnumPropertyValidation() {
        EnumProperty<Direction> facing = EnumProperty.create("facing", Direction.class);
        
        assertTrue(facing.isValidValue(Direction.NORTH));
        assertTrue(facing.isValidValue(Direction.SOUTH));
        // All enum values should be valid for a full enum property
    }
    
    @Test
    @DisplayName("EnumProperty should support subset of values")
    void testEnumPropertySubset() {
        EnumProperty<Direction> eastWest = EnumProperty.create("facing", Direction.class, 
            Direction.EAST, Direction.WEST);
        
        assertEquals(2, eastWest.getValueCount());
        assertTrue(eastWest.isValidValue(Direction.EAST));
        assertTrue(eastWest.isValidValue(Direction.WEST));
        assertFalse(eastWest.isValidValue(Direction.NORTH));
        assertFalse(eastWest.isValidValue(Direction.SOUTH));
    }
    
    // ==================== IntegerProperty Tests ====================
    
    @Test
    @DisplayName("IntegerProperty should define valid range")
    void testIntegerPropertyRange() {
        IntegerProperty power = IntegerProperty.create("power", 0, 15);
        
        assertEquals("power", power.getName());
        assertEquals(Integer.class, power.getValueClass());
        assertEquals(16, power.getValueCount());
        assertEquals(0, power.getMin());
        assertEquals(15, power.getMax());
        assertEquals(0, power.getDefaultValue());
    }
    
    @Test
    @DisplayName("IntegerProperty should validate range")
    void testIntegerPropertyValidation() {
        IntegerProperty power = IntegerProperty.create("power", 0, 15);
        
        assertTrue(power.isValidValue(0));
        assertTrue(power.isValidValue(15));
        assertTrue(power.isValidValue(8));
        assertFalse(power.isValidValue(-1));
        assertFalse(power.isValidValue(16));
    }
    
    @Test
    @DisplayName("IntegerProperty should parse string values")
    void testIntegerPropertyParsing() {
        IntegerProperty power = IntegerProperty.create("power", 0, 15);
        
        assertEquals(Optional.of(0), power.parseValue("0"));
        assertEquals(Optional.of(15), power.parseValue("15"));
        assertEquals(Optional.of(8), power.parseValue("8"));
        assertEquals(Optional.empty(), power.parseValue("-1"));
        assertEquals(Optional.empty(), power.parseValue("16"));
        assertEquals(Optional.empty(), power.parseValue("abc"));
        assertEquals(Optional.empty(), power.parseValue(null));
    }
    
    // ==================== BooleanProperty Tests ====================
    
    @Test
    @DisplayName("BooleanProperty should have exactly two values")
    void testBooleanPropertyValues() {
        BooleanProperty waterlogged = BooleanProperty.create("waterlogged");
        
        assertEquals("waterlogged", waterlogged.getName());
        assertEquals(Boolean.class, waterlogged.getValueClass());
        assertEquals(2, waterlogged.getValueCount());
        assertTrue(waterlogged.getPossibleValues().contains(true));
        assertTrue(waterlogged.getPossibleValues().contains(false));
        assertEquals(false, waterlogged.getDefaultValue());
    }
    
    @Test
    @DisplayName("BooleanProperty should parse string values")
    void testBooleanPropertyParsing() {
        BooleanProperty lit = BooleanProperty.create("lit");
        
        assertEquals(Optional.of(true), lit.parseValue("true"));
        assertEquals(Optional.of(true), lit.parseValue("TRUE"));
        assertEquals(Optional.of(false), lit.parseValue("false"));
        assertEquals(Optional.of(false), lit.parseValue("FALSE"));
        assertEquals(Optional.empty(), lit.parseValue("yes"));
        assertEquals(Optional.empty(), lit.parseValue("1"));
        assertEquals(Optional.empty(), lit.parseValue(null));
    }
    
    // ==================== BlockStateProperties Tests ====================
    
    @Test
    @DisplayName("BlockStateProperties should provide common properties")
    void testBlockStateProperties() {
        assertNotNull(BlockStateProperties.HORIZONTAL_FACING);
        assertNotNull(BlockStateProperties.HALF);
        assertNotNull(BlockStateProperties.STAIRS_SHAPE);
        assertNotNull(BlockStateProperties.AXIS);
        assertNotNull(BlockStateProperties.WATERLOGGED);
        assertNotNull(BlockStateProperties.POWER);
        
        assertEquals("facing", BlockStateProperties.HORIZONTAL_FACING.getName());
        assertEquals("half", BlockStateProperties.HALF.getName());
        assertEquals("shape", BlockStateProperties.STAIRS_SHAPE.getName());
        assertEquals("axis", BlockStateProperties.AXIS.getName());
        assertEquals("waterlogged", BlockStateProperties.WATERLOGGED.getName());
        assertEquals("power", BlockStateProperties.POWER.getName());
    }
    
    // ==================== BlockState String-Based API Tests ====================
    
    @Test
    @DisplayName("BlockState should support string-based setValue/getValue")
    void testBlockStateStringBasedAPI() {
        BlockState state = new BlockState();
        
        // Use string keys with enum values
        state.setValue("facing", Direction.NORTH);
        state.setValue("half", Half.BOTTOM);
        state.setValue("waterlogged", true);
        
        assertEquals(Direction.NORTH, state.getDirection("facing"));
        assertEquals(Half.BOTTOM, state.getHalf("half"));
        assertEquals(true, state.getValue("waterlogged"));
    }
    
    @Test
    @DisplayName("BlockState should return defaults for unset direction/half properties")
    void testBlockStateDefaults() {
        BlockState state = new BlockState();
        
        // Unset properties should return their default values
        assertEquals(Direction.NORTH, state.getDirection("facing"));
        assertEquals(Half.BOTTOM, state.getHalf("half"));
    }
    
    @Test
    @DisplayName("BlockState should support method chaining")
    void testBlockStateChaining() {
        BlockState state = new BlockState()
            .setValue("facing", Direction.EAST)
            .setValue("half", Half.TOP)
            .setValue("shape", StairsShape.STRAIGHT);
        
        assertEquals(Direction.EAST, state.getDirection("facing"));
        assertEquals(Half.TOP, state.getHalf("half"));
    }
    
    @Test
    @DisplayName("BlockState should generate correct variant string")
    void testBlockStateVariantString() {
        BlockState state = new BlockState()
            .setValue("facing", Direction.SOUTH)
            .setValue("half", Half.TOP);
        
        String variant = state.toVariantString();
        // Properties should be sorted alphabetically
        assertTrue(variant.contains("facing=south"));
        assertTrue(variant.contains("half=top"));
        assertEquals("facing=south,half=top", variant);
    }
    
    @Test
    @DisplayName("BlockState hasProperty should work with string keys")
    void testBlockStateHasProperty() {
        BlockState state = new BlockState()
            .setValue("facing", Direction.NORTH);
        
        assertTrue(state.hasProperty("facing"));
        assertFalse(state.hasProperty("half"));
        assertFalse(state.hasProperty("waterlogged"));
    }
    
    @Test
    @DisplayName("BlockState copy should create independent copy")
    void testBlockStateCopy() {
        BlockState original = new BlockState()
            .setValue("facing", Direction.NORTH);
        
        BlockState copy = original.copy();
        copy.setValue("facing", Direction.SOUTH);
        
        assertEquals(Direction.NORTH, original.getDirection("facing"));
        assertEquals(Direction.SOUTH, copy.getDirection("facing"));
    }
    
    @Test
    @DisplayName("BlockState should serialize to and from NBT")
    void testBlockStateNBT() {
        BlockState original = new BlockState()
            .setValue("facing", Direction.EAST)
            .setValue("half", Half.TOP);
        
        var nbt = original.toNBT();
        BlockState restored = BlockState.fromNBT(nbt);
        
        assertEquals(Direction.EAST, restored.getDirection("facing"));
        assertEquals(Half.TOP, restored.getHalf("half"));
    }
    
    @Test
    @DisplayName("BlockState getValue should handle string values from NBT conversion")
    void testBlockStateStringValueConversion() {
        // Simulate loading a blockstate from NBT where values are strings
        java.util.Map<String, Object> nbtData = new java.util.HashMap<>();
        nbtData.put("facing", "EAST");  // String, not Direction enum
        nbtData.put("half", "TOP");     // String, not Half enum
        
        BlockState state = BlockState.fromNBT(nbtData);
        
        // getValue should properly convert string values to enums
        assertEquals(Direction.EAST, state.getDirection("facing"));
        assertEquals(Half.TOP, state.getHalf("half"));
    }
}
