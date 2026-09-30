package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral texture binding target for Vulkanic frontend APIs.
 */
public enum VulkanicTextureTarget {
    TEXTURE_2D,
    TEXTURE_3D,
    TEXTURE_BUFFER,
    TEXTURE_CUBE_MAP,
    TEXTURE_RECTANGLE;

    /**
     * Converts this typed target into its legacy GL target constant.
     */
    public int toLegacyGlTarget() {
        return switch (this) {
            case TEXTURE_2D -> VulkanicAPI.GL_TEXTURE_2D;
            case TEXTURE_3D -> VulkanicAPI.GL_TEXTURE_3D;
            case TEXTURE_BUFFER -> VulkanicAPI.GL_TEXTURE_BUFFER;
            case TEXTURE_CUBE_MAP -> VulkanicAPI.GL_TEXTURE_CUBE_MAP;
            case TEXTURE_RECTANGLE -> VulkanicAPI.GL_TEXTURE_RECTANGLE;
        };
    }

}