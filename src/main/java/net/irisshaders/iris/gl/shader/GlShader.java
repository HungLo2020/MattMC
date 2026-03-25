package net.irisshaders.iris.gl.shader;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlResource {
	private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

	private final String name;

	public GlShader(ShaderType type, String name, String src) {
		super(createShader(type, name, src));

		this.name = name;
	}

	private static int createShader(ShaderType type, String name, String src) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicShaderHandle handle = VulkanicAPI.createShaderHandle(ctx, type.stage);
		ShaderWorkarounds.safeShaderSource(handle, src);
		VulkanicAPI.compileShader(ctx, handle);

		GLDebug.nameObject(VulkanicAPI.GL_SHADER, handle.value(), name + "(" + type.name().toLowerCase(Locale.ROOT) + ")");

		String log = VulkanicAPI.getShaderInfoLog(ctx, handle);

		if (!log.isEmpty()) {
			LOGGER.warn("Shader compilation log for " + name + ": " + log);
		}

		if (!VulkanicAPI.isShaderCompileSuccessful(ctx, handle)) {
			throw new ShaderCompileException(name, log);
		}

		return handle.value();
	}

	public String getName() {
		return this.name;
	}

	public int getHandle() {
		return this.getGlId();
	}

	@Override
	protected void destroyInternal() {
		VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(this.getGlId()));
	}
}
