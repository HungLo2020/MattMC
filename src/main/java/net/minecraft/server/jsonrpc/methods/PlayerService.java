package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class PlayerService {
	private static final Component DEFAULT_KICK_MESSAGE = Component.translatable("multiplayer.disconnect.kicked");

	public static List<PlayerDto> get(MinecraftApi minecraftApi) {
		return minecraftApi.playerListService().getPlayers().stream().map(PlayerDto::from).toList();
	}

	public static List<PlayerDto> kick(MinecraftApi minecraftApi, List<PlayerService.KickDto> list, ClientInfo clientInfo) {
		List<PlayerDto> list2 = new ArrayList();

		for (PlayerService.KickDto kickDto : list) {
			ServerPlayer serverPlayer = getServerPlayer(minecraftApi, kickDto.player());
			if (serverPlayer != null) {
				minecraftApi.playerListService().remove(serverPlayer, clientInfo);
				serverPlayer.connection.disconnect((Component)kickDto.message.flatMap(Message::asComponent).orElse(DEFAULT_KICK_MESSAGE));
				list2.add(kickDto.player());
			}
		}

		return list2;
	}

	@Nullable
	private static ServerPlayer getServerPlayer(MinecraftApi minecraftApi, PlayerDto playerDto) {
		if (playerDto.id().isPresent()) {
			return minecraftApi.playerListService().getPlayer((UUID)playerDto.id().get());
		} else {
			return playerDto.name().isPresent() ? minecraftApi.playerListService().getPlayerByName((String)playerDto.name().get()) : null;
		}
	}

	public record KickDto(PlayerDto player, Optional<Message> message) {
		public static final MapCodec<PlayerService.KickDto> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PlayerDto.CODEC.codec().fieldOf("player").forGetter(PlayerService.KickDto::player),
					Message.CODEC.optionalFieldOf("message").forGetter(PlayerService.KickDto::message)
				)
				.apply(instance, PlayerService.KickDto::new)
		);
	}
}
