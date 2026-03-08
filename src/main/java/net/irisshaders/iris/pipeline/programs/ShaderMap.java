package net.irisshaders.iris.pipeline.programs;

import net.blaze3d.opengl.GlProgram;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.vulkanic.VulkanicAPI;

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
					VulkanicAPI.deleteProgram(VulkanicAPI.getImmediateContext(), shader.id().program());
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

		int j = VulkanicAPI.getProgramParameter(VulkanicAPI.getImmediateContext(), i, 35714);
		if (j == VulkanicAPI.GL_FALSE) {
			String string = VulkanicAPI.getProgramInfoLog(VulkanicAPI.getImmediateContext(), i);
			throw new ShaderCompileException(
				key.name(), string
			);
		}
	}

	public GlProgram getShader(ShaderKey id) {
		return shaders[id.ordinal()];
	}
}
