package net.vulkanic.resources;

/**
 * Descriptor used to create a {@link VulkanicSampler}.
 *
 * <p>A sampler encapsulates all state that controls how a texture is sampled:
 * filter modes, address modes, LOD clamping, anisotropic filtering, etc.
 *
 * <p>In OpenGL, sampler state is often set directly on texture objects via
 * {@code glTexParameteri}.  In Vulkan, sampler objects ({@code VkSampler}) are
 * explicit, immutable, and created separately from image objects.  This
 * descriptor follows the Vulkan model so that a {@link VulkanicSampler} created
 * from it works on both backends without changes to call sites.
 *
 * <p>Usage:
 * <pre>{@code
 * VulkanicSamplerDescriptor desc = VulkanicSamplerDescriptor.builder()
 *     .minFilter(VulkanicFilterMode.LINEAR)
 *     .magFilter(VulkanicFilterMode.LINEAR)
 *     .mipmapMode(VulkanicFilterMode.LINEAR)
 *     .addressU(VulkanicAddressMode.CLAMP_TO_EDGE)
 *     .addressV(VulkanicAddressMode.CLAMP_TO_EDGE)
 *     .addressW(VulkanicAddressMode.CLAMP_TO_EDGE)
 *     .minLod(0.0f)
 *     .maxLod(4.0f)
 *     .debugLabel("my_sampler")
 *     .build();
 * VulkanicSampler sampler = VulkanicAPI.createSampler(ctx, desc);
 * }</pre>
 *
 * @see VulkanicSampler
 * @see VulkanicFilterMode
 * @see VulkanicAddressMode
 */
public final class VulkanicSamplerDescriptor {

    private final VulkanicFilterMode minFilter;
    private final VulkanicFilterMode magFilter;
    /** Mipmap interpolation mode; {@link VulkanicFilterMode#NEAREST} disables trilinear. */
    private final VulkanicFilterMode mipmapMode;
    private final VulkanicAddressMode addressU;
    private final VulkanicAddressMode addressV;
    private final VulkanicAddressMode addressW;
    private final float mipLodBias;
    private final float maxAnisotropy;
    private final float minLod;
    private final float maxLod;
    private final String debugLabel;

    private VulkanicSamplerDescriptor(Builder b) {
        this.minFilter    = b.minFilter;
        this.magFilter    = b.magFilter;
        this.mipmapMode   = b.mipmapMode;
        this.addressU     = b.addressU;
        this.addressV     = b.addressV;
        this.addressW     = b.addressW;
        this.mipLodBias   = b.mipLodBias;
        this.maxAnisotropy = b.maxAnisotropy;
        this.minLod       = b.minLod;
        this.maxLod       = b.maxLod;
        this.debugLabel   = b.debugLabel;
    }

    public VulkanicFilterMode getMinFilter()     { return minFilter; }
    public VulkanicFilterMode getMagFilter()     { return magFilter; }
    public VulkanicFilterMode getMipmapMode()    { return mipmapMode; }
    public VulkanicAddressMode getAddressU()     { return addressU; }
    public VulkanicAddressMode getAddressV()     { return addressV; }
    public VulkanicAddressMode getAddressW()     { return addressW; }
    public float getMipLodBias()                 { return mipLodBias; }
    public float getMaxAnisotropy()              { return maxAnisotropy; }
    public float getMinLod()                     { return minLod; }
    public float getMaxLod()                     { return maxLod; }
    public String getDebugLabel()                { return debugLabel; }

    /** Returns a builder with sane defaults (nearest filter, repeat address, no anisotropy). */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private VulkanicFilterMode  minFilter    = VulkanicFilterMode.NEAREST;
        private VulkanicFilterMode  magFilter    = VulkanicFilterMode.NEAREST;
        private VulkanicFilterMode  mipmapMode   = VulkanicFilterMode.NEAREST;
        private VulkanicAddressMode addressU     = VulkanicAddressMode.REPEAT;
        private VulkanicAddressMode addressV     = VulkanicAddressMode.REPEAT;
        private VulkanicAddressMode addressW     = VulkanicAddressMode.REPEAT;
        private float mipLodBias   = 0.0f;
        private float maxAnisotropy = 1.0f;
        private float minLod       = 0.0f;
        private float maxLod       = Float.MAX_VALUE;
        private String debugLabel  = "VulkanicSampler";

        private Builder() {}

        public Builder minFilter(VulkanicFilterMode v)    { this.minFilter  = v; return this; }
        public Builder magFilter(VulkanicFilterMode v)    { this.magFilter  = v; return this; }
        public Builder mipmapMode(VulkanicFilterMode v)   { this.mipmapMode = v; return this; }
        public Builder addressU(VulkanicAddressMode v)    { this.addressU   = v; return this; }
        public Builder addressV(VulkanicAddressMode v)    { this.addressV   = v; return this; }
        public Builder addressW(VulkanicAddressMode v)    { this.addressW   = v; return this; }
        public Builder mipLodBias(float v)                { this.mipLodBias = v; return this; }
        public Builder maxAnisotropy(float v)             { this.maxAnisotropy = v; return this; }
        public Builder minLod(float v)                    { this.minLod     = v; return this; }
        public Builder maxLod(float v)                    { this.maxLod     = v; return this; }
        public Builder debugLabel(String v)               { this.debugLabel = v; return this; }

        public VulkanicSamplerDescriptor build()          { return new VulkanicSamplerDescriptor(this); }
    }
}
