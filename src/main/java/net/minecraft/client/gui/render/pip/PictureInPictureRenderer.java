package net.minecraft.client.gui.render.pip;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.platform.TextureUtil;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicResourceBarriers;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class PictureInPictureRenderer<T extends PictureInPictureRenderState> implements AutoCloseable {
	private static final VulkanicResourceBarriers OFFSCREEN_COLOR_WRITES_VISIBLE_TO_TEXTURE_FETCH = VulkanicResourceBarriers.of(
		VulkanicResourceBarriers.Barrier.TEXTURE_FETCH
	);
	protected final MultiBufferSource.BufferSource bufferSource;
	@Nullable
	private GpuTexture texture;
	@Nullable
	private GpuTextureView textureView;
	@Nullable
	private GpuTexture depthTexture;
	@Nullable
	private GpuTextureView depthTextureView;
	@Nullable
	private final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;

	protected PictureInPictureRenderer(MultiBufferSource.BufferSource bufferSource) {
		this.bufferSource = bufferSource;
		this.projectionMatrixBuffer = net.vulkanic.gui.RustGalGuiRenderer.isWholeFrameVulkanActive()
			? null
			: new CachedOrthoProjectionMatrixBuffer("PIP - " + this.getClass().getSimpleName(), -1000.0F, 1000.0F, true);
	}

	public void prepare(T pictureInPictureRenderState, GuiRenderState guiRenderState, int i) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException(
				"Java GUI picture-in-picture rendering is unavailable while Rust owns whole-frame presentation"
			);
		}
		int j = this.getRenderTextureWidth(pictureInPictureRenderState, i);
		int k = this.getRenderTextureHeight(pictureInPictureRenderState, i);
		boolean bl = this.texture == null || this.texture.getWidth(0) != j || this.texture.getHeight(0) != k;
		if (!bl && this.textureIsReadyToBlit(pictureInPictureRenderState)) {
			this.blitTexture(pictureInPictureRenderState, guiRenderState);
		} else {
			GpuBufferSlice previousProjectionMatrix = VulkanicAPI.getProjectionMatrixBuffer();
			ProjectionType previousProjectionType = VulkanicAPI.getProjectionType();
			GpuTextureView previousColorTextureOverride = VulkanicAPI.getOutputColorTextureOverride();
			GpuTextureView previousDepthTextureOverride = VulkanicAPI.getOutputDepthTextureOverride();
			try {
				this.prepareTexturesAndProjection(bl, j, k);
				net.vulkanic.VulkanicAPI.setOutputColorTextureOverride(this.textureView);
				net.vulkanic.VulkanicAPI.setOutputDepthTextureOverride(this.depthTextureView);
				PoseStack poseStack = new PoseStack();
				poseStack.translate(j / 2.0F, this.getTranslateY(pictureInPictureRenderState, k, i), 0.0F);
				float f = i * pictureInPictureRenderState.scale();
				poseStack.scale(f, f, -f);
				this.renderToTexture(pictureInPictureRenderState, poseStack);
				this.bufferSource.endBatch();
				this.afterRenderToTexture(pictureInPictureRenderState, guiRenderState, i);
			} finally {
				net.vulkanic.VulkanicAPI.setOutputColorTextureOverride(previousColorTextureOverride);
				net.vulkanic.VulkanicAPI.setOutputDepthTextureOverride(previousDepthTextureOverride);
				net.vulkanic.VulkanicAPI.setProjectionMatrix(previousProjectionMatrix, previousProjectionType);
			}

			VulkanicAPI.applyResourceBarriers(VulkanicAPI.getCommandContext(), OFFSCREEN_COLOR_WRITES_VISIBLE_TO_TEXTURE_FETCH);
			String string = this.getDebugDumpName(pictureInPictureRenderState, guiRenderState, i);
			if (string != null) {
				this.dumpTextureToAutoCapture(string);
			}
			this.blitTexture(pictureInPictureRenderState, guiRenderState);
		}
	}

	protected void afterRenderToTexture(T pictureInPictureRenderState, GuiRenderState guiRenderState, int i) {
	}

	@Nullable
	protected String getDebugDumpName(T pictureInPictureRenderState, GuiRenderState guiRenderState, int i) {
		return null;
	}

	protected void blitTexture(T pictureInPictureRenderState, GuiRenderState guiRenderState) {
		this.submitBlitTexture(guiRenderState, pictureInPictureRenderState, 0.0F, 1.0F, 1.0F, 0.0F);
	}

	protected int getRenderTextureWidth(T pictureInPictureRenderState, int i) {
		return (pictureInPictureRenderState.x1() - pictureInPictureRenderState.x0()) * i;
	}

	protected int getRenderTextureHeight(T pictureInPictureRenderState, int i) {
		return (pictureInPictureRenderState.y1() - pictureInPictureRenderState.y0()) * i;
	}

	protected final void submitBlitTexture(GuiRenderState guiRenderState, T pictureInPictureRenderState, float f, float g, float h, float i) {
		guiRenderState.submitBlitToCurrentLayer(
			new BlitRenderState(
				RenderPipelines.GUI_TEXTURED,
				TextureSetup.singleTexture(this.textureView),
				pictureInPictureRenderState.pose(),
				pictureInPictureRenderState.x0(),
				pictureInPictureRenderState.y0(),
				pictureInPictureRenderState.x1(),
				pictureInPictureRenderState.y1(),
				f,
				g,
				h,
				i,
				-1,
				pictureInPictureRenderState.scissorArea(),
				this.shaderInputParityPictureInPictureContext(pictureInPictureRenderState)
			)
		);
	}

	private String shaderInputParityPictureInPictureContext(T pictureInPictureRenderState) {
		if (pictureInPictureRenderState instanceof OversizedItemRenderState oversizedItemRenderState) {
			return GuiRenderer.shaderInputParityGuiItemContext("gui-item", oversizedItemRenderState.guiItemRenderState());
		}
		return "gui-pip:renderer=" + VulkanicAPI.shaderInputParityDiagnosticLabel(this.getClass().getSimpleName())
			+ ":state=" + VulkanicAPI.shaderInputParityDiagnosticLabel(pictureInPictureRenderState.getClass().getSimpleName())
			+ ":bounds=" + pictureInPictureRenderState.x0() + "x" + pictureInPictureRenderState.y0()
			+ "-" + pictureInPictureRenderState.x1() + "x" + pictureInPictureRenderState.y1();
	}

	private void prepareTexturesAndProjection(boolean bl, int i, int j) {
		if (this.texture != null && bl) {
			this.texture.close();
			this.texture = null;
			this.textureView.close();
			this.textureView = null;
			this.depthTexture.close();
			this.depthTexture = null;
			this.depthTextureView.close();
			this.depthTextureView = null;
		}

		if (this.texture == null) {
			this.texture = net.vulkanic.VulkanicAPI.createTexture(() -> "UI " + this.getTextureLabel() + " texture", 14, TextureFormat.RGBA8, i, j, 1, 1);
			this.texture.setTextureFilter(FilterMode.NEAREST, false);
			this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
			this.depthTexture = net.vulkanic.VulkanicAPI.createTexture(() -> "UI " + this.getTextureLabel() + " depth texture", 8, TextureFormat.DEPTH32, i, j, 1, 1);
			this.depthTextureView = net.vulkanic.VulkanicAPI.createTextureView(this.depthTexture);
		}

		net.vulkanic.VulkanicAPI.createCommandEncoder().clearColorAndDepthTextures(this.texture, 0, this.depthTexture, 1.0);
		net.vulkanic.VulkanicAPI.setProjectionMatrix(this.getProjectionMatrixBuffer(i, j), ProjectionType.ORTHOGRAPHIC);
	}

	protected GpuBufferSlice getProjectionMatrixBuffer(int i, int j) {
		return this.projectionMatrixBuffer.getBuffer(i, j);
	}

	protected boolean textureIsReadyToBlit(T pictureInPictureRenderState) {
		return false;
	}

	protected final void dumpTextureToAutoCapture(String name) {
		if (this.texture == null) {
			return;
		}

		try {
			Path autoCaptureDir = this.getAutoCaptureDir();
			Files.createDirectories(autoCaptureDir);
			TextureUtil.writeAsPNG(autoCaptureDir, name, this.texture, 0, i -> i);
		} catch (IOException ignored) {
		}
	}

	private Path getAutoCaptureDir() {
		String deterministicCaptureDir = System.getProperty("mattmc.dev.deterministicCameraCapture.screenshotDir", "");
		if (!deterministicCaptureDir.isBlank()) {
			// Retain the already-existing PIP debug target beside the verified
			// game-window frames when deterministic capture explicitly enables it.
			return Path.of(deterministicCaptureDir).toAbsolutePath().normalize();
		}
		Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
		Path parent = gameDir.getParent();
		return (parent != null ? parent : gameDir).resolve("logs/auto-capture");
	}

	protected float getTranslateY(T pictureInPictureRenderState, int i, int j) {
		return i;
	}

	public void close() {
		if (this.texture != null) {
			this.texture.close();
		}

		if (this.textureView != null) {
			this.textureView.close();
		}

		if (this.depthTexture != null) {
			this.depthTexture.close();
		}

		if (this.depthTextureView != null) {
			this.depthTextureView.close();
		}

		if (this.projectionMatrixBuffer != null) {
			this.projectionMatrixBuffer.close();
		}
	}

	public abstract Class<T> getRenderStateClass();

	protected abstract void renderToTexture(T pictureInPictureRenderState, PoseStack poseStack);

	protected abstract String getTextureLabel();
}
