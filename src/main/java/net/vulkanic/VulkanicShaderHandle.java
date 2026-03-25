package net.vulkanic;

import java.util.Objects;

/**
 * Backend-neutral wrapper for a compiled shader object handle.
 */
public final class VulkanicShaderHandle {
    public static final VulkanicShaderHandle INVALID = new VulkanicShaderHandle(0);

    private final int value;

    private VulkanicShaderHandle(int value) {
        this.value = value;
    }

    public static VulkanicShaderHandle of(int value) {
        if (value == 0) {
            return INVALID;
        }

        return new VulkanicShaderHandle(value);
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
        if (!(obj instanceof VulkanicShaderHandle other)) {
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
        return "VulkanicShaderHandle{" + "value=" + value + '}';
    }
}
