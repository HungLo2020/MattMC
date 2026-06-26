package net.irisshaders.iris.gl.framebuffer;

import net.blaze3d.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIntegerQuery;
import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.VulkanicResourceUsage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class GlFramebuffer extends GlResource {
	private final Int2IntMap attachments;
	private final int maxDrawBuffers;
	private final int maxColorAttachments;
	private boolean hasDepthAttachment;
	private int depthAttachmentTexture;
	private int[] drawBuffers;

	public GlFramebuffer() {
		super(IrisRenderSystem.createFramebuffer());

		this.attachments = new Int2IntArrayMap();
		this.maxDrawBuffers = VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.MAX_DRAW_BUFFERS);
		this.maxColorAttachments = VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
		this.depthAttachmentTexture = 0;
		this.drawBuffers = new int[0];
	}

	public void addDepthAttachment(GpuTexture texture) {
		int fb = getGlId();

		if (texture.getFormat().hasStencilAspect()) {
			IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT, net.vulkanic.VulkanicCoreAPI.textureId(texture), 0);
		} else {
			IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_DEPTH_ATTACHMENT, net.vulkanic.VulkanicCoreAPI.textureId(texture), 0);
		}

		this.hasDepthAttachment = true;
		this.depthAttachmentTexture = net.vulkanic.VulkanicCoreAPI.textureId(texture);
	}

	public void addDepthAttachmentBypass(int texture) {
		int fb = getGlId();

		IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.GL_DEPTH_ATTACHMENT, texture, 0);

		this.hasDepthAttachment = true;
		this.depthAttachmentTexture = texture;
	}

	public void addColorAttachment(int index, int texture) {
		int fb = getGlId();

		IrisRenderSystem.framebufferTexture2D(fb, VulkanicAPI.colorAttachment(index), texture, 0);
		attachments.put(index, texture);
	}

	public void noDrawBuffers() {
		IrisRenderSystem.drawBuffers(getGlId(), new int[]{VulkanicAPI.GL_NONE});
		this.drawBuffers = new int[]{VulkanicAPI.GL_NONE};
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

			glBuffers[index++] = VulkanicAPI.colorAttachment(buffer);
		}

		IrisRenderSystem.drawBuffers(getGlId(), glBuffers);
		this.drawBuffers = Arrays.copyOf(buffers, buffers.length);
	}

	public void readBuffer(int buffer) {
		IrisRenderSystem.readBuffer(getGlId(), VulkanicAPI.colorAttachment(buffer));
	}

	public int getColorAttachment(int index) {
		return attachments.get(index);
	}

	public boolean hasDepthAttachment() {
		return hasDepthAttachment;
	}

	public VulkanicRenderTargetDescriptor createRenderTargetDescriptor(Supplier<String> label) {
		return createRenderTargetDescriptor(label, -1, -1);
	}

	public VulkanicRenderTargetDescriptor createRenderTargetDescriptor(Supplier<String> label, int width, int height) {
		int[] activeDrawBuffers = this.drawBuffers.length > 0 ? this.drawBuffers : new int[]{0};
		List<VulkanicRenderTargetDescriptor.ColorAttachment> colors = new ArrayList<>(activeDrawBuffers.length);
		for (int drawBuffer : activeDrawBuffers) {
			if (drawBuffer == VulkanicAPI.GL_NONE) {
				continue;
			}
			int texture = attachments.get(drawBuffer);
			if (texture <= 0) {
				throw new IllegalStateException("Iris framebuffer draw buffer " + drawBuffer + " has no color attachment");
			}
			colors.add(colorAttachmentDescriptor(texture));
		}

		VulkanicRenderTargetDescriptor.DepthAttachment depth = null;
		if (hasDepthAttachment) {
			if (depthAttachmentTexture <= 0) {
				throw new IllegalStateException("Iris framebuffer has depth enabled but no depth texture");
			}
			depth = depthAttachmentDescriptor(depthAttachmentTexture);
		}

		return new VulkanicRenderTargetDescriptor(label, colors, depth, width, height);
	}

	private static VulkanicRenderTargetDescriptor.ColorAttachment colorAttachmentDescriptor(int texture) {
		return new VulkanicRenderTargetDescriptor.ColorAttachment(
			texture,
			VulkanicRenderPassDescriptor.LoadOp.LOAD,
			VulkanicRenderPassDescriptor.StoreOp.STORE,
			OptionalInt.empty(),
			VulkanicResourceUsage.SAMPLED_READ,
			VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE,
			VulkanicResourceUsage.SAMPLED_READ
		);
	}

	private static VulkanicRenderTargetDescriptor.DepthAttachment depthAttachmentDescriptor(int texture) {
		return new VulkanicRenderTargetDescriptor.DepthAttachment(
			texture,
			VulkanicRenderPassDescriptor.LoadOp.LOAD,
			VulkanicRenderPassDescriptor.StoreOp.STORE,
			OptionalDouble.empty(),
			VulkanicResourceUsage.SAMPLED_READ,
			VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE,
			VulkanicResourceUsage.SAMPLED_READ
		);
	}

	public void bind() {
		VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), getGlId());
	}

	public void bindAsReadBuffer() {
		VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getCommandContext(), getGlId());
	}

	public void bindAsDrawBuffer() {
		VulkanicAPI.bindDrawFramebuffer(VulkanicAPI.getCommandContext(), getGlId());
	}

	protected void destroyInternal() {
		int framebuffer = getGlId();
		if (VulkanicAPI.getReadFramebufferBinding() == framebuffer) {
			VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getCommandContext(), 0);
		}

		if (VulkanicAPI.getDrawFramebufferBinding() == framebuffer) {
			VulkanicAPI.bindDrawFramebuffer(VulkanicAPI.getCommandContext(), 0);
		}

		VulkanicAPI.deleteFramebuffer(VulkanicAPI.getCommandContext(), framebuffer);
	}

	public int getStatus() {
		bind();

		return IrisRenderSystem.checkFramebufferStatus();
	}

	public int getId() {
		return getGlId();
	}
}
