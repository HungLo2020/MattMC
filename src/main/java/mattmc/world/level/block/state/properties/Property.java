package mattmc.world.level.block.state.properties;

import java.util.Collection;
import java.util.Optional;

/**
 * Type-safe property interface for block states.
 * Similar to Minecraft's IProperty interface.
 * 
 * Properties define valid values that a blockstate can have.
 * Each property has a name, a set of allowed values, and methods
 * for parsing/serializing values.
 * 
 * @param <T> The type of values this property can hold (must be Comparable for sorting)
 */
public interface Property<T extends Comparable<T>> {
    
    /**
     * Get the name of this property.
     * Used for serialization and variant string generation.
     * 
     * @return The property name (e.g., "facing", "half", "axis")
     */
    String getName();
    
    /**
     * Get all allowed values for this property.
     * 
     * @return Collection of all valid values
     */
    Collection<T> getPossibleValues();
    
    /**
     * Get the class of values this property holds.
     * 
     * @return The value class
     */
    Class<T> getValueClass();
    
    /**
     * Get the default value for this property.
     * Used when a property value is not specified.
     * 
     * @return The default value
     */
    T getDefaultValue();
    
    /**
     * Parse a string value into the property's type.
     * 
     * @param value The string representation of the value
     * @return Optional containing the parsed value, or empty if parsing fails
     */
    Optional<T> parseValue(String value);
    
    /**
     * Convert a value to its string representation.
     * Used for serialization and variant string generation.
     * 
     * @param value The value to convert
     * @return The string representation
     */
    String getName(T value);
    
    /**
     * Check if a value is valid for this property.
     * 
     * @param value The value to check
     * @return true if the value is in the set of allowed values
     */
    default boolean isValidValue(T value) {
        return getPossibleValues().contains(value);
    }
    
    /**
     * Get the number of possible values.
     * Useful for calculating total state combinations.
     * 
     * @return The number of possible values
     */
    default int getValueCount() {
        return getPossibleValues().size();
    }
}
