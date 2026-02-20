package net.vulkanic.resources;

/**
 * Vulkanic texture filter mode — controls how texels are sampled when a
 * texture is magnified or minified.
 *
 * <p>This is the Vulkanic equivalent of Blaze3D's {@code FilterMode}.
 * Using this type instead of the Blaze3D enum means the Vulkan backend never
 * needs to touch Blaze3D types when creating a {@link VulkanicSampler}.
 *
 * <p>Vulkan mapping:
 * <ul>
 *   <li>{@link #NEAREST} → {@code VK_FILTER_NEAREST}</li>
 *   <li>{@link #LINEAR}  → {@code VK_FILTER_LINEAR}</li>
 * </ul>
 *
 * <p>OpenGL mapping:
 * <ul>
 *   <li>{@link #NEAREST} → {@code GL_NEAREST} (9728)</li>
 *   <li>{@link #LINEAR}  → {@code GL_LINEAR}  (9729)</li>
 * </ul>
 *
 * <p>Used in both the minification and magnification filter slots of a
 * {@link VulkanicSamplerDescriptor}.
 */
public enum VulkanicFilterMode {

    /**
     * Nearest-neighbour filtering — samples the single closest texel.
     * Produces sharp, pixelated results.
     *
     * <ul>
     *   <li>OpenGL: {@code GL_NEAREST} (9728) for mag/min filter;
     *       {@code GL_NEAREST_MIPMAP_NEAREST} (9984) when mipmaps are enabled.</li>
     *   <li>Vulkan: {@code VK_FILTER_NEAREST} (0)</li>
     * </ul>
     */
    NEAREST,

    /**
     * Bilinear (linear) filtering — samples and blends the four nearest texels.
     * Produces smooth results.
     *
     * <ul>
     *   <li>OpenGL: {@code GL_LINEAR} (9729) for mag filter;
     *       {@code GL_LINEAR_MIPMAP_LINEAR} (9987) when mipmaps are enabled.</li>
     *   <li>Vulkan: {@code VK_FILTER_LINEAR} (1)</li>
     * </ul>
     */
    LINEAR;

    /**
     * Converts a Blaze3D {@code FilterMode} to its Vulkanic equivalent.
     *
     * <p>This bridge method lives here (not in Blaze3D) so that Blaze3D classes
     * can delegate their filter-mode handling to the Vulkanic type system.
     *
     * @param blaze3d The Blaze3D filter mode
     * @return The corresponding {@code VulkanicFilterMode}
     */
    public static VulkanicFilterMode fromBlaze3d(net.blaze3d.textures.FilterMode blaze3d) {
        return switch (blaze3d) {
            case NEAREST -> NEAREST;
            case LINEAR  -> LINEAR;
        };
    }
}
