package net.minecraft.client.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import net.minecraft.server.profile.PlayerProfile;
import net.minecraft.client.auth.SignatureState;
import net.minecraft.client.auth.MinecraftProfileTextures;
import net.minecraft.server.profile.ProfileProperty;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Services;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SkinManager {
	static final Logger LOGGER = LogUtils.getLogger();
	private final Services services;
	final SkinTextureDownloader skinTextureDownloader;
	private final LoadingCache<SkinManager.CacheKey, CompletableFuture<Optional<PlayerSkin>>> skinCache;
	private final SkinManager.TextureCache skinTextures;
	private final SkinManager.TextureCache capeTextures;
	private final SkinManager.TextureCache elytraTextures;

	public SkinManager(Path path, Services services, SkinTextureDownloader skinTextureDownloader, Executor executor) {
		this.services = services;
		this.skinTextureDownloader = skinTextureDownloader;
		this.skinTextures = new SkinManager.TextureCache(path, MinecraftProfileTextures.TextureType.SKIN);
		this.capeTextures = new SkinManager.TextureCache(path, MinecraftProfileTextures.TextureType.CAPE);
		this.elytraTextures = new SkinManager.TextureCache(path, MinecraftProfileTextures.TextureType.ELYTRA);
		this.skinCache = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofSeconds(15L))
			.build(
				new CacheLoader<SkinManager.CacheKey, CompletableFuture<Optional<PlayerSkin>>>() {
					public CompletableFuture<Optional<PlayerSkin>> load(SkinManager.CacheKey cacheKey) {
						// Offline mode - return default skin immediately
						return CompletableFuture.supplyAsync(() -> {
								// In offline mode, always use empty textures (defaults to Steve/Alex skin)
								MinecraftProfileTextures minecraftProfileTextures = MinecraftProfileTextures.EMPTY;
								return minecraftProfileTextures;
							}, Util.backgroundExecutor().forName("unpackSkinTextures"))
							.thenComposeAsync(minecraftProfileTextures -> SkinManager.this.registerTextures(cacheKey.profileId(), minecraftProfileTextures), executor)
							.handle((playerSkin, throwable) -> {
								if (throwable != null) {
									SkinManager.LOGGER.warn("Failed to load texture for profile {}", cacheKey.profileId, throwable);
								}

								return Optional.ofNullable(playerSkin);
							});
					}
				}
			);
	}

	public Supplier<PlayerSkin> createLookup(PlayerProfile playerProfile, boolean bl) {
		CompletableFuture<Optional<PlayerSkin>> completableFuture = this.get(playerProfile);
		PlayerSkin playerSkin = DefaultPlayerSkin.get(playerProfile);
		if (SharedConstants.DEBUG_DEFAULT_SKIN_OVERRIDE) {
			return () -> playerSkin;
		} else {
			Optional<PlayerSkin> optional = (Optional<PlayerSkin>)completableFuture.getNow(null);
			if (optional != null) {
				PlayerSkin playerSkin2 = (PlayerSkin)optional.filter(playerSkinx -> !bl || playerSkinx.secure()).orElse(playerSkin);
				return () -> playerSkin2;
			} else {
				return () -> (PlayerSkin)((Optional<PlayerSkin>)completableFuture.getNow(Optional.empty())).filter(playerSkinxx -> !bl || playerSkinxx.secure()).orElse(playerSkin);
			}
		}
	}

	public CompletableFuture<Optional<PlayerSkin>> get(PlayerProfile playerProfile) {
		// Offline mode - always return default skin
		PlayerSkin playerSkin = DefaultPlayerSkin.get(playerProfile);
		return CompletableFuture.completedFuture(Optional.of(playerSkin));
	}

	CompletableFuture<PlayerSkin> registerTextures(UUID uUID, MinecraftProfileTextures minecraftProfileTextures) {
		// Offline mode - always use default skin
		PlayerSkin playerSkin = DefaultPlayerSkin.get(uUID);
		return CompletableFuture.completedFuture(playerSkin);
	}

	@Environment(EnvType.CLIENT)
	record CacheKey(UUID profileId, @Nullable ProfileProperty packedTextures) {
	}

	@Environment(EnvType.CLIENT)
	class TextureCache {
		private final Path root;
		private final MinecraftProfileTextures.TextureType type;

		TextureCache(final Path path, final MinecraftProfileTextures.TextureType type) {
			this.root = path;
			this.type = type;
		}

		// Offline mode - texture caching is not used, returns null
		// Future implementation can add custom texture loading here
	}
}
