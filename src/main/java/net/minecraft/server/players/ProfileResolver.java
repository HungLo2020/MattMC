package net.minecraft.server.players;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.datafixers.util.Either;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.profile.PlayerProfile;
import net.minecraft.util.StringUtil;

/**
 * Interface for resolving player profiles.
 */
public interface ProfileResolver {
	Optional<PlayerProfile> fetchByName(String string);

	Optional<PlayerProfile> fetchById(UUID uUID);

	default Optional<PlayerProfile> fetchByNameOrId(Either<String, UUID> either) {
		return either.map(this::fetchByName, this::fetchById);
	}

	/**
	 * Offline implementation of ProfileResolver that creates profiles locally.
	 */
	public static class Offline implements ProfileResolver {
		private final LoadingCache<String, Optional<PlayerProfile>> profileCacheByName;
		private final LoadingCache<UUID, Optional<PlayerProfile>> profileCacheById;

		public Offline(UserNameToIdResolver userNameToIdResolver) {
			this.profileCacheById = CacheBuilder.newBuilder()
				.expireAfterAccess(Duration.ofMinutes(10L))
				.maximumSize(256L)
				.build(new CacheLoader<UUID, Optional<PlayerProfile>>() {
					public Optional<PlayerProfile> load(UUID uUID) {
						// Look up from cache first
						return userNameToIdResolver.get(uUID).map(nameAndId -> 
							new PlayerProfile(nameAndId.id(), nameAndId.name())
						);
					}
				});
			this.profileCacheByName = CacheBuilder.newBuilder()
				.expireAfterAccess(Duration.ofMinutes(10L))
				.maximumSize(256L)
				.build(new CacheLoader<String, Optional<PlayerProfile>>() {
					public Optional<PlayerProfile> load(String string) {
						return userNameToIdResolver.get(string)
							.flatMap(nameAndId -> Offline.this.profileCacheById.getUnchecked(nameAndId.id()));
					}
				});
		}

		@Override
		public Optional<PlayerProfile> fetchByName(String string) {
			return StringUtil.isValidPlayerName(string) ? this.profileCacheByName.getUnchecked(string) : Optional.empty();
		}

		@Override
		public Optional<PlayerProfile> fetchById(UUID uUID) {
			return this.profileCacheById.getUnchecked(uUID);
		}
	}
}
