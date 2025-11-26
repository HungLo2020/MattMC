package mattmc.world.level.block.state.properties;

import mattmc.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the type-safe property system.
 * Validates Property interface implementations and BlockState type-safe API.
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
    
    // ==================== BlockState Type-Safe API Tests ====================
    
    @Test
    @DisplayName("BlockState should support type-safe setValue/getValue")
    void testBlockStateTypeSafeAPI() {
        BlockState state = new BlockState();
        
        state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        state.setValue(BlockStateProperties.HALF, Half.BOTTOM);
        state.setValue(BlockStateProperties.WATERLOGGED, true);
        
        assertEquals(Direction.NORTH, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(Half.BOTTOM, state.getValue(BlockStateProperties.HALF));
        assertEquals(true, state.getValue(BlockStateProperties.WATERLOGGED));
    }
    
    @Test
    @DisplayName("BlockState should return default for unset properties")
    void testBlockStateDefaults() {
        BlockState state = new BlockState();
        
        // Unset properties should return their default values
        assertEquals(Direction.NORTH, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(Half.BOTTOM, state.getValue(BlockStateProperties.HALF));
        assertEquals(false, state.getValue(BlockStateProperties.WATERLOGGED));
        assertEquals(0, state.getValue(BlockStateProperties.POWER));
    }
    
    @Test
    @DisplayName("BlockState should reject invalid property values")
    void testBlockStateValidation() {
        BlockState state = new BlockState();
        
        // Should throw for invalid value
        IntegerProperty power = BlockStateProperties.POWER;
        assertThrows(IllegalArgumentException.class, () -> {
            state.setValue(power, 20);  // Max is 15
        });
        assertThrows(IllegalArgumentException.class, () -> {
            state.setValue(power, -1);  // Min is 0
        });
    }
    
    @Test
    @DisplayName("BlockState should support method chaining")
    void testBlockStateChaining() {
        BlockState state = new BlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            .setValue(BlockStateProperties.HALF, Half.TOP)
            .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
        
        assertEquals(Direction.EAST, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(Half.TOP, state.getValue(BlockStateProperties.HALF));
        assertEquals(StairsShape.STRAIGHT, state.getValue(BlockStateProperties.STAIRS_SHAPE));
    }
    
    @Test
    @DisplayName("BlockState should generate correct variant string")
    void testBlockStateVariantString() {
        BlockState state = new BlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            .setValue(BlockStateProperties.HALF, Half.TOP);
        
        String variant = state.toVariantString();
        // Properties should be sorted alphabetically
        assertTrue(variant.contains("facing=south"));
        assertTrue(variant.contains("half=top"));
        assertEquals("facing=south,half=top", variant);
    }
    
    @Test
    @DisplayName("BlockState hasProperty should work with typed properties")
    void testBlockStateHasProperty() {
        BlockState state = new BlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        
        assertTrue(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING));
        assertFalse(state.hasProperty(BlockStateProperties.HALF));
        assertFalse(state.hasProperty(BlockStateProperties.WATERLOGGED));
    }
    
    @Test
    @DisplayName("BlockState copy should create independent copy")
    void testBlockStateCopy() {
        BlockState original = new BlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        
        BlockState copy = original.copy();
        copy.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        
        assertEquals(Direction.NORTH, original.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(Direction.SOUTH, copy.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }
    
    @Test
    @DisplayName("BlockState should serialize to and from NBT")
    void testBlockStateNBT() {
        BlockState original = new BlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            .setValue(BlockStateProperties.HALF, Half.TOP);
        
        var nbt = original.toNBT();
        BlockState restored = BlockState.fromNBT(nbt);
        
        assertEquals(Direction.EAST, restored.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(Half.TOP, restored.getValue(BlockStateProperties.HALF));
    }
}
