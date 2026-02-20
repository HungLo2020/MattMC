package net.vulkanic.resources;

/**
 * Vulkanic texture address mode — controls how texture coordinates outside
 * the [0, 1] range are handled.
 *
 * <p>This is the Vulkanic equivalent of Blaze3D's {@code AddressMode}.
 * Using this type instead of the Blaze3D enum means the Vulkan backend never
 * needs to touch Blaze3D types when creating a {@link VulkanicSampler}.
 *
 * <p>Vulkan mapping:
 * <ul>
 *   <li>{@link #REPEAT} → {@code VK_SAMPLER_ADDRESS_MODE_REPEAT}</li>
 *   <li>{@link #CLAMP_TO_EDGE} → {@code VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE}</li>
 * </ul>
 *
 * <p>OpenGL mapping:
 * <ul>
 *   <li>{@link #REPEAT} → {@code GL_REPEAT}</li>
 *   <li>{@link #CLAMP_TO_EDGE} → {@code GL_CLAMP_TO_EDGE}</li>
 * </ul>
 */
public enum VulkanicAddressMode {

    /**
     * Texture coordinates wrap around — the texture tiles infinitely in all
     * directions.
     *
     * <ul>
     *   <li>OpenGL: {@code GL_REPEAT} (10497)</li>
     *   <li>Vulkan: {@code VK_SAMPLER_ADDRESS_MODE_REPEAT} (0)</li>
     * </ul>
     */
    REPEAT,

    /**
     * Texture coordinates are clamped to the edge of the texture — coordinates
     * outside [0, 1] sample the nearest edge texel.
     *
     * <ul>
     *   <li>OpenGL: {@code GL_CLAMP_TO_EDGE} (33071)</li>
     *   <li>Vulkan: {@code VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE} (2)</li>
     * </ul>
     */
    CLAMP_TO_EDGE;

    /**
     * Converts a Blaze3D {@code AddressMode} to its Vulkanic equivalent.
     *
     * <p>This bridge method lives here (not in Blaze3D) so that Blaze3D classes
     * can delegate their address-mode handling to the Vulkanic type system.
     *
     * @param blaze3d The Blaze3D address mode
     * @return The corresponding {@code VulkanicAddressMode}
     */
    public static VulkanicAddressMode fromBlaze3d(net.blaze3d.textures.AddressMode blaze3d) {
        return switch (blaze3d) {
            case REPEAT        -> REPEAT;
            case CLAMP_TO_EDGE -> CLAMP_TO_EDGE;
        };
    }
}
