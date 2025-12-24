package net.minecraft.server.profile;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single property of a player profile (e.g., textures, capes).
 * Replaces Mojang's Property class from authlib.
 */
public final class ProfileProperty {
	private final String name;
	private final String value;
	@Nullable
	private final String signature;

	public ProfileProperty(String name, String value) {
		this(name, value, null);
	}

	public ProfileProperty(String name, String value, @Nullable String signature) {
		this.name = Objects.requireNonNull(name, "Property name cannot be null");
		this.value = Objects.requireNonNull(value, "Property value cannot be null");
		this.signature = signature;
	}

	public String name() {
		return this.name;
	}

	public String value() {
		return this.value;
	}

	@Nullable
	public String signature() {
		return this.signature;
	}

	public boolean hasSignature() {
		return this.signature != null;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ProfileProperty other)) {
			return false;
		}
		return this.name.equals(other.name) 
			&& this.value.equals(other.value) 
			&& Objects.equals(this.signature, other.signature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.value, this.signature);
	}

	@Override
	public String toString() {
		return "ProfileProperty[name=" + this.name + ", value=" + this.value.substring(0, Math.min(20, this.value.length())) + "..., signed=" + (this.signature != null) + "]";
	}
}
