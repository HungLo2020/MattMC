package net.sodium.client.gl.shader;

import net.sodium.client.gl.GlObject;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderHandle;
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
        CommandContext ctx = VulkanicAPI.getCommandContext();

        VulkanicShaderHandle handle = VulkanicAPI.createShaderHandle(ctx, type.stage);
        ShaderWorkarounds.safeShaderSource(handle, parsedShader.src());
        VulkanicAPI.compileShader(ctx, handle);

        String log = VulkanicAPI.getShaderInfoLog(ctx, handle);

        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for {}: {}", this.name, log);
            LOGGER.warn("Include table: {}", Arrays.toString(parsedShader.includeIds()));
        }

        if (!VulkanicAPI.isShaderCompileSuccessful(ctx, handle)) {
            throw new RuntimeException("Shader compilation failed, see log for details");
        }

        this.setHandle(handle.value());
    }

    public ResourceLocation getName() {
        return this.name;
    }

    public void delete() {
        VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(this.handle()));

        this.invalidateHandle();
    }
}
