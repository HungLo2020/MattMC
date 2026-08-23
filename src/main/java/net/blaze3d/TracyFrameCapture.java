package net.blaze3d;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.minecraft.util.profiling.TracyCompat;
import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;

@Environment(EnvType.CLIENT)
public class TracyFrameCapture implements AutoCloseable {
	private static final int MAX_WIDTH = 320;
	private static final int MAX_HEIGHT = 180;
	private static final int BYTES_PER_PIXEL = 4;
	private int targetWidth;
	private int targetHeight;
	private int width;
	private int height;
	private GpuTexture frameBuffer;
	private GpuTextureView frameBufferView;
	private GpuBuffer pixelbuffer;
	private int lastCaptureDelay;
	private boolean capturedThisFrame;
	private TracyFrameCapture.Status status = TracyFrameCapture.Status.WAITING_FOR_CAPTURE;

	public TracyFrameCapture() {
		this.width = 320;
		this.height = 180;
		this.frameBuffer = net.vulkanic.VulkanicAPI.createTexture("Tracy Frame Capture", 10, TextureFormat.RGBA8, this.width, this.height, 1, 1);
		this.frameBufferView = net.vulkanic.VulkanicAPI.createTextureView(this.frameBuffer);
		this.pixelbuffer = net.vulkanic.VulkanicAPI.createBuffer(() -> "Tracy Frame Capture buffer", 9, this.width * this.height * 4);
	}

	private void resize(int i, int j) {
		float f = (float)i / j;
		if (i > 320) {
			i = 320;
			j = (int)(320.0F / f);
		}

		if (j > 180) {
			i = (int)(180.0F * f);
			j = 180;
		}

		i = i / 4 * 4;
		j = j / 4 * 4;
		if (this.width != i || this.height != j) {
			this.width = i;
			this.height = j;
			this.frameBuffer.close();
			this.frameBuffer = net.vulkanic.VulkanicAPI.createTexture("Tracy Frame Capture", 10, TextureFormat.RGBA8, i, j, 1, 1);
			this.frameBufferView.close();
			this.frameBufferView = net.vulkanic.VulkanicAPI.createTextureView(this.frameBuffer);
			this.pixelbuffer.close();
			this.pixelbuffer = net.vulkanic.VulkanicAPI.createBuffer(() -> "Tracy Frame Capture buffer", 9, i * j * 4);
		}
	}

	public void capture(RenderTarget renderTarget) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Tracy frame capture is unavailable while Rust owns whole-frame presentation");
		}
		if (this.status == TracyFrameCapture.Status.WAITING_FOR_CAPTURE && !this.capturedThisFrame && renderTarget.getColorTexture() != null) {
			this.capturedThisFrame = true;
			if (renderTarget.width != this.targetWidth || renderTarget.height != this.targetHeight) {
				this.targetWidth = renderTarget.width;
				this.targetHeight = renderTarget.height;
				this.resize(this.targetWidth, this.targetHeight);
			}

			this.status = TracyFrameCapture.Status.WAITING_FOR_COPY;
			CommandEncoder commandEncoder = net.vulkanic.VulkanicAPI.createCommandEncoder();

			try (RenderPass renderPass = net.vulkanic.VulkanicAPI.createCommandEncoder().createRenderPass(() -> "Tracy blit", this.frameBufferView, OptionalInt.empty())) {
				renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
				renderPass.bindSampler("InSampler", renderTarget.getColorTextureView());
				renderPass.draw(0, 3);
			}

			commandEncoder.copyTextureToBuffer(this.frameBuffer, this.pixelbuffer, 0, () -> this.status = TracyFrameCapture.Status.WAITING_FOR_UPLOAD, 0);
			this.lastCaptureDelay = 0;
		}
	}

	public void upload() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Tracy frame upload is unavailable while Rust owns whole-frame presentation");
		}
		if (this.status == TracyFrameCapture.Status.WAITING_FOR_UPLOAD) {
			this.status = TracyFrameCapture.Status.WAITING_FOR_CAPTURE;

			try (GpuBuffer.MappedView mappedView = net.vulkanic.VulkanicAPI.createCommandEncoder().mapBuffer(this.pixelbuffer, true, false)) {
				TracyCompat.frameImage(mappedView.data(), this.width, this.height, this.lastCaptureDelay, true);
			}
		}
	}

	public void endFrame() {
		this.lastCaptureDelay++;
		this.capturedThisFrame = false;
		TracyCompat.markFrame();
	}

	public void close() {
		this.frameBuffer.close();
		this.frameBufferView.close();
		this.pixelbuffer.close();
	}

	@Environment(EnvType.CLIENT)
	static enum Status {
		WAITING_FOR_CAPTURE,
		WAITING_FOR_COPY,
		WAITING_FOR_UPLOAD
    }
}
