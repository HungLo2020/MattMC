package mattmc.world.level.block.state.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Property implementation for enum values.
 * Similar to Minecraft's EnumProperty.
 * 
 * @param <T> The enum type
 */
public class EnumProperty<T extends Enum<T> & Comparable<T>> implements Property<T> {
    
    private final String name;
    private final Class<T> valueClass;
    private final List<T> possibleValues;
    private final T defaultValue;
    
    /**
     * Create an enum property with all enum values.
     * 
     * @param name The property name
     * @param valueClass The enum class
     */
    public EnumProperty(String name, Class<T> valueClass) {
        this(name, valueClass, Arrays.asList(valueClass.getEnumConstants()));
    }
    
    /**
     * Create an enum property with a subset of enum values.
     * 
     * @param name The property name
     * @param valueClass The enum class
     * @param allowedValues The allowed values (must be non-empty)
     */
    public EnumProperty(String name, Class<T> valueClass, Collection<T> allowedValues) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty");
        }
        if (valueClass == null) {
            throw new IllegalArgumentException("Value class cannot be null");
        }
        if (allowedValues == null || allowedValues.isEmpty()) {
            throw new IllegalArgumentException("Allowed values cannot be null or empty");
        }
        
        this.name = name;
        this.valueClass = valueClass;
        this.possibleValues = Collections.unmodifiableList(
            allowedValues instanceof List ? (List<T>) allowedValues : List.copyOf(allowedValues)
        );
        this.defaultValue = possibleValues.get(0);
    }
    
    /**
     * Create an enum property with a subset of enum values and a specific default.
     * 
     * @param name The property name
     * @param valueClass The enum class
     * @param allowedValues The allowed values (must be non-empty)
     * @param defaultValue The default value (must be in allowedValues)
     */
    public EnumProperty(String name, Class<T> valueClass, Collection<T> allowedValues, T defaultValue) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty");
        }
        if (valueClass == null) {
            throw new IllegalArgumentException("Value class cannot be null");
        }
        if (allowedValues == null || allowedValues.isEmpty()) {
            throw new IllegalArgumentException("Allowed values cannot be null or empty");
        }
        if (!allowedValues.contains(defaultValue)) {
            throw new IllegalArgumentException("Default value must be in allowed values");
        }
        
        this.name = name;
        this.valueClass = valueClass;
        this.possibleValues = Collections.unmodifiableList(
            allowedValues instanceof List ? (List<T>) allowedValues : List.copyOf(allowedValues)
        );
        this.defaultValue = defaultValue;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Collection<T> getPossibleValues() {
        return possibleValues;
    }
    
    @Override
    public Class<T> getValueClass() {
        return valueClass;
    }
    
    @Override
    public T getDefaultValue() {
        return defaultValue;
    }
    
    @Override
    public Optional<T> parseValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        
        // Try exact match first (case-insensitive)
        String upperValue = value.toUpperCase();
        for (T v : possibleValues) {
            if (v.name().equals(upperValue)) {
                return Optional.of(v);
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public String getName(T value) {
        return value.name().toLowerCase();
    }
    
    @Override
    public String toString() {
        return "EnumProperty[" + name + ", " + valueClass.getSimpleName() + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EnumProperty)) return false;
        EnumProperty<?> other = (EnumProperty<?>) obj;
        return name.equals(other.name) && valueClass.equals(other.valueClass);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode() * 31 + valueClass.hashCode();
    }
    
    // Factory methods for convenience
    
    /**
     * Create an enum property with all values of the enum.
     */
    public static <T extends Enum<T> & Comparable<T>> EnumProperty<T> create(String name, Class<T> valueClass) {
        return new EnumProperty<>(name, valueClass);
    }
    
    /**
     * Create an enum property with specific allowed values.
     */
    @SafeVarargs
    public static <T extends Enum<T> & Comparable<T>> EnumProperty<T> create(String name, Class<T> valueClass, T... allowedValues) {
        return new EnumProperty<>(name, valueClass, Arrays.asList(allowedValues));
    }
}
