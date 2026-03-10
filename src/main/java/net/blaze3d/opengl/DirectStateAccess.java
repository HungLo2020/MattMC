package net.blaze3d.opengl;

import net.blaze3d.GraphicsWorkarounds;
import java.nio.ByteBuffer;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DirectStateAccess {
	public static DirectStateAccess create(GraphicsCapabilities gLCapabilities, Set<String> set, GraphicsWorkarounds graphicsWorkarounds) {
		if (gLCapabilities.GL_ARB_direct_state_access && GlDevice.USE_GL_ARB_direct_state_access && !graphicsWorkarounds.isGlOnDx12()) {
			set.add("GL_ARB_direct_state_access");
			return new DirectStateAccess.Core();
		} else {
			return new DirectStateAccess.Emulated();
		}
	}

	abstract int createBuffer();

	abstract void bufferData(int i, long l, int j);

	abstract void bufferData(int i, ByteBuffer byteBuffer, int j);

	abstract void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k);

	abstract void bufferStorage(int i, long l, int j);

	abstract void bufferStorage(int i, ByteBuffer byteBuffer, int j);

	@Nullable
	abstract ByteBuffer mapBufferRange(int i, int j, int k, int l, int m);

	abstract void unmapBuffer(int i, int j);

	abstract int createFrameBufferObject();

	abstract void bindFrameBufferTextures(int i, int j, int k, int l, int m);

	abstract void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t);

	abstract void flushMappedBufferRange(int i, int j, int k, int l);

	abstract void copyBufferSubData(int i, int j, int k, int l, int m);

	@Environment(EnvType.CLIENT)
	static class Core extends DirectStateAccess {
		@Override
		int createBuffer() {
			net.irisshaders.iris.gl.IrisRenderSystem.incrementTrackedBuffers();
			return net.vulkanic.VulkanicAPI.createBufferDSA(net.vulkanic.VulkanicAPI.getCommandContext());
		}

		@Override
		void bufferData(int i, long l, int j) {
			net.vulkanic.VulkanicAPI.namedBufferDataDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, l, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			net.vulkanic.VulkanicAPI.namedBufferDataDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, byteBuffer, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			net.vulkanic.VulkanicAPI.namedBufferSubDataDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, (long)j, byteBuffer);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			net.vulkanic.VulkanicAPI.namedBufferStorageDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, l, GlConst.bufferUsageToGlFlag(j));
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			net.vulkanic.VulkanicAPI.namedBufferStorageDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, byteBuffer, GlConst.bufferUsageToGlFlag(j));
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			return net.vulkanic.VulkanicAPI.mapNamedBufferRangeDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, j, k, l);
		}

		@Override
		void unmapBuffer(int i, int j) {
			net.vulkanic.VulkanicAPI.unmapNamedBufferDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i);
		}

		@Override
		public int createFrameBufferObject() {
			return net.vulkanic.VulkanicAPI.createFramebuffer(net.vulkanic.VulkanicAPI.getCommandContext());
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			net.vulkanic.VulkanicAPI.namedFramebufferColorAttachment0DSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, j, l);
			net.vulkanic.VulkanicAPI.namedFramebufferDepthAttachmentDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, k, l);
			if (m != 0) {
				net.vulkanic.VulkanicAPI.bindFramebuffer(net.vulkanic.VulkanicAPI.getCommandContext(), m, i);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			net.vulkanic.VulkanicAPI.blitNamedFramebufferDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, j, k, l, m, n, o, p, q, r, s, t);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			net.vulkanic.VulkanicAPI.flushMappedNamedBufferRangeDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, j, k);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			net.vulkanic.VulkanicAPI.copyNamedBufferSubDataDSA(net.vulkanic.VulkanicAPI.getCommandContext(), i, j, k, l, m);
		}
	}

	@Environment(EnvType.CLIENT)
	static class Emulated extends DirectStateAccess {
		private int selectBufferBindTarget(int i) {
			if ((i & 32) != 0) {
				return VulkanicAPI.GL_ARRAY_BUFFER;
			} else if ((i & 64) != 0) {
				return VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER;
			} else {
				return (i & 128) != 0 ? VulkanicAPI.GL_UNIFORM_BUFFER : VulkanicAPI.GL_COPY_WRITE_BUFFER;
			}
		}

		@Override
		int createBuffer() {
			net.irisshaders.iris.gl.IrisRenderSystem.incrementTrackedBuffers();
			return VulkanicAPI.createBuffer(VulkanicAPI.getCommandContext());
		}

		@Override
		void bufferData(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, i);
			VulkanicAPI.bufferData(VulkanicAPI.getCommandContext(), k, l, GlConst.bufferUsageToGlEnum(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, 0);
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, i);
			VulkanicAPI.bufferData(VulkanicAPI.getCommandContext(), k, byteBuffer, GlConst.bufferUsageToGlEnum(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, 0);
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			int l = this.selectBufferBindTarget(k);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), l, i);
			VulkanicAPI.bufferSubData(VulkanicAPI.getCommandContext(), l, (long)j, byteBuffer);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), l, 0);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, i);
			VulkanicAPI.bufferStorage(VulkanicAPI.getCommandContext(), k, l, GlConst.bufferUsageToGlFlag(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, 0);
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, i);
			VulkanicAPI.bufferStorage(VulkanicAPI.getCommandContext(), k, byteBuffer, GlConst.bufferUsageToGlFlag(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, 0);
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			int n = this.selectBufferBindTarget(m);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), n, i);
			ByteBuffer byteBuffer = VulkanicAPI.mapBuffer(VulkanicAPI.getCommandContext(), n, j, k, l);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), n, 0);
			return byteBuffer;
		}

		@Override
		void unmapBuffer(int i, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, i);
			VulkanicAPI.unmapBuffer(VulkanicAPI.getCommandContext(), k);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), k, 0);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			int m = this.selectBufferBindTarget(l);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), m, i);
			VulkanicAPI.flushMappedBufferRange(VulkanicAPI.getCommandContext(), m, j, k);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), m, 0);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.bindCopyReadBuffer(ctx, i);
			VulkanicAPI.bindCopyWriteBuffer(ctx, j);
			VulkanicAPI.copyBufferSubDataBetweenCopyTargets(ctx, k, l, m);
			VulkanicAPI.bindCopyReadBuffer(ctx, 0);
			VulkanicAPI.bindCopyWriteBuffer(ctx, 0);
		}

		@Override
		public int createFrameBufferObject() {
			return VulkanicAPI.createFramebuffer(VulkanicAPI.getCommandContext());
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			int n = m == 0 ? VulkanicAPI.GL_FRAMEBUFFER : m;
			net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
			int o = VulkanicAPI.getFramebufferBinding(n);
			VulkanicAPI.bindFramebuffer(ctx, n, i);
			VulkanicAPI.framebufferColorAttachment0Texture2D(VulkanicAPI.getCommandContext(), n, j, l);
			VulkanicAPI.framebufferDepthAttachmentTexture2D(VulkanicAPI.getCommandContext(), n, k, l);
			if (m == 0) {
				VulkanicAPI.bindFramebuffer(ctx, n, o);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
			int u = VulkanicAPI.getReadFramebufferBinding();
			int v = VulkanicAPI.getDrawFramebufferBinding();
			VulkanicAPI.bindReadFramebuffer(ctx, i);
			VulkanicAPI.bindDrawFramebuffer(ctx, j);
			VulkanicAPI.blitFramebuffer(VulkanicAPI.getCommandContext(), k, l, m, n, o, p, q, r, s, t);
			VulkanicAPI.bindReadFramebuffer(ctx, u);
			VulkanicAPI.bindDrawFramebuffer(ctx, v);
		}
	}
}
