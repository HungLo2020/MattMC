package net.minecraft.server.players;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side cache for player skins received from clients.
 * Stores skin data for connected players and broadcasts to other clients.
 */
public class PlayerSkinCache {
	private static final Logger LOGGER = LogUtils.getLogger();
	
	private final Map<UUID, CachedSkin> skinCache = new ConcurrentHashMap<>();
	
	/**
	 * Store a player's skin in the cache.
	 */
	public void cacheSkin(UUID playerId, String skinName, byte[] skinData, boolean isSlimModel) {
		CachedSkin skin = new CachedSkin(skinName, skinData, isSlimModel);
		skinCache.put(playerId, skin);
		LOGGER.debug("Cached skin for player {}: {}", playerId, skinName);
	}
	
	/**
	 * Get a cached skin for a player.
	 */
	public CachedSkin getSkin(UUID playerId) {
		return skinCache.get(playerId);
	}
	
	/**
	 * Remove a player's skin from the cache (typically when they disconnect).
	 */
	public void removeSkin(UUID playerId) {
		CachedSkin removed = skinCache.remove(playerId);
		if (removed != null) {
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
	 * Clear all cached skins (typically on server shutdown).
	 */
	public void clear() {
		skinCache.clear();
		LOGGER.info("Cleared all cached player skins");
	}
	
	/**
	 * Represents a cached player skin.
	 */
	public record CachedSkin(String skinName, byte[] skinData, boolean isSlimModel) {
	}
}
