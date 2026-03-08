package net.irisshaders.iris.gl.shader;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.VulkanicAPI;
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
		int handle = VulkanicAPI.createShader(VulkanicAPI.getImmediateContext(), type.id);
		ShaderWorkarounds.safeShaderSource(handle, src);
		VulkanicAPI.compileShader(VulkanicAPI.getImmediateContext(), handle);

		GLDebug.nameObject(VulkanicAPI.GL_SHADER, handle, name + "(" + type.name().toLowerCase(Locale.ROOT) + ")");

		String log = IrisRenderSystem.getShaderInfoLog(handle);

		if (!log.isEmpty()) {
			LOGGER.warn("Shader compilation log for " + name + ": " + log);
		}

		int result = VulkanicAPI.getShaderParameter(VulkanicAPI.getImmediateContext(), handle, VulkanicAPI.GL_COMPILE_STATUS);

		if (result != 1) {  // GL_TRUE
			throw new ShaderCompileException(name, log);
		}

		return handle;
	}

	public String getName() {
		return this.name;
	}

	public int getHandle() {
		return this.getGlId();
	}

	@Override
	protected void destroyInternal() {
		VulkanicAPI.deleteShader(VulkanicAPI.getImmediateContext(), this.getGlId());
	}
}
