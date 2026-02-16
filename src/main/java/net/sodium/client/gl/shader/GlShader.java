package net.sodium.client.gl.shader;

import net.sodium.client.gl.GlObject;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.VulkanicAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

    private final ResourceLocation name;

    public GlShader(ShaderType type, ResourceLocation name, ShaderParser.ParsedShader parsedShader) {
        this.name = name;

        net.vulkanic.CommandContext ctx = net.vulkanic.VulkanicAPI.getImmediateContext();
        int handle = VulkanicAPI.createShader(ctx, type.id);
        ShaderWorkarounds.safeShaderSource(handle, parsedShader.src());
        VulkanicAPI.compileShader(ctx, handle);

        String log = VulkanicAPI.getShaderInfoLog(ctx, handle);

        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for {}: {}", this.name, log);
            LOGGER.warn("Include table: {}", Arrays.toString(parsedShader.includeIds()));
        }

        int result = VulkanicAPI.getShaderParameter(ctx, handle, VulkanicAPI.GL_COMPILE_STATUS);

        if (result != 1) {  // GL_TRUE
            throw new RuntimeException("Shader compilation failed, see log for details");
        }

        this.setHandle(handle);
    }

    public ResourceLocation getName() {
        return this.name;
    }

    public void delete() {
        VulkanicAPI.deleteShader(net.vulkanic.VulkanicAPI.getImmediateContext(), this.handle());

        this.invalidateHandle();
    }
}
