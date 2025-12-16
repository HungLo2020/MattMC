package net.minecraft.client.renderer.gl.advanced.tessellation;

import net.minecraft.client.renderer.gl.advanced.device.CommandList;

public interface GlTessellation {
    void delete(CommandList commandList);

    void bind(CommandList commandList);

    void unbind(CommandList commandList);

    GlPrimitiveType getPrimitiveType();
}
