package net.irisshaders.iris.gl.texture;

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
		target.bindForSetup(getGlId());

		TextureUploadHelper.resetTextureUploadState();

		ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
		buffer.put(pixels);
		buffer.flip();
		target.apply(this.getGlId(), sizeX, sizeY, sizeZ, internalFormat, format, pixelType, buffer);
		MemoryUtil.memFree(buffer);

		var ctx = VulkanicAPI.getCommandContext();

		if (filteringData.shouldBlur()) {
			target.setLinearFiltering(ctx);
		} else {
			target.setNearestFiltering(ctx);
		}

		target.setWrapMode(ctx, filteringData.shouldClamp(), sizeY > 0, sizeZ > 0);
		target.resetLodRangeToZero(ctx);

		target.bindForSetup(0);

		this.target = target;
	}

	public TextureType getTarget() {
		return target;
	}

	public void bind(int unit) {
		target.bindToUnit(unit, getGlId());
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
