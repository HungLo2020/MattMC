package net.blaze3d.platform;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTexture;
import net.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class TextureUtil {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int MIN_MIPMAP_LEVEL = 0;
	private static final int DEFAULT_IMAGE_BUFFER_SIZE = 8192;

	public static ByteBuffer readResource(InputStream inputStream) throws IOException {
		ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
		return readableByteChannel instanceof SeekableByteChannel seekableByteChannel
			? readResource(readableByteChannel, (int)seekableByteChannel.size() + 1)
			: readResource(readableByteChannel, 8192);
	}

	private static ByteBuffer readResource(ReadableByteChannel readableByteChannel, int i) throws IOException {
		ByteBuffer byteBuffer = MemoryUtil.memAlloc(i);

		try {
			while (readableByteChannel.read(byteBuffer) != -1) {
				if (!byteBuffer.hasRemaining()) {
					byteBuffer = MemoryUtil.memRealloc(byteBuffer, byteBuffer.capacity() * 2);
				}
			}

			return byteBuffer;
		} catch (IOException var4) {
			MemoryUtil.memFree(byteBuffer);
			throw var4;
		}
	}

	public static void writeAsPNG(Path path, String string, GpuTexture gpuTexture, int i, IntUnaryOperator intUnaryOperator) {
		RenderSystem.assertOnRenderThread();
		throw new IllegalStateException("Java texture readback is unavailable on the Rust Vulkan route");
	}

	public static Path getDebugTexturePath(Path path) {
		return path.resolve("screenshots").resolve("debug");
	}

	public static Path getDebugTexturePath() {
		return getDebugTexturePath(Path.of("."));
	}
}
