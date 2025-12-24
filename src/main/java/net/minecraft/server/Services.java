package net.minecraft.server;

import java.io.File;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.server.players.OfflineUserNameToIdResolver;
import net.minecraft.util.SignatureValidator;
import org.jetbrains.annotations.Nullable;

/**
 * Simplified services record without Mojang authentication dependencies.
 * Operates in offline mode only.
 */
public record Services(
	UserNameToIdResolver nameToIdCache,
	ProfileResolver profileResolver
) {
	private static final String USERID_CACHE_FILE = "usercache.json";

	/**
	 * Creates offline-only services with local profile resolution.
	 */
	public static Services createOffline(File file) {
		UserNameToIdResolver userNameToIdResolver = new OfflineUserNameToIdResolver(new File(file, "usercache.json"));
		ProfileResolver profileResolver = new ProfileResolver.Offline(userNameToIdResolver);
		return new Services(userNameToIdResolver, profileResolver);
	}

	@Nullable
	public SignatureValidator profileKeySignatureValidator() {
		// No key validation in offline mode
		return null;
	}

	public boolean canValidateProfileKeys() {
		// No profile key validation in offline mode
		return false;
	}
}
