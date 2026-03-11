package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral program parameter key used by program status and reflection queries.
 */
public enum VulkanicProgramParameterName {
    LINK_STATUS,
    ACTIVE_UNIFORMS,
    COMPUTE_WORK_GROUP_SIZE;

    public int toLegacyGlPName() {
        return switch (this) {
            case LINK_STATUS -> VulkanicAPI.GL_LINK_STATUS;
            case ACTIVE_UNIFORMS -> VulkanicAPI.GL_ACTIVE_UNIFORMS;
            case COMPUTE_WORK_GROUP_SIZE -> VulkanicAPI.GL_COMPUTE_WORK_GROUP_SIZE;
        };
    }

    public static Optional<VulkanicProgramParameterName> fromLegacyGlPName(int pname) {
        return switch (pname) {
            case VulkanicAPI.GL_LINK_STATUS -> Optional.of(LINK_STATUS);
            case VulkanicAPI.GL_ACTIVE_UNIFORMS -> Optional.of(ACTIVE_UNIFORMS);
            case VulkanicAPI.GL_COMPUTE_WORK_GROUP_SIZE -> Optional.of(COMPUTE_WORK_GROUP_SIZE);
            default -> Optional.empty();
        };
    }
}