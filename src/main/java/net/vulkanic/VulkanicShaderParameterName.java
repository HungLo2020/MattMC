package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral shader parameter key used by shader status queries.
 */
public enum VulkanicShaderParameterName {
    COMPILE_STATUS;

    public int toLegacyGlPName() {
        return switch (this) {
            case COMPILE_STATUS -> VulkanicAPI.GL_COMPILE_STATUS;
        };
    }

    public static Optional<VulkanicShaderParameterName> fromLegacyGlPName(int pname) {
        return switch (pname) {
            case VulkanicAPI.GL_COMPILE_STATUS -> Optional.of(COMPILE_STATUS);
            default -> Optional.empty();
        };
    }
}