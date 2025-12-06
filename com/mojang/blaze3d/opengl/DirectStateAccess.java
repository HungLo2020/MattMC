package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.GraphicsWorkarounds;
import java.nio.ByteBuffer;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GLCapabilities;

@Environment(EnvType.CLIENT)
public abstract class DirectStateAccess {
	public static DirectStateAccess create(GLCapabilities gLCapabilities, Set<String> set, GraphicsWorkarounds graphicsWorkarounds) {
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
			return ARBDirectStateAccess.glCreateBuffers();
		}

		@Override
		void bufferData(int i, long l, int j) {
			ARBDirectStateAccess.glNamedBufferData(i, l, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			ARBDirectStateAccess.glNamedBufferData(i, byteBuffer, GlConst.bufferUsageToGlEnum(j));
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			ARBDirectStateAccess.glNamedBufferSubData(i, (long)j, byteBuffer);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			ARBDirectStateAccess.glNamedBufferStorage(i, l, GlConst.bufferUsageToGlFlag(j));
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			ARBDirectStateAccess.glNamedBufferStorage(i, byteBuffer, GlConst.bufferUsageToGlFlag(j));
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			return ARBDirectStateAccess.glMapNamedBufferRange(i, j, k, l);
		}

		@Override
		void unmapBuffer(int i, int j) {
			ARBDirectStateAccess.glUnmapNamedBuffer(i);
		}

		@Override
		public int createFrameBufferObject() {
			return ARBDirectStateAccess.glCreateFramebuffers();
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			ARBDirectStateAccess.glNamedFramebufferTexture(i, 36064, j, l);
			ARBDirectStateAccess.glNamedFramebufferTexture(i, 36096, k, l);
			if (m != 0) {
				GlStateManager._glBindFramebuffer(m, i);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			ARBDirectStateAccess.glBlitNamedFramebuffer(i, j, k, l, m, n, o, p, q, r, s, t);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			ARBDirectStateAccess.glFlushMappedNamedBufferRange(i, j, k);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			ARBDirectStateAccess.glCopyNamedBufferSubData(i, j, k, l, m);
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
			return GlStateManager._glGenBuffers();
		}

		@Override
		void bufferData(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			GlStateManager._glBindBuffer(k, i);
			GlStateManager._glBufferData(k, l, GlConst.bufferUsageToGlEnum(j));
			GlStateManager._glBindBuffer(k, 0);
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			GlStateManager._glBindBuffer(k, i);
			GlStateManager._glBufferData(k, byteBuffer, GlConst.bufferUsageToGlEnum(j));
			GlStateManager._glBindBuffer(k, 0);
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer, int k) {
			int l = this.selectBufferBindTarget(k);
			GlStateManager._glBindBuffer(l, i);
			GlStateManager._glBufferSubData(l, j, byteBuffer);
			GlStateManager._glBindBuffer(l, 0);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			int k = this.selectBufferBindTarget(j);
			GlStateManager._glBindBuffer(k, i);
			ARBBufferStorage.glBufferStorage(k, l, GlConst.bufferUsageToGlFlag(j));
			GlStateManager._glBindBuffer(k, 0);
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			int k = this.selectBufferBindTarget(j);
			GlStateManager._glBindBuffer(k, i);
			ARBBufferStorage.glBufferStorage(k, byteBuffer, GlConst.bufferUsageToGlFlag(j));
			GlStateManager._glBindBuffer(k, 0);
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l, int m) {
			int n = this.selectBufferBindTarget(m);
			GlStateManager._glBindBuffer(n, i);
			ByteBuffer byteBuffer = GlStateManager._glMapBufferRange(n, j, k, l);
			GlStateManager._glBindBuffer(n, 0);
			return byteBuffer;
		}

		@Override
		void unmapBuffer(int i, int j) {
			int k = this.selectBufferBindTarget(j);
			GlStateManager._glBindBuffer(k, i);
			GlStateManager._glUnmapBuffer(k);
			GlStateManager._glBindBuffer(k, 0);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k, int l) {
			int m = this.selectBufferBindTarget(l);
			GlStateManager._glBindBuffer(m, i);
			GL30.glFlushMappedBufferRange(m, j, k);
			GlStateManager._glBindBuffer(m, 0);
		}

		@Override
		void copyBufferSubData(int i, int j, int k, int l, int m) {
			GlStateManager._glBindBuffer(36662, i);
			GlStateManager._glBindBuffer(36663, j);
			GL31.glCopyBufferSubData(36662, 36663, k, l, m);
			GlStateManager._glBindBuffer(36662, 0);
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Override
		public int createFrameBufferObject() {
			return GlStateManager.glGenFramebuffers();
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			int n = m == 0 ? '販' : m;
			int o = GlStateManager.getFrameBuffer(n);
			GlStateManager._glBindFramebuffer(n, i);
			GlStateManager._glFramebufferTexture2D(n, 36064, 3553, j, l);
			GlStateManager._glFramebufferTexture2D(n, 36096, 3553, k, l);
			if (m == 0) {
				GlStateManager._glBindFramebuffer(n, o);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			int u = GlStateManager.getFrameBuffer(36008);
			int v = GlStateManager.getFrameBuffer(36009);
			GlStateManager._glBindFramebuffer(36008, i);
			GlStateManager._glBindFramebuffer(36009, j);
			GlStateManager._glBlitFrameBuffer(k, l, m, n, o, p, q, r, s, t);
			GlStateManager._glBindFramebuffer(36008, u);
			GlStateManager._glBindFramebuffer(36009, v);
		}
	}
}
