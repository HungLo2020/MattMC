package net.minecraft.server.profile;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Custom player profile implementation replacing Mojang's GameProfile.
 * Represents a player's identity with UUID, username, and optional properties.
 */
public final class PlayerProfile {
	private final UUID id;
	private final String name;
	private final ProfilePropertyMap properties;

	public PlayerProfile(UUID id, String name) {
		this(id, name, new ProfilePropertyMap());
	}

	public PlayerProfile(UUID id, String name, ProfilePropertyMap properties) {
		this.id = Objects.requireNonNull(id, "Profile UUID cannot be null");
		this.name = Objects.requireNonNull(name, "Profile name cannot be null");
		this.properties = Objects.requireNonNull(properties, "Profile properties cannot be null");
	}

	/**
	 * Creates an offline player profile with an offline UUID generated from the username.
	 */
	public static PlayerProfile createOffline(String username) {
		return new PlayerProfile(UUIDUtil.createOfflinePlayerUUID(username), username);
	}

	public UUID id() {
		return this.id;
	}

	public String name() {
		return this.name;
	}

	public ProfilePropertyMap properties() {
		return this.properties;
	}

	/**
	 * Creates a new profile with the same UUID and name but different properties.
	 */
	public PlayerProfile withProperties(ProfilePropertyMap properties) {
		return new PlayerProfile(this.id, this.name, properties);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PlayerProfile other)) {
			return false;
		}
		return this.id.equals(other.id) && this.name.equals(other.name) && this.properties.equals(other.properties);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.name, this.properties);
	}

	@Override
	public String toString() {
		return "PlayerProfile[id=" + this.id + ", name=" + this.name + ", properties=" + this.properties.size() + "]";
	}

	/**
	 * Checks if this profile is complete (has both UUID and name).
	 */
	public boolean isComplete() {
		return this.id != null && this.name != null && !this.name.isEmpty();
	}

	/**
	 * Gets the UUID as a string without dashes (for legacy compatibility).
	 */
	public String getIdAsString() {
		return this.id.toString().replace("-", "");
	}
}
