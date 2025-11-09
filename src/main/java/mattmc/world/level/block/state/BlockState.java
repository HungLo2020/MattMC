package mattmc.world.level.block.state;

import mattmc.world.level.block.state.properties.Direction;
import mattmc.world.level.block.state.properties.Half;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of a block with its properties.
 * Similar to Minecraft's BlockState class.
 * 
 * BlockStates store property values for blocks like stairs (facing, half, shape).
 */
public class BlockState {
    private final Map<String, Object> properties;
    
    public BlockState() {
        this.properties = new HashMap<>();
    }
    
    /**
     * Set a property value.
     */
    public BlockState setValue(String property, Object value) {
        properties.put(property, value);
        return this;
    }
    
    /**
     * Get a property value.
     */
    public Object getValue(String property) {
        return properties.get(property);
    }
    
    /**
     * Get a Direction property.
     */
    public Direction getDirection(String property) {
        Object value = properties.get(property);
        return value instanceof Direction ? (Direction) value : Direction.NORTH;
    }
    
    /**
     * Get a Half property.
     */
    public Half getHalf(String property) {
        Object value = properties.get(property);
        return value instanceof Half ? (Half) value : Half.BOTTOM;
    }
    
    /**
     * Check if this blockstate has a property.
     */
    public boolean hasProperty(String property) {
        return properties.containsKey(property);
    }
    
    /**
     * Create a copy of this blockstate.
     */
    public BlockState copy() {
        BlockState copy = new BlockState();
        copy.properties.putAll(this.properties);
        return copy;
    }
}
