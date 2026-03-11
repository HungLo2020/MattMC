package net.irisshaders.iris.gl.shader;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProgramCreator {
	private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

	public static int create(String name, GlShader... shaders) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		int program = VulkanicAPI.createShaderProgram(ctx);

		VulkanicAPI.setAttributeLocation(ctx, program, 11, "iris_Entity");
		VulkanicAPI.setAttributeLocation(ctx, program, 11, "mc_Entity");
		VulkanicAPI.setAttributeLocation(ctx, program, 12, "mc_midTexCoord");
		VulkanicAPI.setAttributeLocation(ctx, program, 13, "at_tangent");
		VulkanicAPI.setAttributeLocation(ctx, program, 14, "at_midBlock");

		VulkanicAPI.setAttributeLocation(ctx, program, 0, "Position");
		VulkanicAPI.setAttributeLocation(ctx, program, 1, "UV0");

		for (GlShader shader : shaders) {
			GLDebug.nameObject(VulkanicAPI.GL_SHADER, shader.getHandle(), shader.getName());

			VulkanicAPI.attachShader(ctx, program, shader.getHandle());
		}

		VulkanicAPI.linkProgram(ctx, program);

		GLDebug.nameObject(VulkanicAPI.GL_PROGRAM, program, name);

		//Always detach shaders according to https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
		for (GlShader shader : shaders) {
			IrisRenderSystem.detachShader(program, shader.getHandle());
		}

		String log = IrisRenderSystem.getProgramInfoLog(program);

		if (!log.isEmpty()) {
			LOGGER.warn("Program link log for " + name + ": " + log);
		}

		int result = VulkanicAPI.getProgramParameter(ctx, program, net.vulkanic.VulkanicProgramParameterName.LINK_STATUS);

		if (result != VulkanicAPI.GL_TRUE) {
			throw new ShaderCompileException(name, log);
		}

		return program;
	}
}
