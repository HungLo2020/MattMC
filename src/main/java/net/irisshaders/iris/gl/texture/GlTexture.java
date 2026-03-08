package net.irisshaders.iris.gl.texture;

import net.blaze3d.opengl.GlStateManager;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.shaderpack.texture.TextureFilteringData;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

public class GlTexture extends GlResource implements TextureAccess {
	private final TextureType target;

	public GlTexture(TextureType target, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, byte[] pixels, TextureFilteringData filteringData) {
		super(net.irisshaders.iris.gl.IrisRenderSystem.createTextureId());
		IrisRenderSystem.bindTextureForSetup(target.getGlType(), getGlId());

		TextureUploadHelper.resetTextureUploadState();

		ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
		buffer.put(pixels);
		buffer.flip();
		target.apply(this.getGlId(), sizeX, sizeY, sizeZ, internalFormat, format, pixelType, buffer);
		MemoryUtil.memFree(buffer);

		int texture = this.getGlId();

		IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MIN_FILTER, filteringData.shouldBlur() ? VulkanicAPI.GL_LINEAR : VulkanicAPI.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MAG_FILTER, filteringData.shouldBlur() ? VulkanicAPI.GL_LINEAR : VulkanicAPI.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_WRAP_S, filteringData.shouldClamp() ? VulkanicAPI.GL_CLAMP_TO_EDGE : VulkanicAPI.GL_REPEAT);

		if (sizeY > 0) {
			IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_WRAP_T, filteringData.shouldClamp() ? VulkanicAPI.GL_CLAMP_TO_EDGE : VulkanicAPI.GL_REPEAT);
		}

		if (sizeZ > 0) {
			IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_WRAP_R, filteringData.shouldClamp() ? VulkanicAPI.GL_CLAMP_TO_EDGE : VulkanicAPI.GL_REPEAT);
		}

		IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MAX_LEVEL, 0);
		IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MIN_LOD, 0);
		IrisRenderSystem.texParameteri(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_MAX_LOD, 0);
		IrisRenderSystem.texParameterf(texture, target.getGlType(), VulkanicAPI.GL_TEXTURE_LOD_BIAS, 0.0F);

		IrisRenderSystem.bindTextureForSetup(target.getGlType(), 0);

		this.target = target;
	}

	public TextureType getTarget() {
		return target;
	}

	public void bind(int unit) {
		IrisRenderSystem.bindTextureToUnit(target.getGlType(), unit, getGlId());
	}

	@Override
	public TextureType getType() {
		return target;
	}

	@Override
	public IntSupplier getTextureId() {
		return this::getGlId;
	}

	@Override
	protected void destroyInternal() {
		net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(getGlId());
	}
}
