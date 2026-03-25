package net.vulkanic;

import java.util.Objects;

/**
 * Backend-neutral wrapper for a resolved shader uniform binding slot/location.
 */
public final class VulkanicUniformLocation {
    public static final VulkanicUniformLocation INVALID = new VulkanicUniformLocation(-1);

    private final int value;

    private VulkanicUniformLocation(int value) {
        this.value = value;
    }

    public static VulkanicUniformLocation of(int value) {
        if (value == -1) {
            return INVALID;
        }

        return new VulkanicUniformLocation(value);
    }

    public int value() {
        return value;
    }

    public boolean isValid() {
        return value >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VulkanicUniformLocation other)) {
            return false;
        }

        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "VulkanicUniformLocation{" + "value=" + value + '}';
    }
}
