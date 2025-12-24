package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.core.ClientAsset.DownloadedTexture;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SkinTextureDownloader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int SKIN_WIDTH = 64;
	private static final int SKIN_HEIGHT = 64;
	private static final int LEGACY_SKIN_HEIGHT = 32;
	private final Proxy proxy;
	private final TextureManager textureManager;
	private final Executor mainThreadExecutor;

	public SkinTextureDownloader(Proxy proxy, TextureManager textureManager, Executor executor) {
		this.proxy = proxy;
		this.textureManager = textureManager;
		this.mainThreadExecutor = executor;
	}

	public CompletableFuture<Texture> downloadAndRegisterSkin(ResourceLocation resourceLocation, Path path, String string, boolean bl) {
		DownloadedTexture downloadedTexture = new DownloadedTexture(resourceLocation, string);
		return CompletableFuture.supplyAsync(() -> {
			NativeImage nativeImage;
			try {
				nativeImage = this.downloadSkin(path, downloadedTexture.url());
			} catch (IOException var6) {
				throw new UncheckedIOException(var6);
			}

			return bl ? processLegacySkin(nativeImage, downloadedTexture.url()) : nativeImage;
		}, Util.nonCriticalIoPool().forName("downloadTexture")).thenCompose(nativeImage -> this.registerTextureInManager(downloadedTexture, nativeImage));
	}

	private NativeImage downloadSkin(Path path, String string) throws IOException {
		if (Files.isRegularFile(path, new LinkOption[0])) {
			LOGGER.debug("Loading HTTP texture from local cache ({})", path);
			InputStream inputStream = Files.newInputStream(path);

			NativeImage var18;
			try {
				var18 = NativeImage.read(inputStream);
			} catch (Throwable var15) {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (Throwable var13) {
						var15.addSuppressed(var13);
					}
				}

				throw var15;
			}

			if (inputStream != null) {
				inputStream.close();
			}

			return var18;
		} else {
			HttpURLConnection httpURLConnection = null;
			LOGGER.debug("Downloading HTTP texture from {} to {}", string, path);
			URI uRI = URI.create(string);

			NativeImage iOException;
			try {
				httpURLConnection = (HttpURLConnection)uRI.toURL().openConnection(this.proxy);
				httpURLConnection.setDoInput(true);
				httpURLConnection.setDoOutput(false);
				httpURLConnection.connect();
				int i = httpURLConnection.getResponseCode();
				if (i / 100 != 2) {
					throw new IOException("Failed to open " + uRI + ", HTTP error code: " + i);
				}

				byte[] bs = httpURLConnection.getInputStream().readAllBytes();

				try {
					FileUtil.createDirectoriesSafe(path.getParent());
					Files.write(path, bs, new OpenOption[0]);
				} catch (IOException var14) {
					LOGGER.warn("Failed to cache texture {} in {}", string, path);
				}

				iOException = NativeImage.read(bs);
			} finally {
				if (httpURLConnection != null) {
					httpURLConnection.disconnect();
				}
			}

			return iOException;
		}
	}

	private CompletableFuture<Texture> registerTextureInManager(Texture texture, NativeImage nativeImage) {
		return CompletableFuture.supplyAsync(() -> {
			DynamicTexture dynamicTexture = new DynamicTexture(texture.texturePath()::toString, nativeImage);
			this.textureManager.register(texture.texturePath(), dynamicTexture);
			return texture;
		}, this.mainThreadExecutor);
	}

	private static NativeImage processLegacySkin(NativeImage nativeImage, String string) {
		int i = nativeImage.getHeight();
		int j = nativeImage.getWidth();
		if (j == 64 && (i == 32 || i == 64)) {
			boolean bl = i == 32;
			if (bl) {
				NativeImage nativeImage2 = new NativeImage(64, 64, true);
				nativeImage2.copyFrom(nativeImage);
				nativeImage.close();
				nativeImage = nativeImage2;
				nativeImage2.fillRect(0, 32, 64, 32, 0);
				nativeImage2.copyRect(4, 16, 16, 32, 4, 4, true, false);
				nativeImage2.copyRect(8, 16, 16, 32, 4, 4, true, false);
				nativeImage2.copyRect(0, 20, 24, 32, 4, 12, true, false);
				nativeImage2.copyRect(4, 20, 16, 32, 4, 12, true, false);
				nativeImage2.copyRect(8, 20, 8, 32, 4, 12, true, false);
				nativeImage2.copyRect(12, 20, 16, 32, 4, 12, true, false);
				nativeImage2.copyRect(44, 16, -8, 32, 4, 4, true, false);
				nativeImage2.copyRect(48, 16, -8, 32, 4, 4, true, false);
				nativeImage2.copyRect(40, 20, 0, 32, 4, 12, true, false);
				nativeImage2.copyRect(44, 20, -8, 32, 4, 12, true, false);
				nativeImage2.copyRect(48, 20, -16, 32, 4, 12, true, false);
				nativeImage2.copyRect(52, 20, -8, 32, 4, 12, true, false);
			}

			setNoAlpha(nativeImage, 0, 0, 32, 16);
			if (bl) {
				doNotchTransparencyHack(nativeImage, 32, 0, 64, 32);
			}

			setNoAlpha(nativeImage, 0, 16, 64, 32);
			setNoAlpha(nativeImage, 16, 48, 48, 64);
			return nativeImage;
		} else {
			nativeImage.close();
			throw new IllegalStateException("Discarding incorrectly sized (" + j + "x" + i + ") skin texture from " + string);
		}
	}

	private static void doNotchTransparencyHack(NativeImage nativeImage, int i, int j, int k, int l) {
		for (int m = i; m < k; m++) {
			for (int n = j; n < l; n++) {
				int o = nativeImage.getPixel(m, n);
				if (ARGB.alpha(o) < 128) {
					return;
				}
			}
		}

		for (int m = i; m < k; m++) {
			for (int nx = j; nx < l; nx++) {
				nativeImage.setPixel(m, nx, nativeImage.getPixel(m, nx) & 16777215);
			}
		}
	}

	private static void setNoAlpha(NativeImage nativeImage, int i, int j, int k, int l) {
		for (int m = i; m < k; m++) {
			for (int n = j; n < l; n++) {
				nativeImage.setPixel(m, n, ARGB.opaque(nativeImage.getPixel(m, n)));
			}
		}
	}
}
