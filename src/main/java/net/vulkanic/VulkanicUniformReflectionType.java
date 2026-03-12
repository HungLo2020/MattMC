package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral descriptor for reflected active uniform GL types.
 */
public enum VulkanicUniformReflectionType {
    FLOAT(VulkanicAPI.GL_FLOAT, "float", false, false),
    INT(VulkanicAPI.GL_INT, "int", false, false),
    BOOL(VulkanicAPI.GL_BOOL, "bool", false, false),
    FLOAT_MAT4(VulkanicAPI.GL_FLOAT_MAT4, "mat4", false, false),
    FLOAT_VEC4(VulkanicAPI.GL_FLOAT_VEC4, "vec4", false, false),
    INT_VEC4(VulkanicAPI.GL_INT_VEC4, "ivec4", false, false),
    FLOAT_MAT3(VulkanicAPI.GL_FLOAT_MAT3, "mat3", false, false),
    FLOAT_VEC3(VulkanicAPI.GL_FLOAT_VEC3, "vec3", false, false),
    INT_VEC3(VulkanicAPI.GL_INT_VEC3, "ivec3", false, false),
    FLOAT_MAT2(VulkanicAPI.GL_FLOAT_MAT2, "mat2", false, false),
    FLOAT_VEC2(VulkanicAPI.GL_FLOAT_VEC2, "vec2", false, false),
    INT_VEC2(VulkanicAPI.GL_INT_VEC2, "ivec2", false, false),
    SAMPLER_1D(VulkanicAPI.GL_SAMPLER_1D, "sampler1D", true, false),
    SAMPLER_2D(VulkanicAPI.GL_SAMPLER_2D, "sampler2D", true, false),
    SAMPLER_3D(VulkanicAPI.GL_SAMPLER_3D, "sampler3D", true, false),
    SAMPLER_1D_SHADOW(VulkanicAPI.GL_SAMPLER_1D_SHADOW, "sampler1DShadow", true, false),
    SAMPLER_2D_SHADOW(VulkanicAPI.GL_SAMPLER_2D_SHADOW, "sampler2DShadow", true, false),
    UNSIGNED_INT_SAMPLER_2D(VulkanicAPI.GL_UNSIGNED_INT_SAMPLER_2D, "usampler2D", true, false),
    UNSIGNED_INT_SAMPLER_3D(VulkanicAPI.GL_UNSIGNED_INT_SAMPLER_3D, "usampler3D", true, false),
    IMAGE_1D(VulkanicAPI.GL_IMAGE_1D, "image1D", false, true),
    IMAGE_2D(VulkanicAPI.GL_IMAGE_2D, "image2D", false, true),
    IMAGE_3D(VulkanicAPI.GL_IMAGE_3D, "image3D", false, true),
    IMAGE_1D_ARRAY(VulkanicAPI.GL_IMAGE_1D_ARRAY, "image1DArray", false, true),
    IMAGE_2D_ARRAY(VulkanicAPI.GL_IMAGE_2D_ARRAY, "image2DArray", false, true),
    INT_IMAGE_1D(VulkanicAPI.GL_INT_IMAGE_1D, "iimage1D", false, true),
    INT_IMAGE_2D(VulkanicAPI.GL_INT_IMAGE_2D, "iimage2D", false, true),
    INT_IMAGE_3D(VulkanicAPI.GL_INT_IMAGE_3D, "iimage3D", false, true),
    UNSIGNED_INT_IMAGE_1D(VulkanicAPI.GL_UNSIGNED_INT_IMAGE_1D, "uimage1D", false, true),
    UNSIGNED_INT_IMAGE_2D(VulkanicAPI.GL_UNSIGNED_INT_IMAGE_2D, "uimage2D", false, true),
    UNSIGNED_INT_IMAGE_3D(VulkanicAPI.GL_UNSIGNED_INT_IMAGE_3D, "uimage3D", false, true);

    private final int legacyGlConstant;
    private final String glslTypeName;
    private final boolean sampler;
    private final boolean image;

    VulkanicUniformReflectionType(int legacyGlConstant, String glslTypeName, boolean sampler, boolean image) {
        this.legacyGlConstant = legacyGlConstant;
        this.glslTypeName = glslTypeName;
        this.sampler = sampler;
        this.image = image;
    }

    /**
     * Converts this typed uniform reflection type to its legacy GL constant.
     */
    public int toLegacyGlConstant() {
        return legacyGlConstant;
    }

    /**
     * Returns the GLSL-style name used for logging this reflection type.
     */
    public String getGlslTypeName() {
        return glslTypeName;
    }

    /**
     * Returns whether this reflected type is a sampler uniform.
     */
    public boolean isSampler() {
        return sampler;
    }

    /**
     * Returns whether this reflected type is an image uniform.
     */
    public boolean isImage() {
        return image;
    }

    /**
     * Converts a legacy GL active-uniform type to a typed descriptor when known.
     */
    public static Optional<VulkanicUniformReflectionType> fromLegacyGlConstant(int glConstant) {
        return switch (glConstant) {
            case VulkanicAPI.GL_FLOAT -> Optional.of(FLOAT);
            case VulkanicAPI.GL_INT -> Optional.of(INT);
            case VulkanicAPI.GL_BOOL -> Optional.of(BOOL);
            case VulkanicAPI.GL_FLOAT_MAT4 -> Optional.of(FLOAT_MAT4);
            case VulkanicAPI.GL_FLOAT_VEC4 -> Optional.of(FLOAT_VEC4);
            case VulkanicAPI.GL_INT_VEC4 -> Optional.of(INT_VEC4);
            case VulkanicAPI.GL_FLOAT_MAT3 -> Optional.of(FLOAT_MAT3);
            case VulkanicAPI.GL_FLOAT_VEC3 -> Optional.of(FLOAT_VEC3);
            case VulkanicAPI.GL_INT_VEC3 -> Optional.of(INT_VEC3);
            case VulkanicAPI.GL_FLOAT_MAT2 -> Optional.of(FLOAT_MAT2);
            case VulkanicAPI.GL_FLOAT_VEC2 -> Optional.of(FLOAT_VEC2);
            case VulkanicAPI.GL_INT_VEC2 -> Optional.of(INT_VEC2);
            case VulkanicAPI.GL_SAMPLER_1D -> Optional.of(SAMPLER_1D);
            case VulkanicAPI.GL_SAMPLER_2D -> Optional.of(SAMPLER_2D);
            case VulkanicAPI.GL_SAMPLER_3D -> Optional.of(SAMPLER_3D);
            case VulkanicAPI.GL_SAMPLER_1D_SHADOW -> Optional.of(SAMPLER_1D_SHADOW);
            case VulkanicAPI.GL_SAMPLER_2D_SHADOW -> Optional.of(SAMPLER_2D_SHADOW);
            case VulkanicAPI.GL_UNSIGNED_INT_SAMPLER_2D -> Optional.of(UNSIGNED_INT_SAMPLER_2D);
            case VulkanicAPI.GL_UNSIGNED_INT_SAMPLER_3D -> Optional.of(UNSIGNED_INT_SAMPLER_3D);
            case VulkanicAPI.GL_IMAGE_1D -> Optional.of(IMAGE_1D);
            case VulkanicAPI.GL_IMAGE_2D -> Optional.of(IMAGE_2D);
            case VulkanicAPI.GL_IMAGE_3D -> Optional.of(IMAGE_3D);
            case VulkanicAPI.GL_IMAGE_1D_ARRAY -> Optional.of(IMAGE_1D_ARRAY);
            case VulkanicAPI.GL_IMAGE_2D_ARRAY -> Optional.of(IMAGE_2D_ARRAY);
            case VulkanicAPI.GL_INT_IMAGE_1D -> Optional.of(INT_IMAGE_1D);
            case VulkanicAPI.GL_INT_IMAGE_2D -> Optional.of(INT_IMAGE_2D);
            case VulkanicAPI.GL_INT_IMAGE_3D -> Optional.of(INT_IMAGE_3D);
            case VulkanicAPI.GL_UNSIGNED_INT_IMAGE_1D -> Optional.of(UNSIGNED_INT_IMAGE_1D);
            case VulkanicAPI.GL_UNSIGNED_INT_IMAGE_2D -> Optional.of(UNSIGNED_INT_IMAGE_2D);
            case VulkanicAPI.GL_UNSIGNED_INT_IMAGE_3D -> Optional.of(UNSIGNED_INT_IMAGE_3D);
            default -> Optional.empty();
        };
    }
}