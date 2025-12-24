package net.minecraft.client.player;

import net.minecraft.server.profile.PlayerProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.server.players.ProfileResolver;

@Environment(EnvType.CLIENT)
public class LocalPlayerResolver implements ProfileResolver {
	private final Minecraft minecraft;
	private final ProfileResolver parentResolver;

	public LocalPlayerResolver(Minecraft minecraft, ProfileResolver profileResolver) {
		this.minecraft = minecraft;
		this.parentResolver = profileResolver;
	}

	public Optional<PlayerProfile> fetchByName(String string) {
		ClientPacketListener clientPacketListener = this.minecraft.getConnection();
		if (clientPacketListener != null) {
			PlayerInfo playerInfo = clientPacketListener.getPlayerInfoIgnoreCase(string);
			if (playerInfo != null) {
				return Optional.of(playerInfo.getProfile());
			}
		}

		return this.parentResolver.fetchByName(string);
	}

	public Optional<PlayerProfile> fetchById(UUID uUID) {
		ClientPacketListener clientPacketListener = this.minecraft.getConnection();
		if (clientPacketListener != null) {
			PlayerInfo playerInfo = clientPacketListener.getPlayerInfo(uUID);
			if (playerInfo != null) {
				return Optional.of(playerInfo.getProfile());
			}
		}

		return this.parentResolver.fetchById(uUID);
	}
}
