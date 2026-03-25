package net.irisshaders.iris.gl.shader;

import net.irisshaders.iris.gl.GLDebug;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicProgramHandle;
import net.vulkanic.VulkanicShaderHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProgramCreator {
	private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

	public static int create(String name, GlShader... shaders) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicProgramHandle program = VulkanicAPI.createShaderProgramHandle(ctx);
		int programId = program.value();

		VulkanicAPI.setAttributeLocation(ctx, programId, 11, "iris_Entity");
		VulkanicAPI.setAttributeLocation(ctx, programId, 11, "mc_Entity");
		VulkanicAPI.setAttributeLocation(ctx, programId, 12, "mc_midTexCoord");
		VulkanicAPI.setAttributeLocation(ctx, programId, 13, "at_tangent");
		VulkanicAPI.setAttributeLocation(ctx, programId, 14, "at_midBlock");

		VulkanicAPI.setAttributeLocation(ctx, programId, 0, "Position");
		VulkanicAPI.setAttributeLocation(ctx, programId, 1, "UV0");

		for (GlShader shader : shaders) {
			GLDebug.nameObject(VulkanicAPI.GL_SHADER, shader.getHandle(), shader.getName());

			VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(shader.getHandle()));
		}

		VulkanicAPI.linkProgram(ctx, program);

		GLDebug.nameObject(VulkanicAPI.GL_PROGRAM, programId, name);

		//Always detach shaders according to https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
		for (GlShader shader : shaders) {
			VulkanicAPI.detachShader(ctx, program, VulkanicShaderHandle.of(shader.getHandle()));
		}

		String log = VulkanicAPI.getProgramInfoLog(ctx, program);

		if (!log.isEmpty()) {
			LOGGER.warn("Program link log for " + name + ": " + log);
		}

		if (!VulkanicAPI.isProgramLinkSuccessful(ctx, program)) {
			throw new ShaderCompileException(name, log);
		}

		return programId;
	}
}
