package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral data type for vertex attribute declarations.
 */
public enum VulkanicVertexAttributeType {
    BYTE,
    UNSIGNED_BYTE,
    SHORT,
    UNSIGNED_SHORT,
    INT,
    UNSIGNED_INT,
    HALF_FLOAT,
    FLOAT,
    DOUBLE;

    /**
     * Converts this typed attribute descriptor to its legacy GL constant.
     */
    public int toLegacyGlConstant() {
        return switch (this) {
            case BYTE -> VulkanicAPI.GL_BYTE;
            case UNSIGNED_BYTE -> VulkanicAPI.GL_UNSIGNED_BYTE;
            case SHORT -> VulkanicAPI.GL_SHORT;
            case UNSIGNED_SHORT -> VulkanicAPI.GL_UNSIGNED_SHORT;
            case INT -> VulkanicAPI.GL_INT;
            case UNSIGNED_INT -> VulkanicAPI.GL_UNSIGNED_INT;
            case HALF_FLOAT -> VulkanicAPI.GL_HALF_FLOAT;
            case FLOAT -> VulkanicAPI.GL_FLOAT;
            case DOUBLE -> VulkanicAPI.GL_DOUBLE;
        };
    }

    /**
     * Converts a legacy GL vertex attribute type constant to a typed descriptor when known.
     */
    public static Optional<VulkanicVertexAttributeType> fromLegacyGlConstant(int glConstant) {
        return switch (glConstant) {
            case VulkanicAPI.GL_BYTE -> Optional.of(BYTE);
            case VulkanicAPI.GL_UNSIGNED_BYTE -> Optional.of(UNSIGNED_BYTE);
            case VulkanicAPI.GL_SHORT -> Optional.of(SHORT);
            case VulkanicAPI.GL_UNSIGNED_SHORT -> Optional.of(UNSIGNED_SHORT);
            case VulkanicAPI.GL_INT -> Optional.of(INT);
            case VulkanicAPI.GL_UNSIGNED_INT -> Optional.of(UNSIGNED_INT);
            case VulkanicAPI.GL_HALF_FLOAT -> Optional.of(HALF_FLOAT);
            case VulkanicAPI.GL_FLOAT -> Optional.of(FLOAT);
            case VulkanicAPI.GL_DOUBLE -> Optional.of(DOUBLE);
            default -> Optional.empty();
        };
    }
}