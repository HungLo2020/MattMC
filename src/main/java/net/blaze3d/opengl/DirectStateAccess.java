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
			GlStateManager.incrementTrackedBuffers();
			return net.vulkanic.VulkanicAPI.createBufferDSA(net.vulkanic.VulkanicAPI.getImmediateContext());
		}

		@Override
		void bufferData(int i, long l, int j) {
			net.vulkanic.VulkanicAPI.namedBufferDataDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, l, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			net.vulkanic.VulkanicAPI.namedBufferDataDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, byteBuffer, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			net.vulkanic.VulkanicAPI.namedBufferSubDataDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, (long)j, byteBuffer);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			net.vulkanic.VulkanicAPI.namedBufferStorageDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, l, GlConst.bufferUsageToGlFlag(j));
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			net.vulkanic.VulkanicAPI.namedBufferStorageDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, byteBuffer, GlConst.bufferUsageToGlFlag(j));
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			return net.vulkanic.VulkanicAPI.mapNamedBufferRangeDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, j, k, l);
		}

		@Override
		void unmapBuffer(int i, int j) {
			net.vulkanic.VulkanicAPI.unmapNamedBufferDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i);
		}

		@Override
		public int createFrameBufferObject() {
			return net.vulkanic.VulkanicAPI.createFramebuffer(net.vulkanic.VulkanicAPI.getImmediateContext());
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			net.vulkanic.VulkanicAPI.namedFramebufferColorAttachment0DSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, j, l);
			net.vulkanic.VulkanicAPI.namedFramebufferDepthAttachmentDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, k, l);
			if (m != 0) {
				GlStateManager._glBindFramebuffer(m, i);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			net.vulkanic.VulkanicAPI.blitNamedFramebufferDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, j, k, l, m, n, o, p, q, r, s, t);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			net.vulkanic.VulkanicAPI.flushMappedNamedBufferRangeDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, j, k);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			net.vulkanic.VulkanicAPI.copyNamedBufferSubDataDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, j, k, l, m);
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
			GlStateManager.incrementTrackedBuffers();
			return VulkanicAPI.createBuffer(VulkanicAPI.getImmediateContext());
		}

		@Override
		void bufferData(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, i);
			VulkanicAPI.bufferData(VulkanicAPI.getImmediateContext(), k, l, GlConst.bufferUsageToGlEnum(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, 0);
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, i);
			VulkanicAPI.bufferData(VulkanicAPI.getImmediateContext(), k, byteBuffer, GlConst.bufferUsageToGlEnum(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, 0);
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			int l = this.selectBufferBindTarget(k);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), l, i);
			VulkanicAPI.bufferSubData(VulkanicAPI.getImmediateContext(), l, (long)j, byteBuffer);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), l, 0);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, i);
			VulkanicAPI.bufferStorage(VulkanicAPI.getImmediateContext(), k, l, GlConst.bufferUsageToGlFlag(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, 0);
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, i);
			VulkanicAPI.bufferStorage(VulkanicAPI.getImmediateContext(), k, byteBuffer, GlConst.bufferUsageToGlFlag(j));
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, 0);
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			int n = this.selectBufferBindTarget(m);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), n, i);
			ByteBuffer byteBuffer = VulkanicAPI.mapBuffer(VulkanicAPI.getImmediateContext(), n, j, k, l);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), n, 0);
			return byteBuffer;
		}

		@Override
		void unmapBuffer(int i, int j) {
			int k = this.selectBufferBindTarget(j);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, i);
			VulkanicAPI.unmapBuffer(VulkanicAPI.getImmediateContext(), k);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), k, 0);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			int m = this.selectBufferBindTarget(l);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), m, i);
			VulkanicAPI.flushMappedBufferRange(VulkanicAPI.getImmediateContext(), m, j, k);
			VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), m, 0);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindCopyReadBuffer(ctx, i);
			VulkanicAPI.bindCopyWriteBuffer(ctx, j);
			VulkanicAPI.copyBufferSubDataBetweenCopyTargets(ctx, k, l, m);
			VulkanicAPI.bindCopyReadBuffer(ctx, 0);
			VulkanicAPI.bindCopyWriteBuffer(ctx, 0);
		}

		@Override
		public int createFrameBufferObject() {
			return VulkanicAPI.createFramebuffer(VulkanicAPI.getImmediateContext());
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			int n = m == 0 ? VulkanicAPI.GL_FRAMEBUFFER : m;
			int o = GlStateManager.getFrameBuffer(n);
			GlStateManager._glBindFramebuffer(n, i);
			VulkanicAPI.framebufferColorAttachment0Texture2D(VulkanicAPI.getImmediateContext(), n, j, l);
			VulkanicAPI.framebufferDepthAttachmentTexture2D(VulkanicAPI.getImmediateContext(), n, k, l);
			if (m == 0) {
				GlStateManager._glBindFramebuffer(n, o);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			int u = GlStateManager.getFrameBuffer(VulkanicAPI.GL_READ_FRAMEBUFFER);
			int v = GlStateManager.getFrameBuffer(VulkanicAPI.GL_DRAW_FRAMEBUFFER);
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_READ_FRAMEBUFFER, i);
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_DRAW_FRAMEBUFFER, j);
			VulkanicAPI.blitFramebuffer(VulkanicAPI.getImmediateContext(), k, l, m, n, o, p, q, r, s, t);
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_READ_FRAMEBUFFER, u);
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_DRAW_FRAMEBUFFER, v);
		}
	}
}
