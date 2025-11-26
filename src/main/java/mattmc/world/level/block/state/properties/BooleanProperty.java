package mattmc.world.level.block.state.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Property implementation for boolean values.
 * Similar to Minecraft's BooleanProperty.
 * 
 * Useful for properties like:
 * - waterlogged (true/false)
 * - lit (furnace on/off)
 * - powered (lever, button)
 * - open (door, trapdoor)
 * - attached (tripwire)
 */
public class BooleanProperty implements Property<Boolean> {
    
    private static final List<Boolean> POSSIBLE_VALUES = Arrays.asList(Boolean.FALSE, Boolean.TRUE);
    
    private final String name;
    private final boolean defaultValue;
    
    /**
     * Create a boolean property with false as default.
     * 
     * @param name The property name
     */
    public BooleanProperty(String name) {
        this(name, false);
    }
    
    /**
     * Create a boolean property with a specific default.
     * 
     * @param name The property name
     * @param defaultValue The default value
     */
    public BooleanProperty(String name, boolean defaultValue) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty");
        }
        
        this.name = name;
        this.defaultValue = defaultValue;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Collection<Boolean> getPossibleValues() {
        return POSSIBLE_VALUES;
    }
    
    @Override
    public Class<Boolean> getValueClass() {
        return Boolean.class;
    }
    
    @Override
    public Boolean getDefaultValue() {
        return defaultValue;
    }
    
    @Override
    public Optional<Boolean> parseValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        
        if ("true".equalsIgnoreCase(value)) {
            return Optional.of(Boolean.TRUE);
        }
        if ("false".equalsIgnoreCase(value)) {
            return Optional.of(Boolean.FALSE);
        }
        
        return Optional.empty();
    }
    
    @Override
    public String getName(Boolean value) {
        return value.toString();
    }
    
    @Override
    public String toString() {
        return "BooleanProperty[" + name + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BooleanProperty)) return false;
        BooleanProperty other = (BooleanProperty) obj;
        return name.equals(other.name);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    // Factory method for convenience
    
    /**
     * Create a boolean property with false as default.
     */
    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }
    
    /**
     * Create a boolean property with a specific default.
     */
    public static BooleanProperty create(String name, boolean defaultValue) {
        return new BooleanProperty(name, defaultValue);
    }
}
