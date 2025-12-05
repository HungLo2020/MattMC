package com.mojang.blaze3d.opengl;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public abstract class BufferStorage {
	public static BufferStorage create(GLCapabilities gLCapabilities, Set<String> set) {
		if (gLCapabilities.GL_ARB_buffer_storage && GlDevice.USE_GL_ARB_buffer_storage) {
			set.add("GL_ARB_buffer_storage");
			return new BufferStorage.Immutable();
		} else {
			return new BufferStorage.Mutable();
		}
	}

	public abstract GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, int i, int j);

	public abstract GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer);

	public abstract GlBuffer.GlMappedView mapBuffer(DirectStateAccess directStateAccess, GlBuffer glBuffer, int i, int j, int k);

	@Environment(EnvType.CLIENT)
	static class Immutable extends BufferStorage {
		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, int i, int j) {
			int k = directStateAccess.createBuffer();
			directStateAccess.bufferStorage(k, j, i);
			ByteBuffer byteBuffer = this.tryMapBufferPersistent(directStateAccess, i, k, j);
			return new GlBuffer(supplier, directStateAccess, i, j, k, byteBuffer);
		}

		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer) {
			int j = directStateAccess.createBuffer();
			int k = byteBuffer.remaining();
			directStateAccess.bufferStorage(j, byteBuffer, i);
			ByteBuffer byteBuffer2 = this.tryMapBufferPersistent(directStateAccess, i, j, k);
			return new GlBuffer(supplier, directStateAccess, i, k, j, byteBuffer2);
		}

		@Nullable
		private ByteBuffer tryMapBufferPersistent(DirectStateAccess directStateAccess, int i, int j, int k) {
			int l = 0;
			if ((i & 1) != 0) {
				l |= 1;
			}

			if ((i & 2) != 0) {
				l |= 18;
			}

			ByteBuffer byteBuffer;
			if (l != 0) {
				GlStateManager.clearGlErrors();
				byteBuffer = directStateAccess.mapBufferRange(j, 0, k, l | 64, i);
				if (byteBuffer == null) {
					throw new IllegalStateException("Can't persistently map buffer, opengl error " + GlStateManager._getError());
				}
			} else {
				byteBuffer = null;
			}

			return byteBuffer;
		}

		@Override
		public GlBuffer.GlMappedView mapBuffer(DirectStateAccess directStateAccess, GlBuffer glBuffer, int i, int j, int k) {
			if (glBuffer.persistentBuffer == null) {
				throw new IllegalStateException("Somehow trying to map an unmappable buffer");
			} else {
				return new GlBuffer.GlMappedView(() -> {
					if ((k & 2) != 0) {
						directStateAccess.flushMappedBufferRange(glBuffer.handle, i, j, glBuffer.usage());
					}
				}, glBuffer, MemoryUtil.memSlice(glBuffer.persistentBuffer, i, j));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class Mutable extends BufferStorage {
		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, int i, int j) {
			int k = directStateAccess.createBuffer();
			directStateAccess.bufferData(k, j, i);
			return new GlBuffer(supplier, directStateAccess, i, j, k, null);
		}

		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer) {
			int j = directStateAccess.createBuffer();
			int k = byteBuffer.remaining();
			directStateAccess.bufferData(j, byteBuffer, i);
			return new GlBuffer(supplier, directStateAccess, i, k, j, null);
		}

		@Override
		public GlBuffer.GlMappedView mapBuffer(DirectStateAccess directStateAccess, GlBuffer glBuffer, int i, int j, int k) {
			GlStateManager.clearGlErrors();
			ByteBuffer byteBuffer = directStateAccess.mapBufferRange(glBuffer.handle, i, j, k, glBuffer.usage());
			if (byteBuffer == null) {
				throw new IllegalStateException("Can't map buffer, opengl error " + GlStateManager._getError());
			} else {
				return new GlBuffer.GlMappedView(() -> directStateAccess.unmapBuffer(glBuffer.handle, glBuffer.usage()), glBuffer, byteBuffer);
			}
		}
	}
}
