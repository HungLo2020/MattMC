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
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			// The presented image is Rust-owned: Rust copies the completed frame
			// before presenting it and hands back RGBA bytes to encode here.
			if (i != 1) {
				consumer.accept(Component.translatable("screenshot.failure", "scaled capture is not supported on the Rust Vulkan renderer"));
				return;
			}
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
				saver.accept(nativeImage);
			});
			return;
		}
		takeScreenshot(renderTarget, i, saver);
	}

	public static void takeScreenshot(RenderTarget renderTarget, Consumer<NativeImage> consumer) {
		takeScreenshot(renderTarget, 1, consumer);
	}

	public static void takeScreenshot(RenderTarget renderTarget, int i, Consumer<NativeImage> consumer) {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException(
				"Java screenshot readback is unavailable on selected Vulkan"
			);
		}
		int j = renderTarget.width;
		int k = renderTarget.height;
		GpuTexture gpuTexture = renderTarget.getColorTexture();
		if (gpuTexture == null) {
			throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
		} else if (j % i == 0 && k % i == 0) {
			GpuBuffer gpuBuffer = VulkanicAPI.createBuffer(() -> "Screenshot buffer", 9, j * k * gpuTexture.getFormat().pixelSize());
			CommandEncoder commandEncoder = VulkanicAPI.createCommandEncoder();
			VulkanicAPI.createCommandEncoder().copyTextureToBuffer(gpuTexture, gpuBuffer, 0, () -> {
				try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(gpuBuffer, true, false)) {
					int l = k / i;
					int m = j / i;
					NativeImage nativeImage = new NativeImage(m, l, false);

					for (int n = 0; n < l; n++) {
						for (int o = 0; o < m; o++) {
							if (i == 1) {
								int p = mappedView.data().getInt((o + n * j) * gpuTexture.getFormat().pixelSize());
								nativeImage.setPixelABGR(o, k - n - 1, p | 0xFF000000);
							} else {
								int p = 0;
								int q = 0;
								int r = 0;

								for (int s = 0; s < i; s++) {
									for (int t = 0; t < i; t++) {
										int u = mappedView.data().getInt((o * i + s + (n * i + t) * j) * gpuTexture.getFormat().pixelSize());
										p += ARGB.red(u);
										q += ARGB.green(u);
										r += ARGB.blue(u);
									}
								}

								int s = i * i;
								nativeImage.setPixelABGR(o, l - n - 1, ARGB.color(255, p / s, q / s, r / s));
							}
						}
					}

					consumer.accept(nativeImage);
				}

				gpuBuffer.close();
			}, 0);
		} else {
			throw new IllegalArgumentException("Image size is not divisible by downscale factor");
		}
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
