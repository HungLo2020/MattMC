package mattmc.world.level.block.state.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Property implementation for integer values within a range.
 * Similar to Minecraft's IntegerProperty.
 * 
 * Useful for properties like:
 * - age (crop growth stages)
 * - power (redstone signal strength 0-15)
 * - moisture (farmland 0-7)
 * - level (cauldron 0-3)
 */
public class IntegerProperty implements Property<Integer> {
    
    private final String name;
    private final int min;
    private final int max;
    private final List<Integer> possibleValues;
    private final int defaultValue;
    
    /**
     * Create an integer property with a range.
     * Default value is the minimum.
     * 
     * @param name The property name
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     */
    public IntegerProperty(String name, int min, int max) {
        this(name, min, max, min);
    }
    
    /**
     * Create an integer property with a range and specific default.
     * 
     * @param name The property name
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @param defaultValue The default value (must be in range)
     */
    public IntegerProperty(String name, int min, int max, int defaultValue) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty");
        }
        if (min > max) {
            throw new IllegalArgumentException("Min cannot be greater than max");
        }
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Default value must be in range [" + min + ", " + max + "]");
        }
        
        this.name = name;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        
        // Pre-compute all possible values
        List<Integer> values = new ArrayList<>(max - min + 1);
        for (int i = min; i <= max; i++) {
            values.add(i);
        }
        this.possibleValues = Collections.unmodifiableList(values);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Collection<Integer> getPossibleValues() {
        return possibleValues;
    }
    
    @Override
    public Class<Integer> getValueClass() {
        return Integer.class;
    }
    
    @Override
    public Integer getDefaultValue() {
        return defaultValue;
    }
    
    @Override
    public Optional<Integer> parseValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        
        try {
            int intValue = Integer.parseInt(value);
            if (intValue >= min && intValue <= max) {
                return Optional.of(intValue);
            }
        } catch (NumberFormatException ignored) {
        }
        
        return Optional.empty();
    }
    
    @Override
    public String getName(Integer value) {
        return value.toString();
    }
    
    /**
     * Get the minimum value.
     */
    public int getMin() {
        return min;
    }
    
    /**
     * Get the maximum value.
     */
    public int getMax() {
        return max;
    }
    
    @Override
    public String toString() {
        return "IntegerProperty[" + name + ", " + min + "-" + max + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IntegerProperty)) return false;
        IntegerProperty other = (IntegerProperty) obj;
        return name.equals(other.name) && min == other.min && max == other.max;
    }
    
    @Override
    public int hashCode() {
        return name.hashCode() * 31 + min * 17 + max;
    }
    
    // Factory methods for convenience
    
    /**
     * Create an integer property with a range [min, max].
     */
    public static IntegerProperty create(String name, int min, int max) {
        return new IntegerProperty(name, min, max);
    }
    
    /**
     * Create an integer property with a range [0, max].
     */
    public static IntegerProperty create(String name, int max) {
        return new IntegerProperty(name, 0, max);
    }
}
