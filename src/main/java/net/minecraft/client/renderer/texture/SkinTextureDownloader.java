package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.logging.LogUtils;
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

}
