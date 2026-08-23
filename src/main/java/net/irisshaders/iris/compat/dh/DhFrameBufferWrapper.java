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
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris Distant Horizons framebuffer binding is unavailable while Rust owns whole-frame presentation");
		}
		this.framebuffer.bind();
	}

	@Override
	public void addDepthAttachment(int i, boolean b) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris Distant Horizons framebuffer attachment mutation is unavailable while Rust owns whole-frame presentation");
		}
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
