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
			GlStateManager.numBuffers++;
			GlStateManager.PLOT_BUFFERS.setValue(GlStateManager.numBuffers);
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
			net.vulkanic.VulkanicAPI.namedFramebufferTextureDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, 36064, j, l); // GL_COLOR_ATTACHMENT0
			net.vulkanic.VulkanicAPI.namedFramebufferTextureDSA(net.vulkanic.VulkanicAPI.getImmediateContext(), i, 36096, k, l); // GL_DEPTH_ATTACHMENT
			if (m != 0) {
				VulkanicAPI.bindFramebuffer(VulkanicAPI.getImmediateContext(), m, i);
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
				return 34962;
			} else if ((i & 64) != 0) {
				return 34963;
			} else {
				return (i & 128) != 0 ? 35345 : 36663;
			}
		}

		@Override
		int createBuffer() {
			GlStateManager.numBuffers++;
			GlStateManager.PLOT_BUFFERS.setValue(GlStateManager.numBuffers);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			return VulkanicAPI.createBuffer(ctx);
		}

		@Override
		void bufferData(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, k, i);
			VulkanicAPI.bufferData(ctx, k, l, GlConst.bufferUsageToGlEnum(j));
			VulkanicAPI.bindBuffer(ctx, k, 0);
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, k, i);
			VulkanicAPI.bufferData(ctx, k, byteBuffer, GlConst.bufferUsageToGlEnum(j));
			VulkanicAPI.bindBuffer(ctx, k, 0);
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			int l = this.selectBufferBindTarget(k);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, l, i);
			VulkanicAPI.bufferSubData(ctx, l, (long)j, byteBuffer);
			VulkanicAPI.bindBuffer(ctx, l, 0);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, k, i);
			VulkanicAPI.bufferStorage(ctx, k, l, GlConst.bufferUsageToGlFlag(j));
			VulkanicAPI.bindBuffer(ctx, k, 0);
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, k, i);
			VulkanicAPI.bufferStorage(ctx, k, byteBuffer, GlConst.bufferUsageToGlFlag(j));
			VulkanicAPI.bindBuffer(ctx, k, 0);
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			int n = this.selectBufferBindTarget(m);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, n, i);
			ByteBuffer byteBuffer = VulkanicAPI.mapBuffer(ctx, n, j, k, l);
			VulkanicAPI.bindBuffer(ctx, n, 0);
			return byteBuffer;
		}

		@Override
		void unmapBuffer(int i, int j) {
			int k = this.selectBufferBindTarget(j);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, k, i);
			VulkanicAPI.unmapBuffer(ctx, k);
			VulkanicAPI.bindBuffer(ctx, k, 0);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			int m = this.selectBufferBindTarget(l);
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, m, i);
			VulkanicAPI.flushMappedBufferRange(ctx, m, j, k);
			VulkanicAPI.bindBuffer(ctx, m, 0);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VulkanicAPI.bindBuffer(ctx, 36662, i);
			VulkanicAPI.bindBuffer(ctx, 36663, j);
			VulkanicAPI.copyBufferSubData(ctx, 36662, 36663, k, l, m);
			VulkanicAPI.bindBuffer(ctx, 36662, 0);
			VulkanicAPI.bindBuffer(ctx, 36663, 0);
		}

		@Override
		public int createFrameBufferObject() {
			return VulkanicAPI.createFramebuffer(VulkanicAPI.getImmediateContext());
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			int n = m == 0 ? '\u8CA9' : m;
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			int o = VulkanicAPI.getBoundFramebuffer(ctx, n);
			VulkanicAPI.bindFramebuffer(ctx, n, i);
			VulkanicAPI.framebufferTexture(ctx, n, 36064, 3553, j, l);
			VulkanicAPI.framebufferTexture(ctx, n, 36096, 3553, k, l);
			if (m == 0) {
				VulkanicAPI.bindFramebuffer(ctx, n, o);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			int u = VulkanicAPI.getBoundFramebuffer(ctx, 36008);
			int v = VulkanicAPI.getBoundFramebuffer(ctx, 36009);
			VulkanicAPI.bindFramebuffer(ctx, 36008, i);
			VulkanicAPI.bindFramebuffer(ctx, 36009, j);
			VulkanicAPI.blitFramebuffer(ctx, k, l, m, n, o, p, q, r, s, t);
			VulkanicAPI.bindFramebuffer(ctx, 36008, u);
			VulkanicAPI.bindFramebuffer(ctx, 36009, v);
		}
	}
}
