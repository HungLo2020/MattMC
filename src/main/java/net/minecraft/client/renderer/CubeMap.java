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
import net.minecraft.client.renderer.texture.CubeMapTexture;
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
		this.projectionMatrixUbo = net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			? null
			: new CachedPerspectiveProjectionMatrixBuffer("cubemap", 0.05F, 10.0F);
	}

	/** Semantic source identity for the Rust-owned panorama path. */
	public ResourceLocation semanticTextureLocation() {
		return this.location;
	}

	public void render(Minecraft minecraft, float f, float g) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			int width = minecraft.getWindow().getGuiScaledWidth();
			int height = minecraft.getWindow().getGuiScaledHeight();
			if (!net.vulkanic.gui.RustGalPanoramaRenderer.enqueue(this, f, g, width, height)) {
				throw new IllegalStateException(
					"Rust Vulkan whole-frame panorama asset is unavailable; Java cube-map rendering is not a fallback"
				);
			}
			return;
		}
		net.vulkanic.VulkanicAPI.setProjectionMatrix(
			this.projectionMatrixUbo.getBuffer(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), 85.0F), ProjectionType.PERSPECTIVE
		);
		RenderPipeline renderPipeline = RenderPipelines.PANORAMA;
		RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
		GpuTextureView gpuTextureView = renderTarget.getColorTextureView();
		Matrix4fStack matrix4fStack = VulkanicAPI.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.rotationX((float) Math.PI);
		matrix4fStack.rotateX(f * (float) (Math.PI / 180.0));
		matrix4fStack.rotateY(g * (float) (Math.PI / 180.0));
		GpuBufferSlice gpuBufferSlice = VulkanicAPI.getDynamicUniforms()
			.writeTransform(new Matrix4f(matrix4fStack), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
		matrix4fStack.popMatrix();

		try (RenderPass renderPass = gpuTextureView != null
			? VulkanicAPI.createRenderPass(() -> "Cubemap", gpuTextureView, OptionalInt.empty())
			: createFramebufferRenderPass(renderTarget)) {
			renderPass.setPipeline(renderPipeline);
			net.vulkanic.VulkanicAPI.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.bindSampler("Sampler0", minecraft.getTextureManager().getTexture(this.location).getTextureView());
			renderPass.draw(0, 3);
		}
	}

	private static RenderPass createFramebufferRenderPass(RenderTarget renderTarget) {
		int framebuffer = VulkanicAPI.resolveFramebufferForTextures(renderTarget.getColorTexture(), renderTarget.getDepthTexture());
		return VulkanicAPI.createRenderPass(() -> "Cubemap", framebuffer, renderTarget.getDepthTexture() != null);
	}

	public void registerTextures(TextureManager textureManager) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			return;
		}
		textureManager.register(this.location, new CubeMapTexture(this.location));
	}

	public void registerAndLoadTextures(TextureManager textureManager) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			return;
		}
		textureManager.registerAndLoad(this.location, new CubeMapTexture(this.location));
	}

	public void close() {
		if (this.projectionMatrixUbo != null) {
			this.projectionMatrixUbo.close();
		}
	}
}
