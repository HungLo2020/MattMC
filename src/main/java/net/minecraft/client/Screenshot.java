package net.minecraft.client;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTexture;
import net.logging.LogUtils;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.OpenFile;
import net.minecraft.util.ARGB;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Screenshot {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String SCREENSHOT_DIR = "screenshots";

	public static void grab(File file, RenderTarget renderTarget, Consumer<Component> consumer) {
		grab(file, null, renderTarget, 1, consumer);
	}

	public static void grab(File file, @Nullable String string, RenderTarget renderTarget, int i, Consumer<Component> consumer) {
		Consumer<NativeImage> saver = nativeImage -> {
				File file2 = new File(file, "screenshots");
				file2.mkdir();
				File file3;
				if (string == null) {
					file3 = getFile(file2);
				} else {
					file3 = new File(file2, string);
				}

				Util.ioPool()
					.execute(
						() -> {
							try {
								NativeImage exception = nativeImage;

								try {
									nativeImage.writeToFile(file3);
									Component component = Component.literal(file3.getName())
										.withStyle(ChatFormatting.UNDERLINE)
										.withStyle(style -> style.withClickEvent(new OpenFile(file3.getAbsoluteFile())));
									consumer.accept(Component.translatable("screenshot.success", new Object[]{component}));
								} catch (Throwable var7) {
									if (nativeImage != null) {
										try {
											exception.close();
										} catch (Throwable var6) {
											var7.addSuppressed(var6);
										}
									}

									throw var7;
								}

								if (nativeImage != null) {
									nativeImage.close();
								}
							} catch (Exception var8) {
								LOGGER.warn("Couldn't save screenshot", (Throwable)var8);
								consumer.accept(Component.translatable("screenshot.failure", new Object[]{var8.getMessage()}));
							}
						}
					);
		};
		// The presented image is Rust-owned: Rust copies the completed frame
		// before presenting it and hands back RGBA bytes to encode here.
		if (i != 1) {
			consumer.accept(Component.translatable("screenshot.failure", "scaled capture is not supported on the Rust Vulkan renderer"));
			return;
		}
		captureNextFrame(saver);
	}

	/**
	 * Hands the next presented frame to {@code consumer} as an RGBA image. Rust
	 * copies the completed frame target before presenting it.
	 */
	public static void captureNextFrame(Consumer<NativeImage> consumer) {
		net.vulkanic.gui.RustGalFrameCoordinator.requestScreenshot(frame -> {
			NativeImage nativeImage = new NativeImage(frame.width(), frame.height(), false);
			byte[] rgba = frame.rgba();
			for (int y = 0; y < frame.height(); y++) {
				for (int x = 0; x < frame.width(); x++) {
					int index = (y * frame.width() + x) * 4;
					nativeImage.setPixelABGR(x, y, 0xFF000000
						| (rgba[index + 2] & 0xFF) << 16
						| (rgba[index + 1] & 0xFF) << 8
						| (rgba[index] & 0xFF));
				}
			}
			consumer.accept(nativeImage);
		});
	}

	private static File getFile(File file) {
		String string = Util.getFilenameFormattedDateTime();
		int i = 1;

		while (true) {
			File file2 = new File(file, string + (i == 1 ? "" : "_" + i) + ".png");
			if (!file2.exists()) {
				return file2;
			}

			i++;
		}
	}
}
