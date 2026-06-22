package net.sodium.client.render.device;

import net.sodium.client.gl.device.CommandList;
import net.vulkanic.VulkanicPrimitiveMode;

public interface RenderTessellation {
    void delete(CommandList commandList);

    void bind(CommandList commandList);

    void unbind(CommandList commandList);

    VulkanicPrimitiveMode getPrimitiveMode();
}
