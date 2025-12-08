package net.minecraft.client.auth;

import java.util.Collections;
import java.util.Set;
import net.minecraft.server.profile.PlayerProfile;

/**
 * Stub for profile fetch results from Mojang services.
 * In offline mode, profiles are created locally.
 */
public record ProfileResult(PlayerProfile profile, Set<ProfileActionType> actions) {
	public static final ProfileResult EMPTY = new ProfileResult(null, Collections.emptySet());

	public enum ProfileActionType {
		FORCED_NAME_CHANGE,
		USING_BANNED_SKIN
	}
}
