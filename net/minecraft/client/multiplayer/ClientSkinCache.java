package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for player skins received from the server.
 */
@Environment(EnvType.CLIENT)
public class ClientSkinCache {
	private static final Logger LOGGER = LogUtils.getLogger();
	
	private final Map<UUID, PlayerSkin> skinCache = new ConcurrentHashMap<>();
	private final Minecraft minecraft;
	
	public ClientSkinCache(Minecraft minecraft) {
		this.minecraft = minecraft;
	}
	
	/**
	 * Cache a player's skin received from the server.
	 */
	public void cacheSkin(UUID playerId, String skinName, byte[] skinData, boolean isSlimModel) {
		try {
			// Load the skin image from byte array using NativeImage
			ByteArrayInputStream inputStream = new ByteArrayInputStream(skinData);
			com.mojang.blaze3d.platform.NativeImage nativeImage = com.mojang.blaze3d.platform.NativeImage.read(inputStream);
			
			if (nativeImage == null) {
				LOGGER.warn("Failed to read skin image for player {}", playerId);
				return;
			}
			
			// Create a dynamic texture and register it
			ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath("player_skins", playerId.toString());
			String textureLabel = "player_skin_" + playerId;
			DynamicTexture dynamicTexture = new DynamicTexture(() -> textureLabel, nativeImage);
			
			// Register the texture with the texture manager
			this.minecraft.execute(() -> {
				this.minecraft.getTextureManager().register(textureLocation, dynamicTexture);
			});
			
			// Create and cache the player skin
			PlayerModelType modelType = isSlimModel ? PlayerModelType.SLIM : PlayerModelType.WIDE;
			PlayerSkin skin = new PlayerSkin(new ResourceTexture(textureLocation), null, null, modelType, true);
			skinCache.put(playerId, skin);
			
			LOGGER.debug("Cached skin for player {}: {}", playerId, skinName);
		} catch (IOException e) {
			LOGGER.error("Failed to load skin for player {}", playerId, e);
		}
	}
	
	/**
	 * Get a cached skin for a player.
	 */
	public PlayerSkin getSkin(UUID playerId) {
		return skinCache.get(playerId);
	}
	
	/**
	 * Remove a player's skin from the cache.
	 */
	public void removeSkin(UUID playerId) {
		PlayerSkin removed = skinCache.remove(playerId);
		if (removed != null) {
			// Clean up the texture
			ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath("player_skins", playerId.toString());
			this.minecraft.execute(() -> {
				this.minecraft.getTextureManager().release(textureLocation);
			});
			LOGGER.debug("Removed cached skin for player {}", playerId);
		}
	}
	
	/**
	 * Check if a skin is cached for a player.
	 */
	public boolean hasSkin(UUID playerId) {
		return skinCache.containsKey(playerId);
	}
	
	/**
	 * Clear all cached skins.
	 */
	public void clear() {
		for (UUID playerId : skinCache.keySet()) {
			removeSkin(playerId);
		}
		LOGGER.info("Cleared all cached player skins");
	}
}
