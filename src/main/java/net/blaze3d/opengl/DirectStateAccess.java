package net.blaze3d.opengl;

import net.blaze3d.GraphicsWorkarounds;
import java.nio.ByteBuffer;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.GraphicsFeature;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicCoreAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DirectStateAccess {
	public static DirectStateAccess create(GraphicsCapabilities gLCapabilities, Set<String> set, GraphicsWorkarounds graphicsWorkarounds) {
		if (gLCapabilities.supports(GraphicsFeature.DIRECT_STATE_ACCESS) && GlDevice.USE_GL_ARB_direct_state_access && !graphicsWorkarounds.isGlOnDx12()) {
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

	void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
		this.bindFrameBufferTextures(i, j, k, l, m, false);
	}

	abstract void bindFrameBufferTextures(int i, int j, int k, int l, int m, boolean bl);

	abstract void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t);

	abstract void flushMappedBufferRange(int i, int j, int k, int l);

	abstract void copyBufferSubData(int i, int j, int k, int l, int m);

	private static CommandContext commandContext() {
		return VulkanicAPI.getCommandContext();
	}

	@Environment(EnvType.CLIENT)
	static class Core extends DirectStateAccess {
		@Override
		int createBuffer() {
			net.irisshaders.iris.gl.IrisRenderSystem.incrementTrackedBuffers();
			CommandContext ctx = commandContext();
			return VulkanicAPI.createBufferDSA(ctx);
		}

		@Override
		void bufferData(int i, long l, int j) {
			CommandContext ctx = commandContext();
			VulkanicAPI.namedBufferDataDSA(ctx, i, l, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			CommandContext ctx = commandContext();
			VulkanicAPI.namedBufferDataDSA(ctx, i, byteBuffer, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			CommandContext ctx = commandContext();
			VulkanicAPI.namedBufferSubDataDSA(ctx, i, (long)j, byteBuffer);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			CommandContext ctx = commandContext();
			VulkanicAPI.namedBufferStorageDSA(ctx, i, l, GlConst.bufferUsageToGlFlag(j));
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			CommandContext ctx = commandContext();
			VulkanicAPI.namedBufferStorageDSA(ctx, i, byteBuffer, GlConst.bufferUsageToGlFlag(j));
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			CommandContext ctx = commandContext();
			return VulkanicAPI.mapNamedBufferRangeDSA(ctx, i, j, k, l);
		}

		@Override
		void unmapBuffer(int i, int j) {
			CommandContext ctx = commandContext();
			VulkanicAPI.unmapNamedBufferDSA(ctx, i);
		}

		@Override
		public int createFrameBufferObject() {
			CommandContext ctx = commandContext();
			return VulkanicAPI.createFramebuffer(ctx);
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m, boolean bl) {
			CommandContext ctx = commandContext();
			VulkanicAPI.namedFramebufferColorAttachment0DSA(ctx, i, j, l);
			if (bl) {
				VulkanicAPI.namedFramebufferTextureDSA(ctx, i, VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT, k, l);
			} else {
				VulkanicAPI.namedFramebufferDepthAttachmentDSA(ctx, i, k, l);
			}
			if (m != 0) {
				VulkanicAPI.bindFramebuffer(ctx, m, i);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			CommandContext ctx = commandContext();
			VulkanicAPI.blitNamedFramebufferDSA(ctx, i, j, k, l, m, n, o, p, q, r, s, t);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			CommandContext ctx = commandContext();
			VulkanicAPI.flushMappedNamedBufferRangeDSA(ctx, i, j, k);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			CommandContext ctx = commandContext();
			VulkanicAPI.copyNamedBufferSubDataDSA(ctx, i, j, k, l, m);
		}
	}

	@Environment(EnvType.CLIENT)
	static class Emulated extends DirectStateAccess {
		private VulkanicBufferTarget selectBufferBindTarget(int i) {
			if ((i & 32) != 0) {
				return VulkanicBufferTarget.VERTEX;
			} else if ((i & 64) != 0) {
				return VulkanicBufferTarget.INDEX;
			} else {
				return (i & 128) != 0 ? VulkanicBufferTarget.UNIFORM : VulkanicBufferTarget.COPY_WRITE;
			}
		}

		@Override
		int createBuffer() {
			net.irisshaders.iris.gl.IrisRenderSystem.incrementTrackedBuffers();
			CommandContext ctx = commandContext();
			return VulkanicAPI.createBuffer(ctx);
		}

		@Override
		void bufferData(int i, long l, int j) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(j);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			VulkanicCoreAPI.bufferData(ctx, bindTarget, l, GlConst.bufferUsageToGlEnum(j));
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(j);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			VulkanicCoreAPI.bufferData(ctx, bindTarget, byteBuffer, GlConst.bufferUsageToGlEnum(j));
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(k);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			VulkanicCoreAPI.bufferSubData(ctx, bindTarget, (long)j, byteBuffer);
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(j);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			VulkanicCoreAPI.bufferStorage(ctx, bindTarget, l, GlConst.bufferUsageToGlFlag(j));
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(j);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			VulkanicCoreAPI.bufferStorage(ctx, bindTarget, byteBuffer, GlConst.bufferUsageToGlFlag(j));
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(m);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			ByteBuffer byteBuffer = VulkanicCoreAPI.mapBufferRange(ctx, bindTarget, j, k, l);
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
			return byteBuffer;
		}

		@Override
		void unmapBuffer(int i, int j) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(j);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			VulkanicCoreAPI.unmapBuffer(ctx, bindTarget);
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			VulkanicBufferTarget bindTarget = this.selectBufferBindTarget(l);
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, i);
			VulkanicCoreAPI.flushMappedBufferRange(ctx, bindTarget, j, k);
			VulkanicCoreAPI.bindBuffer(ctx, bindTarget, 0);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			CommandContext ctx = commandContext();
			VulkanicAPI.bindCopyReadBuffer(ctx, i);
			VulkanicAPI.bindCopyWriteBuffer(ctx, j);
			VulkanicAPI.copyBufferSubDataBetweenCopyTargets(ctx, k, l, m);
			VulkanicAPI.bindCopyReadBuffer(ctx, 0);
			VulkanicAPI.bindCopyWriteBuffer(ctx, 0);
		}

		@Override
		public int createFrameBufferObject() {
			CommandContext ctx = commandContext();
			return VulkanicAPI.createFramebuffer(ctx);
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m, boolean bl) {
			int n = m == 0 ? VulkanicAPI.GL_FRAMEBUFFER : m;
			CommandContext ctx = commandContext();
			int o = VulkanicAPI.getFramebufferBinding(n);
			VulkanicAPI.bindFramebuffer(ctx, n, i);
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, n, j, l);
			VulkanicAPI.framebufferTexture2D(ctx, n, bl ? VulkanicAPI.GL_DEPTH_STENCIL_ATTACHMENT : VulkanicAPI.GL_DEPTH_ATTACHMENT, k, l);
			if (m == 0) {
				VulkanicAPI.bindFramebuffer(ctx, n, o);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			CommandContext ctx = commandContext();
			VulkanicAPI.blitNamedFramebuffer(ctx, i, j, k, l, m, n, o, p, q, r, s, t);
		}
	}
}
