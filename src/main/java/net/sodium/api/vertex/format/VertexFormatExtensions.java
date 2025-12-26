package net.sodium.api.vertex.format;

import com.mojang.blaze3d.vertex.VertexFormat;

public interface VertexFormatExtensions {
    /**
     * Returns an integer identifier that represents this vertex format in the global namespace. These identifiers
     * are valid only for the current process lifetime and should not be saved to disk.
     */
    int sodium$getGlobalId();
    
    /**
     * Gets the global ID for a vertex format using the registry.
     * This replaces the need for casting to VertexFormatExtensions.
     */
    static int getGlobalId(VertexFormat format) {
        return VertexFormatRegistry.instance().allocateGlobalId(format);
    }
}
