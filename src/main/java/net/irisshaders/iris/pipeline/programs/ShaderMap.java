package net.irisshaders.iris.pipeline.programs;

import net.blaze3d.opengl.GlProgram;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicProgramHandle;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A specialized map mapping {@link ShaderKey} to {@link CompiledShaderProgram}.
 * Avoids much of the complexity / overhead of an EnumMap while ultimately
 * fulfilling the same function.
 */
public class ShaderMap {
	private final GlProgram[] shaders;

	public ShaderMap(ShaderLoadingMap loadingMap, Function<ShaderSupplier, Boolean> deletionFunction, Consumer<GlProgram> programConsumer) {
		ShaderKey[] ids = ShaderKey.values();

		this.shaders = new GlProgram[ids.length];

		loadingMap.forAllShaders((key, shader) -> {
			if (shader != null) {
				if (deletionFunction.apply(shader)) {
					VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(shader.id().program()));
					return;
				}

				checkLinkingState(key, shader);
				GlProgram shaderProgram = shader.shader().get();
				this.shaders[key.ordinal()] = shaderProgram;
				programConsumer.accept(shaderProgram);
			}
		});
	}

	private void checkLinkingState(ShaderKey key, ShaderSupplier shader) {
		int i = shader.id().program();
		CommandContext ctx = VulkanicAPI.getCommandContext();

		if (!VulkanicAPI.isProgramLinkSuccessful(ctx, i)) {
			String string = VulkanicAPI.getProgramInfoLog(ctx, i);
			throw new ShaderCompileException(
				key.name(), string
			);
		}
	}

	public GlProgram getShader(ShaderKey id) {
		return shaders[id.ordinal()];
	}
}
