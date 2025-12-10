package net.minecraft.client.renderer.shaders.shadows;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;

/**
 * Shadow configuration directives from shader pack properties.
 * Based on IRIS PackShadowDirectives.java
 */
public class PackShadowDirectives {
    // Maximum shadow color buffers (IRIS: 8, OptiFine: 2)
    public static final int MAX_SHADOW_COLOR_BUFFERS_IRIS = 8;
    public static final int MAX_SHADOW_COLOR_BUFFERS_OF = 2;

    private final int resolution;
    private final float distance;
    private final float intervalSize;
    private final boolean shouldRenderTerrain;
    private final boolean shouldRenderTranslucent;
    private final boolean shouldRenderEntities;
    private final DepthSamplingSettings[] depthSamplingSettings;
    private final Int2ObjectMap<SamplingSettings> colorSamplingSettings;

    public PackShadowDirectives() {
        // Default values from IRIS
        this.resolution = 1024;
        this.distance = 160.0f;
        this.intervalSize = 2.0f;
        this.shouldRenderTerrain = true;
        this.shouldRenderTranslucent = true;
        this.shouldRenderEntities = true;

        // shadowtex0 and shadowtex1
        this.depthSamplingSettings = new DepthSamplingSettings[2];
        this.depthSamplingSettings[0] = new DepthSamplingSettings();
        this.depthSamplingSettings[1] = new DepthSamplingSettings();

        this.colorSamplingSettings = new Int2ObjectArrayMap<>();
    }

    public int getResolution() {
        return resolution;
    }

    public float getDistance() {
        return distance;
    }

    public float getIntervalSize() {
        return intervalSize;
    }

    public boolean getShouldRenderTerrain() {
        return shouldRenderTerrain;
    }

    public boolean getShouldRenderTranslucent() {
        return shouldRenderTranslucent;
    }

    public boolean getShouldRenderEntities() {
        return shouldRenderEntities;
    }

    public DepthSamplingSettings[] getDepthSamplingSettings() {
        return depthSamplingSettings;
    }

    public Int2ObjectMap<SamplingSettings> getColorSamplingSettings() {
        return colorSamplingSettings;
    }

    /**
     * Depth texture sampling settings (shadowtex0, shadowtex1)
     */
    public static class DepthSamplingSettings {
        private boolean hardwareFiltering = false;
        private boolean mipmap = false;
        private boolean nearest = false;

        public boolean getHardwareFiltering() {
            return hardwareFiltering;
        }

        public void setHardwareFiltering(boolean hardwareFiltering) {
            this.hardwareFiltering = hardwareFiltering;
        }

        public boolean getMipmap() {
            return mipmap;
        }

        public void setMipmap(boolean mipmap) {
            this.mipmap = mipmap;
        }

        public boolean getNearest() {
            return nearest;
        }

        public void setNearest(boolean nearest) {
            this.nearest = nearest;
        }
    }

    /**
     * Color buffer sampling settings (shadowcolor0-7)
     */
    public static class SamplingSettings {
        private InternalTextureFormat format = InternalTextureFormat.RGBA;
        private boolean clear = false;
        private boolean mipmap = false;
        private boolean nearest = false;

        public InternalTextureFormat getFormat() {
            return format;
        }

        public void setFormat(InternalTextureFormat format) {
            this.format = format;
        }

        public boolean getClear() {
            return clear;
        }

        public void setClear(boolean clear) {
            this.clear = clear;
        }

        public boolean getMipmap() {
            return mipmap;
        }

        public void setMipmap(boolean mipmap) {
            this.mipmap = mipmap;
        }

        public boolean getNearest() {
            return nearest;
            }

        public void setNearest(boolean nearest) {
            this.nearest = nearest;
        }
    }
    
    // Helper methods for shadow rendering
    
    /**
     * Gets the shadow angle offset.
     * @return shadow angle in radians
     */
    public float getShadowAngle() {
        return 0.0f;  // Default, could be configured from shader pack
    }
    
    /**
     * Gets the distance render multiplier for shadow culling.
     * @return distance multiplier
     */
    public float getDistanceRenderMul() {
        return 1.0f;  // Default 1.0x distance
    }
    
    /**
     * Gets the number of shadow composite passes.
     * @return number of composite passes (0-8)
     */
    public int getCompositePasses() {
        return 0;  // No composite passes by default
    }
    
    /**
     * Gets the number of shadow color samplers.
     * @return number of color samplers
     */
    public int getColorSamplersCount() {
        return colorSamplingSettings.size();
    }
    
    /**
     * Checks if mipmaps should be generated for shadow maps.
     * @return true if mipmaps should be generated
     */
    public boolean shouldGenerateMipmaps() {
        // Check if any depth texture has mipmaps enabled
        for (DepthSamplingSettings settings : depthSamplingSettings) {
            if (settings.getMipmap()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if hardware filtering should be used.
     * @return true if hardware filtering is enabled
     */
    public boolean shouldUseHardwareFiltering() {
        // Check if any depth texture has hardware filtering
        for (DepthSamplingSettings settings : depthSamplingSettings) {
            if (settings.getHardwareFiltering()) {
                return true;
            }
        }
        return false;
    }
}
