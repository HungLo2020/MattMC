package net.minecraft.client.auth;

import org.jetbrains.annotations.Nullable;

/**
 * Stub for Minecraft profile textures (skins and capes).
 * In offline mode, textures are not fetched from Mojang.
 * Custom skin support can be added by implementing texture resolution locally.
 */
public record MinecraftProfileTextures(
	@Nullable String skin,
	@Nullable String cape,
	@Nullable String elytra,
	SignatureState signatureState
) {
	public static final MinecraftProfileTextures EMPTY = new MinecraftProfileTextures(null, null, null, SignatureState.UNVERIFIED);

	/**
	 * Get texture URL by type.
	 * Returns null in offline mode - custom implementation can override.
	 */
	@Nullable
	public String getTexture(TextureType type) {
		return switch (type) {
			case SKIN -> skin;
			case CAPE -> cape;
			case ELYTRA -> elytra;
		};
	}

	public enum TextureType {
		SKIN,
		CAPE,
		ELYTRA
	}
}
