package net.minecraft.client.renderer;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTextureView;
import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.VulkanicAPI;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CubeMap implements AutoCloseable {
	@Nullable
	private final CachedPerspectiveProjectionMatrixBuffer projectionMatrixUbo;
	private final ResourceLocation location;

	public CubeMap(ResourceLocation resourceLocation) {
		this.location = resourceLocation;
		this.projectionMatrixUbo = null;
	}

	/** Semantic source identity for the Rust-owned panorama path. */
	public ResourceLocation semanticTextureLocation() {
		return this.location;
	}

	public void registerTextures(TextureManager textureManager) {
		return;
	}

	public void registerAndLoadTextures(TextureManager textureManager) {
		return;
	}

	public void close() {
		if (this.projectionMatrixUbo != null) {
			this.projectionMatrixUbo.close();
		}
	}

	public void ensureRustSemanticRoute() {
		if (this.projectionMatrixUbo != null) this.projectionMatrixUbo.ensureRustSemanticRoute();
	}
}
