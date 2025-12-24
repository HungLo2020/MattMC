package net.minecraft.server.network;

import net.minecraft.server.profile.PlayerProfile;
import net.minecraft.server.level.ClientInformation;

public record CommonListenerCookie(PlayerProfile playerProfile, int latency, ClientInformation clientInformation, boolean transferred) {
	public static CommonListenerCookie createInitial(PlayerProfile playerProfile, boolean bl) {
		return new CommonListenerCookie(playerProfile, 0, ClientInformation.createDefault(), bl);
	}
}
