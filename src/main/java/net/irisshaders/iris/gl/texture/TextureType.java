package net.irisshaders.iris.gl.texture;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureTarget;

import java.nio.ByteBuffer;
import java.util.Optional;

public enum TextureType {
	TEXTURE_1D(VulkanicAPI.GL_TEXTURE_1D),
	TEXTURE_2D(VulkanicAPI.GL_TEXTURE_2D),
	TEXTURE_3D(VulkanicAPI.GL_TEXTURE_3D),
	TEXTURE_RECTANGLE(VulkanicAPI.GL_TEXTURE_RECTANGLE);

	private final int glType;

	TextureType(int glType) {
		this.glType = glType;
	}

	public static Optional<TextureType> fromString(String name) {
		try {
			return Optional.of(TextureType.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlType() {
		return glType;
	}

	public Optional<VulkanicTextureTarget> getVulkanicTarget() {
		return VulkanicTextureTarget.fromLegacyGlTarget(glType);
	}

	public int createTexture() {
		return getVulkanicTarget()
			.map(IrisRenderSystem::createTexture)
			.orElseGet(() -> IrisRenderSystem.createTexture(getGlType()));
	}

	public void bindForSetup(int texture) {
		getVulkanicTarget()
			.ifPresentOrElse(
				typedTarget -> IrisRenderSystem.bindTextureForSetup(typedTarget, texture),
				() -> IrisRenderSystem.bindTextureForSetup(getGlType(), texture)
			);
	}

	public void bindToUnit(int unit, int texture) {
		getVulkanicTarget()
			.ifPresentOrElse(
				typedTarget -> IrisRenderSystem.bindTextureToUnit(typedTarget, unit, texture),
				() -> IrisRenderSystem.bindTextureToUnit(getGlType(), unit, texture)
			);
	}

	public void setLinearFiltering(CommandContext ctx) {
		getVulkanicTarget()
			.ifPresentOrElse(
				typedTarget -> VulkanicAPI.setTextureLinearFiltering(ctx, typedTarget),
				() -> VulkanicAPI.setTextureLinearFiltering(ctx, getGlType())
			);
	}

	public void setNearestFiltering(CommandContext ctx) {
		getVulkanicTarget()
			.ifPresentOrElse(
				typedTarget -> VulkanicAPI.setTextureNearestFiltering(ctx, typedTarget),
				() -> VulkanicAPI.setTextureNearestFiltering(ctx, getGlType())
			);
	}

	public void setWrapMode(CommandContext ctx, boolean clampToEdge, boolean includeWrapT, boolean includeWrapR) {
		getVulkanicTarget()
			.ifPresentOrElse(
				typedTarget -> VulkanicAPI.setTextureWrapMode(ctx, typedTarget, clampToEdge, includeWrapT, includeWrapR),
				() -> VulkanicAPI.setTextureWrapMode(ctx, getGlType(), clampToEdge, includeWrapT, includeWrapR)
			);
	}

	public void resetLodRangeToZero(CommandContext ctx) {
		getVulkanicTarget()
			.ifPresentOrElse(
				typedTarget -> VulkanicAPI.resetTextureLodRangeToZero(ctx, typedTarget),
				() -> VulkanicAPI.resetTextureLodRangeToZero(ctx, getGlType())
			);
	}

	public void apply(int texture, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, ByteBuffer pixels) {
		switch (this) {
			case TEXTURE_1D:
				IrisRenderSystem.texImage1D(texture, getGlType(), 0, internalFormat, sizeX, 0, format, pixelType, pixels);
				break;
			case TEXTURE_2D, TEXTURE_RECTANGLE:
				getVulkanicTarget().ifPresentOrElse(
					typedTarget -> IrisRenderSystem.texImage2D(texture, typedTarget, 0, internalFormat, sizeX, sizeY, 0, format, pixelType, pixels),
					() -> IrisRenderSystem.texImage2D(texture, getGlType(), 0, internalFormat, sizeX, sizeY, 0, format, pixelType, pixels)
				);
				break;
			case TEXTURE_3D:
				IrisRenderSystem.texImage3D(texture, getGlType(), 0, internalFormat, sizeX, sizeY, sizeZ, 0, format, pixelType, pixels);
				break;
		}
	}
}
