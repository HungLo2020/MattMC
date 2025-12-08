package net.minecraft.client.auth;

import java.util.Set;
import java.util.UUID;

/**
 * Stub implementation of UserApiService for offline mode.
 * In offline mode, no user API features are available.
 */
public interface UserApiService {
	UserApiService OFFLINE = new UserApiService() {
		@Override
		public UserProperties fetchProperties() {
			return OFFLINE_PROPERTIES;
		}

		@Override
		public boolean isBlockedPlayer(UUID uuid) {
			return false;
		}

		@Override
		public boolean isAllowedPlayer(UUID uuid) {
			return true;
		}
	};

	UserProperties OFFLINE_PROPERTIES = new UserProperties(
		Set.of(UserFlag.SERVERS_ALLOWED, UserFlag.CHAT_ALLOWED, UserFlag.REALMS_ALLOWED), 
		Set.of()
	);

	UserProperties fetchProperties();

	boolean isBlockedPlayer(UUID uuid);

	boolean isAllowedPlayer(UUID uuid);

	/**
	 * User properties including flags and privileges.
	 */
	record UserProperties(Set<UserFlag> flags, Set<UUID> bannedPlayers) {
		public boolean flag(UserFlag flag) {
			return flags.contains(flag);
		}

		public java.util.Map<String, BanDetails> bannedScopes() {
			return java.util.Collections.emptyMap();
		}
	}

	/**
	 * User flags from Mojang services.
	 */
	enum UserFlag {
		SERVERS_ALLOWED,
		REALMS_ALLOWED,
		CHAT_ALLOWED,
		TELEMETRY_ENABLED,
		PROFANITY_FILTER_ENABLED
	}
}
