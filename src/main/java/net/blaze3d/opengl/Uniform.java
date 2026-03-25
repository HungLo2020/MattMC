package net.blaze3d.opengl;

import net.blaze3d.textures.TextureFormat;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public sealed interface Uniform extends AutoCloseable permits Uniform.Ubo, Uniform.Utb, Uniform.Sampler {
	default void close() {
	}

	@Environment(EnvType.CLIENT)
	public record Sampler(int location, int samplerIndex) implements Uniform {
	}

	@Environment(EnvType.CLIENT)
	public record Ubo(int blockBinding) implements Uniform {
	}

	@Environment(EnvType.CLIENT)
	public record Utb(int location, int samplerIndex, TextureFormat format, int texture) implements Uniform {
		public Utb(int i, int j, TextureFormat textureFormat) {
			this(i, j, textureFormat, net.irisshaders.iris.gl.IrisRenderSystem.createTextureId());
		}

		@Override
		public void close() {
			net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(this.texture);
		}
	}
}
