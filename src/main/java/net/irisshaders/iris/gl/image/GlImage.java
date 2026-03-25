package net.irisshaders.iris.gl.image;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.irisshaders.iris.gl.texture.TextureType;
import net.vulkanic.VulkanicAPI;

public class GlImage extends GlResource {
	protected final String name;
	protected final String samplerName;
	protected final TextureType target;
	protected final PixelFormat format;
	protected final InternalTextureFormat internalTextureFormat;
	protected final PixelType pixelType;
	private final boolean clear;

	public GlImage(String name, String samplerName, TextureType target, PixelFormat format, InternalTextureFormat internalFormat, PixelType pixelType, boolean clear, int width, int height, int depth) {
		super(target.createTexture());

		this.name = name;
		this.samplerName = samplerName;
		this.target = target;
		this.format = format;
		this.internalTextureFormat = internalFormat;
		this.pixelType = pixelType;
		this.clear = clear;

		GLDebug.nameObject(VulkanicAPI.GL_TEXTURE, getGlId(), name);

		target.bindForSetup(getGlId());
		target.apply(getGlId(), width, height, depth, internalFormat.getGlFormat(), format.getGlFormat(), pixelType.getGlFormat(), null);

		int texture = getGlId();

		setup(texture, width, height, depth);

		target.bindForSetup(0);
	}

	protected void setup(int texture, int width, int height, int depth) {
		boolean isInteger = internalTextureFormat.getPixelFormat().isInteger();
		var ctx = VulkanicAPI.getCommandContext();
		if (isInteger) {
			target.setNearestFiltering(ctx);
		} else {
			target.setLinearFiltering(ctx);
		}

		target.setWrapMode(ctx, true, height > 0, depth > 0);
		target.resetLodRangeToZero(ctx);

		VulkanicAPI.clearTexImage(VulkanicAPI.getCommandContext(), texture, 0, format.getGlFormat(), pixelType.getGlFormat(), (int[]) null);
	}

	public String getName() {
		return name;
	}

	public String getSamplerName() {
		return samplerName;
	}

	public TextureType getTarget() {
		return target;
	}

	public boolean shouldClear() {
		return clear;
	}

	public int getId() {
		return getGlId();
	}

	/**
	 * This makes the image aware of a new render target. Depending on the image's properties, it may not follow these targets.
	 *
	 * @param width  The width of the main render target.
	 * @param height The height of the main render target.
	 */
	public void updateNewSize(int width, int height) {

	}

	@Override
	protected void destroyInternal() {
		net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(getGlId());
	}

	public InternalTextureFormat getInternalFormat() {
		return internalTextureFormat;
	}

	@Override
	public String toString() {
		return "GlImage name " + name + " format " + format + "internalformat " + internalTextureFormat + " pixeltype " + pixelType;
	}

	public PixelFormat getFormat() {
		return format;
	}

	public PixelType getPixelType() {
		return pixelType;
	}

	public static class Relative extends GlImage {

		private final float relativeHeight;
		private final float relativeWidth;

		public Relative(String name, String samplerName, PixelFormat format, InternalTextureFormat internalFormat, PixelType pixelType, boolean clear, float relativeWidth, float relativeHeight, int currentWidth, int currentHeight) {
			super(name, samplerName, TextureType.TEXTURE_2D, format, internalFormat, pixelType, clear, (int) (currentWidth * relativeWidth), (int) (currentHeight * relativeHeight), 0);

			this.relativeWidth = relativeWidth;
			this.relativeHeight = relativeHeight;
		}

		@Override
		public void updateNewSize(int width, int height) {
			target.bindForSetup(getGlId());
			target.apply(getGlId(), (int) (width * relativeWidth), (int) (height * relativeHeight), 0, internalTextureFormat.getGlFormat(), format.getGlFormat(), pixelType.getGlFormat(), null);

			int texture = getGlId();

			setup(texture, width, height, 0);

			target.bindForSetup(0);
		}
	}
}
