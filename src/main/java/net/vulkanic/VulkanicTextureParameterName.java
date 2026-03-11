package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral texture parameter key for Vulkanic frontend APIs.
 */
public enum VulkanicTextureParameterName {
    MIN_FILTER,
    MAG_FILTER,
    WRAP_S,
    WRAP_T,
    WRAP_R,
    MIN_LOD,
    MAX_LOD,
    LOD_BIAS,
    BASE_LEVEL,
    MAX_LEVEL,
    COMPARE_MODE,
    SWIZZLE_RGBA;

    /**
     * Converts this typed texture parameter key to its legacy GL pname constant.
     */
    public int toLegacyGlPName() {
        return switch (this) {
            case MIN_FILTER -> VulkanicAPI.GL_TEXTURE_MIN_FILTER;
            case MAG_FILTER -> VulkanicAPI.GL_TEXTURE_MAG_FILTER;
            case WRAP_S -> VulkanicAPI.GL_TEXTURE_WRAP_S;
            case WRAP_T -> VulkanicAPI.GL_TEXTURE_WRAP_T;
            case WRAP_R -> VulkanicAPI.GL_TEXTURE_WRAP_R;
            case MIN_LOD -> VulkanicAPI.GL_TEXTURE_MIN_LOD;
            case MAX_LOD -> VulkanicAPI.GL_TEXTURE_MAX_LOD;
            case LOD_BIAS -> VulkanicAPI.GL_TEXTURE_LOD_BIAS;
            case BASE_LEVEL -> VulkanicAPI.GL_TEXTURE_BASE_LEVEL;
            case MAX_LEVEL -> VulkanicAPI.GL_TEXTURE_MAX_LEVEL;
            case COMPARE_MODE -> VulkanicAPI.GL_TEXTURE_COMPARE_MODE;
            case SWIZZLE_RGBA -> VulkanicAPI.GL_TEXTURE_SWIZZLE_RGBA;
        };
    }

    /**
     * Converts a legacy GL texture parameter pname constant into a typed key when known.
     */
    public static Optional<VulkanicTextureParameterName> fromLegacyGlPName(int pname) {
        return switch (pname) {
            case VulkanicAPI.GL_TEXTURE_MIN_FILTER -> Optional.of(MIN_FILTER);
            case VulkanicAPI.GL_TEXTURE_MAG_FILTER -> Optional.of(MAG_FILTER);
            case VulkanicAPI.GL_TEXTURE_WRAP_S -> Optional.of(WRAP_S);
            case VulkanicAPI.GL_TEXTURE_WRAP_T -> Optional.of(WRAP_T);
            case VulkanicAPI.GL_TEXTURE_WRAP_R -> Optional.of(WRAP_R);
            case VulkanicAPI.GL_TEXTURE_MIN_LOD -> Optional.of(MIN_LOD);
            case VulkanicAPI.GL_TEXTURE_MAX_LOD -> Optional.of(MAX_LOD);
            case VulkanicAPI.GL_TEXTURE_LOD_BIAS -> Optional.of(LOD_BIAS);
            case VulkanicAPI.GL_TEXTURE_BASE_LEVEL -> Optional.of(BASE_LEVEL);
            case VulkanicAPI.GL_TEXTURE_MAX_LEVEL -> Optional.of(MAX_LEVEL);
            case VulkanicAPI.GL_TEXTURE_COMPARE_MODE -> Optional.of(COMPARE_MODE);
            case VulkanicAPI.GL_TEXTURE_SWIZZLE_RGBA -> Optional.of(SWIZZLE_RGBA);
            default -> Optional.empty();
        };
    }
}