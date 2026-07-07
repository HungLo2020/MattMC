package net.irisshaders.iris.compat.dh;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.vulkanic.VulkanicAPI;

public class DhFrameBufferWrapper implements IDhApiFramebuffer {
	private final GlFramebuffer framebuffer;


	public DhFrameBufferWrapper(GlFramebuffer framebuffer) {
		this.framebuffer = framebuffer;
	}


	@Override
	public boolean overrideThisFrame() {
		return true;
	}

	@Override
	public void bind() {
		this.framebuffer.bind();
	}

	@Override
	public void addDepthAttachment(int i, boolean b) {
		this.framebuffer.addDepthAttachmentBypass(i, b);
	}

	@Override
	public int getId() {
		return this.framebuffer.getId();
	}

	@Override
	public int getStatus() {
		this.bind();
		return IrisRenderSystem.checkFramebufferStatus();
	}

	@Override
	public void addColorAttachment(int i, int i1) {
		// ignore
	}

	@Override
	public void destroy() {
		// ignore
		//this.framebuffer.destroy();
	}

}
