package net.irisshaders.iris.gl.framebuffer;

import net.blaze3d.opengl.GlStateManager;
import net.blaze3d.opengl.GlTexture;
import net.blaze3d.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.VulkanicAPI;

public class GlFramebuffer extends GlResource {
	private final Int2IntMap attachments;
	private final int maxDrawBuffers;
	private final int maxColorAttachments;
	private boolean hasDepthAttachment;

	public GlFramebuffer() {
		super(IrisRenderSystem.createFramebuffer());

		this.attachments = new Int2IntArrayMap();
		this.maxDrawBuffers = VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}

	public void addDepthAttachment(GpuTexture texture) {
		int fb = getGlId();

		// TODO: NeoForge 1.21.5
		//if (texture.getFormat().hasStencilAspect()) {
		//	IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT, VulkanicAPI.GL_TEXTURE_2D, texture, 0);
		//} else {
			IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_DEPTH_ATTACHMENT, VulkanicAPI.GL_TEXTURE_2D, ((GlTexture) texture).glId(), 0);
		//}

		this.hasDepthAttachment = true;
	}

	public void addDepthAttachmentBypass(int texture) {
		int fb = getGlId();

		IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_DEPTH_ATTACHMENT, VulkanicAPI.GL_TEXTURE_2D, texture, 0);

		this.hasDepthAttachment = true;
	}

	public void addColorAttachment(int index, int texture) {
		int fb = getGlId();

		IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0 + index, VulkanicAPI.GL_TEXTURE_2D, texture, 0);
		attachments.put(index, texture);
	}

	public void noDrawBuffers() {
		IrisRenderSystem.drawBuffers(getGlId(), new int[]{VulkanicAPI.GL_NONE});
	}

	public void drawBuffers(int[] buffers) {
		int[] glBuffers = new int[buffers.length];
		int index = 0;

		if (buffers.length > maxDrawBuffers) {
			throw new IllegalArgumentException("Cannot write to more than " + maxDrawBuffers + " draw buffers on this GPU");
		}

		for (int buffer : buffers) {
			if (buffer >= maxColorAttachments) {
				throw new IllegalArgumentException("Only " + maxColorAttachments + " color attachments are supported on this GPU, but an attempt was made to write to a color attachment with index " + buffer);
			}

			glBuffers[index++] = VulkanicAPI.GL_COLOR_ATTACHMENT0 + buffer;
		}

		IrisRenderSystem.drawBuffers(getGlId(), glBuffers);
	}

	public void readBuffer(int buffer) {
		IrisRenderSystem.readBuffer(getGlId(), VulkanicAPI.GL_COLOR_ATTACHMENT0 + buffer);
	}

	public int getColorAttachment(int index) {
		return attachments.get(index);
	}

	public boolean hasDepthAttachment() {
		return hasDepthAttachment;
	}

	public void bind() {
		GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, getGlId());
	}

	public void bindAsReadBuffer() {
		GlStateManager._glBindFramebuffer(VulkanicAPI.GL_READ_FRAMEBUFFER, getGlId());
	}

	public void bindAsDrawBuffer() {
		GlStateManager._glBindFramebuffer(VulkanicAPI.GL_DRAW_FRAMEBUFFER, getGlId());
	}

	protected void destroyInternal() {
		GlStateManager._glDeleteFramebuffers(getGlId());
	}

	public int getStatus() {
		bind();

		return IrisRenderSystem.checkFramebufferStatus(VulkanicAPI.GL_FRAMEBUFFER);
	}

	public int getId() {
		return getGlId();
	}
}
