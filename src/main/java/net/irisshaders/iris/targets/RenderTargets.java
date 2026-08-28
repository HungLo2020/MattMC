package net.irisshaders.iris.targets;

import com.google.common.collect.ImmutableSet;
import net.blaze3d.textures.AddressMode;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.TextureFormat;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicCoreAPI;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RenderTargets {
	private final RenderTarget[] targets;
	private GpuTexture noTranslucents;
	private GpuTexture noHand;
	private final GlFramebuffer depthSourceFb;
	private final GlFramebuffer noTranslucentsDestFb;
	private final GlFramebuffer noHandDestFb;
	private final List<GlFramebuffer> ownedFramebuffers;
	private final Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> targetSettingsMap;
	private final PackDirectives packDirectives;
	private GpuTexture currentDepthTexture;
	private DepthBufferFormat currentDepthFormat;
	private DepthCopyStrategy copyStrategy;
	private int cachedWidth;
	private int cachedHeight;
	private boolean fullClearRequired;
	private boolean translucentDepthDirty;
	private boolean handDepthDirty;

	private int cachedDepthBufferVersion;
	private boolean destroyed;

	public RenderTargets(int width, int height, GpuTexture depthTexture, int depthBufferVersion, DepthBufferFormat depthFormat, Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets, PackDirectives packDirectives) {
		ensureJavaRenderTargetsAvailable();
		targets = new RenderTarget[renderTargets.size()];

		targetSettingsMap = renderTargets;
		this.packDirectives = packDirectives;

		this.currentDepthTexture = depthTexture;
		this.currentDepthFormat = depthFormat;
		this.copyStrategy = DepthCopyStrategy.fastestDepthSnapshot(false);

		this.cachedWidth = width;
		this.cachedHeight = height;
		this.cachedDepthBufferVersion = depthBufferVersion;

		this.ownedFramebuffers = new ArrayList<>();

		// NB: Make sure all buffers are cleared so that they don't contain undefined
		// data. Otherwise very weird things can happen.
		fullClearRequired = true;

		this.depthSourceFb = createFramebufferWritingToMain(new int[]{0});

		TextureFormat snapshotDepthFormat = snapshotDepthFormat(depthFormat);

		this.noTranslucents = VulkanicAPI.createTexture("Depth / Opaque", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, snapshotDepthFormat, width, height, 1, 1);
		this.noHand = VulkanicAPI.createTexture("Depth / Before Hand", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, snapshotDepthFormat, width, height, 1, 1);

		this.noTranslucents.setTextureFilter(FilterMode.NEAREST, false);
		this.noHand.setTextureFilter(FilterMode.NEAREST, false);
		this.noTranslucents.setAddressMode(AddressMode.CLAMP_TO_EDGE);
		this.noHand.setAddressMode(AddressMode.CLAMP_TO_EDGE);
		this.noTranslucentsDestFb = createFramebufferWritingToMain(new int[]{0});
		this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents);

		this.noHandDestFb = createFramebufferWritingToMain(new int[]{0});
		this.noHandDestFb.addDepthAttachment(this.noHand);

		this.translucentDepthDirty = true;
		this.handDepthDirty = true;
	}

	public void destroy() {
		destroyed = true;

		for (GlFramebuffer owned : ownedFramebuffers) {
			owned.destroy();
		}

		for (RenderTarget target : targets) {
			if (target != null) {
				target.destroy();
			}
		}

		noTranslucents.close();
		noHand.close();
	}

	public int getRenderTargetCount() {
		return targets.length;
	}

	public RenderTarget get(int index) {
		if (destroyed) {
			throw new IllegalStateException("Tried to use destroyed RenderTargets");
		}

		if (targets[index] == null) {
			return null;
		}

		return targets[index];
	}

	public RenderTarget getOrCreate(int index) {
		if (destroyed) {
			throw new IllegalStateException("Tried to use destroyed RenderTargets");
		}

		if (targets[index] != null) return targets[index];

		create(index);

		return targets[index];
	}

	private void create(int index) {
		PackRenderTargetDirectives.RenderTargetSettings settings = targetSettingsMap.get(index);
		Vector2i dimensions = packDirectives.getTextureScaleOverride(index, cachedWidth, cachedHeight);
		targets[index] = RenderTarget.builder().setDimensions(dimensions.x, dimensions.y)
			.setName("colortex" + index)
			.setInternalFormat(settings.getInternalFormat())
			.setPixelFormat(settings.getInternalFormat().getPixelFormat()).build();
	}

	public GpuTexture getDepthTexture() {
		return currentDepthTexture;
	}

	public GpuTexture getDepthTextureNoTranslucents() {
		if (destroyed) {
			throw new IllegalStateException("Tried to use destroyed RenderTargets");
		}

		return noTranslucents;
	}

	public GpuTexture getDepthTextureNoHand() {
		return noHand;
	}

	public boolean resizeIfNeeded(int newDepthBufferVersion, GpuTexture newDepthTextureId, int newWidth, int newHeight, DepthBufferFormat newDepthFormat, PackDirectives packDirectives) {
		ensureJavaRenderTargetsAvailable();
		boolean recreateDepth = false;
		if (cachedDepthBufferVersion != newDepthBufferVersion) {
			recreateDepth = true;
			currentDepthTexture = newDepthTextureId;
			cachedDepthBufferVersion = newDepthBufferVersion;
		}

		boolean sizeChanged = newWidth != cachedWidth || newHeight != cachedHeight;
		boolean depthFormatChanged = newDepthFormat != currentDepthFormat;

		if (depthFormatChanged) {
			currentDepthFormat = newDepthFormat;
			copyStrategy = DepthCopyStrategy.fastestDepthSnapshot(false);
		}

		if (depthFormatChanged || sizeChanged) {
			// Reallocate depth buffers
			noTranslucents.close();
			noHand.close();

			TextureFormat snapshotDepthFormat = snapshotDepthFormat(newDepthFormat);
			this.noTranslucents = VulkanicAPI.createTexture("Depth / Opaque", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, snapshotDepthFormat, newWidth, newHeight, 1, 1);
			this.noHand = VulkanicAPI.createTexture("Depth / Before Hand", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, snapshotDepthFormat, newWidth, newHeight, 1, 1);
			this.noTranslucents.setTextureFilter(FilterMode.NEAREST, false);
			this.noHand.setTextureFilter(FilterMode.NEAREST, false);
			this.noTranslucents.setAddressMode(AddressMode.CLAMP_TO_EDGE);
			this.noHand.setAddressMode(AddressMode.CLAMP_TO_EDGE);
			this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents);
			this.noHandDestFb.addDepthAttachment(this.noHand);

			this.translucentDepthDirty = true;
			this.handDepthDirty = true;

			recreateDepth = true;
		}

		if (recreateDepth) {
			// Re-attach the depth textures with the new depth texture ID, since Minecraft re-creates
			// the depth texture when resizing its render targets.
			//
			// I'm not sure if our framebuffers holding on to the old depth texture between frames
			// could be a concern, in the case of resizing and similar. I think it should work
			// based on what I've seen of the spec, though - it seems like deleting a texture
			// automatically detaches it from its framebuffers.
			for (GlFramebuffer framebuffer : ownedFramebuffers) {
				if (framebuffer.hasDepthAttachment()) {
					framebuffer.addDepthAttachment(newDepthTextureId);
				}
			}
		}

		if (sizeChanged) {
			cachedWidth = newWidth;
			cachedHeight = newHeight;

			for (int i = 0; i < targets.length; i++) {
				if (targets[i] != null) {
					targets[i].resize(packDirectives.getTextureScaleOverride(i, newWidth, newHeight));
				}
			}

			fullClearRequired = true;
		}

		return sizeChanged;
	}

	private static void ensureJavaRenderTargetsAvailable() {
		if (VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris render-target resources are unavailable on the Rust Vulkan route");
		}
	}

	private static TextureFormat snapshotDepthFormat(DepthBufferFormat sourceDepthFormat) {
		if (!VulkanicAPI.isVulkanBackendSelected()) {
			return TextureFormat.DEPTH32;
		}

		return switch (sourceDepthFormat) {
			case DEPTH_STENCIL, DEPTH24_STENCIL8 -> TextureFormat.DEPTH24_STENCIL8;
			case DEPTH32F_STENCIL8 -> TextureFormat.DEPTH32F_STENCIL8;
			default -> TextureFormat.DEPTH32;
		};
	}

	public void copyPreTranslucentDepth() {
		if (translucentDepthDirty) {
			translucentDepthDirty = false;
		}

		copyStrategy.copy(depthSourceFb, VulkanicCoreAPI.textureId(getDepthTexture()), noTranslucentsDestFb, VulkanicCoreAPI.textureId(noTranslucents),
			getCurrentWidth(), getCurrentHeight());
	}

	public void copyPreHandDepth() {
		if (handDepthDirty) {
			handDepthDirty = false;
		}

		copyStrategy.copy(depthSourceFb, VulkanicCoreAPI.textureId(getDepthTexture()), noHandDestFb, VulkanicCoreAPI.textureId(noHand),
			getCurrentWidth(), getCurrentHeight());
	}

	public boolean isFullClearRequired() {
		return fullClearRequired;
	}

	public void onFullClear() {
		fullClearRequired = false;
	}

	public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
		return createFullFramebuffer(false, drawBuffers);
	}

	public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
		return createFullFramebuffer(true, drawBuffers);
	}

	public GlFramebuffer createClearFramebuffer(boolean alt, int[] clearBuffers) {
		ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

		if (!alt) {
			stageWritesToMain = invert(ImmutableSet.of(), clearBuffers);
		}

		return createColorFramebuffer(stageWritesToMain, clearBuffers);
	}

	private ImmutableSet<Integer> invert(ImmutableSet<Integer> base, int[] relevant) {
		ImmutableSet.Builder<Integer> inverted = ImmutableSet.builder();

		for (int i : relevant) {
			if (!base.contains(i)) {
				inverted.add(i);
			}
		}

		return inverted.build();
	}

	private GlFramebuffer createEmptyFramebuffer() {
		GlFramebuffer framebuffer = new GlFramebuffer();
		ownedFramebuffers.add(framebuffer);

		framebuffer.addDepthAttachment(currentDepthTexture);

		// NB: Before OpenGL 3.0, all framebuffers are required to have a color
		// attachment no matter what.
		framebuffer.addColorAttachment(0, getOrCreate(0).getMainTexture());
		framebuffer.noDrawBuffers();

		return framebuffer;
	}

	public GlFramebuffer createDHFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);

		return createColorFramebuffer(stageWritesToMain, drawBuffers);
	}


	public GlFramebuffer createGbufferFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);

		GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);

		framebuffer.addDepthAttachment(currentDepthTexture);

		return framebuffer;
	}

	private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

		if (!clearsAlt) {
			stageWritesToMain = invert(ImmutableSet.of(), drawBuffers);
		}

		return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);
	}

	public GlFramebuffer createColorFramebufferWithDepth(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);

		framebuffer.addDepthAttachment(currentDepthTexture);

		return framebuffer;
	}

	public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
		}

		GlFramebuffer framebuffer = new GlFramebuffer();
		ownedFramebuffers.add(framebuffer);

		int[] actualDrawBuffers = new int[drawBuffers.length];

		for (int i = 0; i < drawBuffers.length; i++) {
			actualDrawBuffers[i] = i;

			if (drawBuffers[i] >= getRenderTargetCount()) {
				// TODO: This causes resource leaks, also we should really verify this in the shaderpack parser...
				framebuffer.destroy();
				ownedFramebuffers.remove(framebuffer);
				throw new IllegalStateException("Render target with index " + drawBuffers[i] + " is not supported, only "
					+ getRenderTargetCount() + " render targets are supported.");
			}

			RenderTarget target = this.getOrCreate(drawBuffers[i]);

			boolean writesMain = stageWritesToMain.contains(drawBuffers[i]);
			int textureId = writesMain ? target.getMainTexture() : target.getAltTexture();

			framebuffer.addColorAttachment(i, textureId);
			VulkanicAPI.recordDiagnosticIrisColorAttachment(
				framebuffer.getId(),
				i,
				drawBuffers[i],
				textureId,
				writesMain,
				"iris-render-targets-createColorFramebuffer"
			);
		}

		framebuffer.drawBuffers(actualDrawBuffers);
		framebuffer.readBuffer(0);


		int status = framebuffer.getStatus();
		if (!VulkanicAPI.isFramebufferComplete(status)) {
			throw new IllegalStateException("Unexpected error while creating framebuffer: Draw buffers " + Arrays.toString(actualDrawBuffers) + " Status: " + status);
		}

		return framebuffer;
	}

	public void destroyFramebuffer(GlFramebuffer framebuffer) {
		framebuffer.destroy();
		ownedFramebuffers.remove(framebuffer);
	}

	public int getCurrentWidth() {
		return cachedWidth;
	}

	public int getCurrentHeight() {
		return cachedHeight;
	}

	public void createIfUnsure(int index) {
		if (targets[index] == null) {
			create(index);
		}
	}
}
