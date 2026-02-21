package net.blaze3d;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.resources.VulkanicBufferSlice;
import net.vulkanic.resources.VulkanicTextureFormat;
import com.mojang.jtracy.TracyClient;
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
		this.frameBuffer = (GpuTexture) VulkanicAPI.createVulkanicTexture("Tracy Frame Capture", 10, VulkanicTextureFormat.RGBA8, this.width, this.height, 1, 1);
		this.frameBufferView = (GpuTextureView) VulkanicAPI.createVulkanicTextureView(this.frameBuffer);
		this.pixelbuffer = (GpuBuffer) VulkanicAPI.createVulkanicBuffer(9, this.width * this.height * 4);
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
			this.frameBuffer = (GpuTexture) VulkanicAPI.createVulkanicTexture("Tracy Frame Capture", 10, VulkanicTextureFormat.RGBA8, i, j, 1, 1);
			this.frameBufferView.close();
			this.frameBufferView = (GpuTextureView) VulkanicAPI.createVulkanicTextureView(this.frameBuffer);
			this.pixelbuffer.close();
			this.pixelbuffer = (GpuBuffer) VulkanicAPI.createVulkanicBuffer(9, i * j * 4);
		}
	}

	public void capture(RenderTarget renderTarget) {
		if (this.status == TracyFrameCapture.Status.WAITING_FOR_CAPTURE && !this.capturedThisFrame && renderTarget.getColorTexture() != null) {
			this.capturedThisFrame = true;
			if (renderTarget.width != this.targetWidth || renderTarget.height != this.targetHeight) {
				this.targetWidth = renderTarget.width;
				this.targetHeight = renderTarget.height;
				this.resize(this.targetWidth, this.targetHeight);
			}

			this.status = TracyFrameCapture.Status.WAITING_FOR_COPY;
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();

			try (net.vulkanic.resources.VulkanicRenderPass renderPass = VulkanicAPI.createVulkanicRenderPass(
					ctx, () -> "Tracy blit", this.frameBufferView, OptionalInt.empty())) {
				renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
				renderPass.bindSampler("InSampler", renderTarget.getColorTextureView());
				renderPass.draw(0, 3);
			}

			VulkanicAPI.copyVulkanicTextureToBuffer(ctx, this.frameBuffer, this.pixelbuffer, 0,
				() -> this.status = TracyFrameCapture.Status.WAITING_FOR_UPLOAD, 0);
			this.lastCaptureDelay = 0;
		}
	}

	public void upload() {
		if (this.status == TracyFrameCapture.Status.WAITING_FOR_UPLOAD) {
			this.status = TracyFrameCapture.Status.WAITING_FOR_CAPTURE;

			try (net.vulkanic.resources.VulkanicMapView mappedView = VulkanicAPI.mapBuffer(
					VulkanicAPI.getImmediateContext(),
					new VulkanicBufferSlice(this.pixelbuffer, 0, this.pixelbuffer.size()), true, false)) {
				TracyClient.frameImage(mappedView.data(), this.width, this.height, this.lastCaptureDelay, true);
			}
		}
	}

	public void endFrame() {
		this.lastCaptureDelay++;
		this.capturedThisFrame = false;
		TracyClient.markFrame();
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
