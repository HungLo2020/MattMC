package net.minecraft.client.renderer;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
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

@Environment(EnvType.CLIENT)
public class CubeMap implements AutoCloseable {
	private static final int SIDES = 6;
	private static final boolean DEBUG_SKIP_VULKAN_TITLE_PANORAMA_EXPERIMENT = false;
	private final GpuBuffer vertexBuffer;
	private final CachedPerspectiveProjectionMatrixBuffer projectionMatrixUbo;
	private final ResourceLocation location;

	public CubeMap(ResourceLocation resourceLocation) {
		this.location = resourceLocation;
		this.projectionMatrixUbo = new CachedPerspectiveProjectionMatrixBuffer("cubemap", 0.05F, 10.0F);
		this.vertexBuffer = initializeVertices();
	}

	public void render(Minecraft minecraft, float f, float g) {
		if (DEBUG_SKIP_VULKAN_TITLE_PANORAMA_EXPERIMENT && VulkanicAPI.isVulkanBackendSelected()) {
			return;
		}

		net.vulkanic.VulkanicAPI.setProjectionMatrix(
			this.projectionMatrixUbo.getBuffer(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), 85.0F), ProjectionType.PERSPECTIVE
		);
		RenderPipeline renderPipeline = RenderPipelines.PANORAMA;
		RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
		GpuTextureView gpuTextureView = renderTarget.getColorTextureView();
		GpuTextureView gpuTextureView2 = renderTarget.getDepthTextureView();
		VulkanicAPI.AutoStorageIndexBuffer autoStorageIndexBuffer = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer gpuBuffer = autoStorageIndexBuffer.getBuffer(36);
		Matrix4fStack matrix4fStack = VulkanicAPI.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.rotationX((float) Math.PI);
		matrix4fStack.rotateX(f * (float) (Math.PI / 180.0));
		matrix4fStack.rotateY(g * (float) (Math.PI / 180.0));
		GpuBufferSlice gpuBufferSlice = VulkanicAPI.getDynamicUniforms()
			.writeTransform(new Matrix4f(matrix4fStack), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
		matrix4fStack.popMatrix();

		try (RenderPass renderPass = VulkanicAPI.createRenderPass(() -> "Cubemap", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(renderPipeline);
			net.vulkanic.VulkanicAPI.bindDefaultUniforms(renderPass);
			renderPass.setVertexBuffer(0, this.vertexBuffer);
			renderPass.setIndexBuffer(gpuBuffer, autoStorageIndexBuffer.type());
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.bindSampler("Sampler0", minecraft.getTextureManager().getTexture(this.location).getTextureView());
			renderPass.drawIndexed(0, 0, 36, 1);
		}
	}

	private static GpuBuffer initializeVertices() {
		GpuBuffer var3;
		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 4 * 6)) {
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
			bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, 1.0F);

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				var3 = VulkanicAPI.createBuffer(() -> "Cube map vertex buffer", 32, meshData.vertexBuffer());
			}
		}

		return var3;
	}

	public void registerTextures(TextureManager textureManager) {
		textureManager.register(this.location, new CubeMapTexture(this.location));
	}

	public void registerAndLoadTextures(TextureManager textureManager) {
		textureManager.registerAndLoad(this.location, new CubeMapTexture(this.location));
	}

	public void close() {
		this.vertexBuffer.close();
		this.projectionMatrixUbo.close();
	}
}
