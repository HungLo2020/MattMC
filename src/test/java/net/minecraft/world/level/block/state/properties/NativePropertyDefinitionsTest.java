package net.minecraft.world.level.block.state.properties;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativePropertyDefinitionsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everySharedDeclarationHasDistinctNativeIdentity() throws Exception {
        var ids = new HashSet<Integer>();
        int count = 0;
        for (var field : BlockStateProperties.class.getFields()) {
            if (!Property.class.isAssignableFrom(field.getType())) continue;
            var property = (Property<?>) field.get(null);
            assertTrue(property.nativeDefinitionId() >= 0, field.getName());
            assertTrue(ids.add(property.nativeDefinitionId()), field.getName());
            assertThrows(UnsupportedOperationException.class, () -> property.getPossibleValues().clear());
            count++;
        }
        assertEquals(123, count);
        assertEquals(-1, BooleanProperty.create("custom").nativeDefinitionId());
        assertEquals(-1, IntegerProperty.create("age", 0, 3).nativeDefinitionId());
        assertEquals(-1, EnumProperty.create("facing", Direction.class).nativeDefinitionId());
        assertNotSame(BlockStateProperties.LEVEL_FLOWING, BlockStateProperties.LAYERS);
        for (var block : BuiltInRegistries.BLOCK) {
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                assertTrue(property.nativeDefinitionId() >= 0, BuiltInRegistries.BLOCK.getKey(block) + "/" + property.getName());
            }
        }
    }

    @Test
    void projectionKeepsDirectionOrdersAndParsingBoundaries() {
        assertEquals(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN), BlockStateProperties.FACING.getPossibleValues());
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST), BlockStateProperties.HORIZONTAL_FACING.getPossibleValues());
        assertEquals(List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST), BlockStateProperties.FACING_HOPPER.getPossibleValues());
        assertEquals(List.of(Direction.UP, Direction.DOWN), BlockStateProperties.VERTICAL_DIRECTION.getPossibleValues());
        assertEquals(-1, BlockStateProperties.HORIZONTAL_FACING.getInternalIndex(Direction.UP));
        assertTrue(BlockStateProperties.HORIZONTAL_FACING.getValue("up").isEmpty());
        assertEquals(List.of(true, false), BlockStateProperties.FALLING.getPossibleValues());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), BlockStateProperties.LEVEL_FLOWING.getPossibleValues());
        assertTrue(BlockStateProperties.LEVEL_FLOWING.getValue("0").isEmpty());
        assertTrue(BlockStateProperties.LEVEL_FLOWING.getValue("9").isEmpty());
        assertEquals(15, BlockStateProperties.ROTATION_16.getPossibleValues().getLast());
        assertEquals(RotationSegment.getMaxSegmentIndex(), BlockStateProperties.ROTATION_16.getPossibleValues().getLast());
        assertThrows(IllegalStateException.class, () -> NativePropertyDefinitions.booleanProperty("MISSING"));
        assertThrows(IllegalStateException.class, () -> NativePropertyDefinitions.integerProperty("FALLING"));
        assertThrows(IllegalStateException.class, () -> NativePropertyDefinitions.enumProperty("FACING", Half.class));
    }
}
