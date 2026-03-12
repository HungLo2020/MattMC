package net.vulkanic;

import java.util.Objects;

/**
 * Backend-neutral wrapper for a linked shader program object handle.
 */
public final class VulkanicProgramHandle {
    public static final VulkanicProgramHandle INVALID = new VulkanicProgramHandle(0);

    private final int value;

    private VulkanicProgramHandle(int value) {
        this.value = value;
    }

    public static VulkanicProgramHandle of(int value) {
        if (value == 0) {
            return INVALID;
        }

        return new VulkanicProgramHandle(value);
    }

    public int value() {
        return value;
    }

    public boolean isValid() {
        return value != 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VulkanicProgramHandle other)) {
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
        return "VulkanicProgramHandle{" + "value=" + value + '}';
    }
}
