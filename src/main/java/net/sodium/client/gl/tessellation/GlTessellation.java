package net.sodium.client.gl.tessellation;

import net.sodium.client.gl.device.CommandList;
import net.sodium.client.render.device.RenderTessellation;
import net.vulkanic.VulkanicPrimitiveMode;

public interface GlTessellation extends RenderTessellation {
    @Override
    void delete(CommandList commandList);

    @Override
    void bind(CommandList commandList);

    @Override
    void unbind(CommandList commandList);

    GlPrimitiveType getPrimitiveType();

    @Override
    default VulkanicPrimitiveMode getPrimitiveMode() {
        return this.getPrimitiveType().toVulkanicPrimitiveMode();
    }
}
