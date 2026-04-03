package net.irisshaders.iris.targets;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.vulkanic.VulkanicAPI;
import org.joml.Vector4f;

import java.util.Objects;
import java.util.function.IntSupplier;

public class ClearPass {
	private final Vector4f color;
	private final IntSupplier viewportX;
	private final IntSupplier viewportY;
	private final GlFramebuffer framebuffer;

	public ClearPass(Vector4f color, IntSupplier viewportX, IntSupplier viewportY, GlFramebuffer framebuffer) {
		this.color = color;
		this.viewportX = viewportX;
		this.viewportY = viewportY;
		this.framebuffer = framebuffer;
	}

	public void execute(Vector4f defaultClearColor) {
		var ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.setDynamicViewport(ctx, 0, 0, viewportX.getAsInt(), viewportY.getAsInt());
		framebuffer.bind();

		Vector4f color = Objects.requireNonNull(defaultClearColor);

		if (this.color != null) {
			color = this.color;
		}

		IrisRenderSystem.clearColor(color.x, color.y, color.z, color.w);
		VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx);
	}

	public GlFramebuffer getFramebuffer() {
		return framebuffer;
	}
}
