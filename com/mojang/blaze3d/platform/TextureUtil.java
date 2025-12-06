package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
@DontObfuscate
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
		int j = 0;

		for (int k = 0; k <= i; k++) {
			j += gpuTexture.getFormat().pixelSize() * gpuTexture.getWidth(k) * gpuTexture.getHeight(k);
		}

		GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(() -> "Texture output buffer", 9, j);
		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
		Runnable runnable = () -> {
			try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(gpuBuffer, true, false)) {
				int jx = 0;

				for (int k = 0; k <= i; k++) {
					int lx = gpuTexture.getWidth(k);
					int mx = gpuTexture.getHeight(k);

					try (NativeImage nativeImage = new NativeImage(lx, mx, false)) {
						for (int n = 0; n < mx; n++) {
							for (int o = 0; o < lx; o++) {
								int p = mappedView.data().getInt(jx + (o + n * lx) * gpuTexture.getFormat().pixelSize());
								nativeImage.setPixelABGR(o, n, intUnaryOperator.applyAsInt(p));
							}
						}

						Path path2 = path.resolve(string + "_" + k + ".png");
						nativeImage.writeToFile(path2);
						LOGGER.debug("Exported png to: {}", path2.toAbsolutePath());
					} catch (IOException var19) {
						LOGGER.debug("Unable to write: ", (Throwable)var19);
					}

					jx += gpuTexture.getFormat().pixelSize() * lx * mx;
				}
			}

			gpuBuffer.close();
		};
		AtomicInteger atomicInteger = new AtomicInteger();
		int l = 0;

		for (int m = 0; m <= i; m++) {
			commandEncoder.copyTextureToBuffer(gpuTexture, gpuBuffer, l, () -> {
				if (atomicInteger.getAndIncrement() == i) {
					runnable.run();
				}
			}, m);
			l += gpuTexture.getFormat().pixelSize() * gpuTexture.getWidth(m) * gpuTexture.getHeight(m);
		}
	}

	public static Path getDebugTexturePath(Path path) {
		return path.resolve("screenshots").resolve("debug");
	}

	public static Path getDebugTexturePath() {
		return getDebugTexturePath(Path.of("."));
	}
}
