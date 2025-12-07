package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.gui.screens.AddRealmPopupScreen;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class TextureManager implements PreparableReloadListener, Tickable, AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final ResourceLocation INTENTIONAL_MISSING_TEXTURE = ResourceLocation.withDefaultNamespace("");
	private final Map<ResourceLocation, AbstractTexture> byPath = new HashMap();
	private final Set<Tickable> tickableTextures = new HashSet();
	private final ResourceManager resourceManager;

	public TextureManager(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
		NativeImage nativeImage = MissingTextureAtlasSprite.generateMissingImage();
		this.register(MissingTextureAtlasSprite.getLocation(), new DynamicTexture(() -> "(intentionally-)Missing Texture", nativeImage));
	}

	public void registerAndLoad(ResourceLocation resourceLocation, ReloadableTexture reloadableTexture) {
		try {
			reloadableTexture.apply(this.loadContentsSafe(resourceLocation, reloadableTexture));
		} catch (Throwable var6) {
			CrashReport crashReport = CrashReport.forThrowable(var6, "Uploading texture");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Uploaded texture");
			crashReportCategory.setDetail("Resource location", reloadableTexture.resourceId());
			crashReportCategory.setDetail("Texture id", resourceLocation);
			throw new ReportedException(crashReport);
		}

		this.register(resourceLocation, reloadableTexture);
	}

	private TextureContents loadContentsSafe(ResourceLocation resourceLocation, ReloadableTexture reloadableTexture) {
		try {
			return loadContents(this.resourceManager, resourceLocation, reloadableTexture);
		} catch (Exception var4) {
			LOGGER.error("Failed to load texture {} into slot {}", reloadableTexture.resourceId(), resourceLocation, var4);
			return TextureContents.createMissing();
		}
	}

	public void registerForNextReload(ResourceLocation resourceLocation) {
		this.register(resourceLocation, new SimpleTexture(resourceLocation));
	}

	public void register(ResourceLocation resourceLocation, AbstractTexture abstractTexture) {
		AbstractTexture abstractTexture2 = (AbstractTexture)this.byPath.put(resourceLocation, abstractTexture);
		if (abstractTexture2 != abstractTexture) {
			if (abstractTexture2 != null) {
				this.safeClose(resourceLocation, abstractTexture2);
			}

			if (abstractTexture instanceof Tickable tickable) {
				this.tickableTextures.add(tickable);
			}
		}
	}

	private void safeClose(ResourceLocation resourceLocation, AbstractTexture abstractTexture) {
		this.tickableTextures.remove(abstractTexture);

		try {
			abstractTexture.close();
		} catch (Exception var4) {
			LOGGER.warn("Failed to close texture {}", resourceLocation, var4);
		}
	}

	public AbstractTexture getTexture(ResourceLocation resourceLocation) {
		AbstractTexture abstractTexture = (AbstractTexture)this.byPath.get(resourceLocation);
		if (abstractTexture != null) {
			return abstractTexture;
		} else {
			SimpleTexture simpleTexture = new SimpleTexture(resourceLocation);
			this.registerAndLoad(resourceLocation, simpleTexture);
			return simpleTexture;
		}
	}

	@Override
	public void tick() {
		for (Tickable tickable : this.tickableTextures) {
			tickable.tick();
		}
	}

	public void release(ResourceLocation resourceLocation) {
		AbstractTexture abstractTexture = (AbstractTexture)this.byPath.remove(resourceLocation);
		if (abstractTexture != null) {
			this.safeClose(resourceLocation, abstractTexture);
		}
	}

	public void close() {
		this.byPath.forEach(this::safeClose);
		this.byPath.clear();
		this.tickableTextures.clear();
	}

	public CompletableFuture<Void> reload(SharedState sharedState, Executor executor, PreparationBarrier preparationBarrier, Executor executor2) {
		ResourceManager resourceManager = sharedState.resourceManager();
		List<TextureManager.PendingReload> list = new ArrayList();
		this.byPath.forEach((resourceLocation, abstractTexture) -> {
			if (abstractTexture instanceof ReloadableTexture reloadableTexture) {
				list.add(scheduleLoad(resourceManager, resourceLocation, reloadableTexture, executor));
			}
		});
		return CompletableFuture.allOf((CompletableFuture[])list.stream().map(TextureManager.PendingReload::newContents).toArray(CompletableFuture[]::new))
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(void_ -> {
				AddRealmPopupScreen.updateCarouselImages(this.resourceManager);

				for (TextureManager.PendingReload pendingReload : list) {
					pendingReload.texture.apply((TextureContents)pendingReload.newContents.join());
				}
			}, executor2);
	}

	public void dumpAllSheets(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException var3) {
			LOGGER.error("Failed to create directory {}", path, var3);
			return;
		}

		this.byPath.forEach((resourceLocation, abstractTexture) -> {
			if (abstractTexture instanceof Dumpable dumpable) {
				try {
					dumpable.dumpContents(resourceLocation, path);
				} catch (IOException var5) {
					LOGGER.error("Failed to dump texture {}", resourceLocation, var5);
				}
			}
		});
	}

	private static TextureContents loadContents(ResourceManager resourceManager, ResourceLocation resourceLocation, ReloadableTexture reloadableTexture) throws IOException {
		try {
			return reloadableTexture.loadContents(resourceManager);
		} catch (FileNotFoundException var4) {
			if (resourceLocation != INTENTIONAL_MISSING_TEXTURE) {
				LOGGER.warn("Missing resource {} referenced from {}", reloadableTexture.resourceId(), resourceLocation);
			}

			return TextureContents.createMissing();
		}
	}

	private static TextureManager.PendingReload scheduleLoad(
		ResourceManager resourceManager, ResourceLocation resourceLocation, ReloadableTexture reloadableTexture, Executor executor
	) {
		return new TextureManager.PendingReload(reloadableTexture, CompletableFuture.supplyAsync(() -> {
			try {
				return loadContents(resourceManager, resourceLocation, reloadableTexture);
			} catch (IOException var4) {
				throw new UncheckedIOException(var4);
			}
		}, executor));
	}

	@Environment(EnvType.CLIENT)
	record PendingReload(ReloadableTexture texture, CompletableFuture<TextureContents> newContents) {
	}
}
