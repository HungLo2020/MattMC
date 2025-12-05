package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

public class BanlistService {
	private static final String BAN_SOURCE = "Management server";

	public static List<BanlistService.UserBanDto> get(MinecraftApi minecraftApi) {
		return minecraftApi.banListService()
			.getUserBanEntries()
			.stream()
			.filter(userBanListEntry -> userBanListEntry.getUser() != null)
			.map(BanlistService.UserBan::from)
			.map(BanlistService.UserBanDto::from)
			.toList();
	}

	public static List<BanlistService.UserBanDto> add(MinecraftApi minecraftApi, List<BanlistService.UserBanDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<BanlistService.UserBan>>> list2 = list.stream()
			.map(
				userBanDto -> minecraftApi.playerListService()
					.getUser(userBanDto.player().id(), userBanDto.player().name())
					.thenApply(optionalx -> optionalx.map(userBanDto::toUserBan))
			)
			.toList();

		for (Optional<BanlistService.UserBan> optional : (List)Util.sequence(list2).join()) {
			if (!optional.isEmpty()) {
				BanlistService.UserBan userBan = (BanlistService.UserBan)optional.get();
				minecraftApi.banListService().addUserBan(userBan.toBanEntry(), clientInfo);
				ServerPlayer serverPlayer = minecraftApi.playerListService().getPlayer(((BanlistService.UserBan)optional.get()).player().id());
				if (serverPlayer != null) {
					serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
				}
			}
		}

		return get(minecraftApi);
	}

	public static List<BanlistService.UserBanDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
		minecraftApi.banListService().clearUserBans(clientInfo);
		return get(minecraftApi);
	}

	public static List<BanlistService.UserBanDto> remove(MinecraftApi minecraftApi, List<PlayerDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<NameAndId>>> list2 = list.stream()
			.map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
			.toList();

		for (Optional<NameAndId> optional : (List)Util.sequence(list2).join()) {
			if (!optional.isEmpty()) {
				minecraftApi.banListService().removeUserBan((NameAndId)optional.get(), clientInfo);
			}
		}

		return get(minecraftApi);
	}

	public static List<BanlistService.UserBanDto> set(MinecraftApi minecraftApi, List<BanlistService.UserBanDto> list, ClientInfo clientInfo) {
		List<CompletableFuture<Optional<BanlistService.UserBan>>> list2 = list.stream()
			.map(
				userBanDto -> minecraftApi.playerListService()
					.getUser(userBanDto.player().id(), userBanDto.player().name())
					.thenApply(optional -> optional.map(userBanDto::toUserBan))
			)
			.toList();
		Set<BanlistService.UserBan> set = (Set<BanlistService.UserBan>)((List)Util.sequence(list2).join())
			.stream()
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(Collectors.toSet());
		Set<BanlistService.UserBan> set2 = (Set<BanlistService.UserBan>)minecraftApi.banListService()
			.getUserBanEntries()
			.stream()
			.filter(userBanListEntry -> userBanListEntry.getUser() != null)
			.map(BanlistService.UserBan::from)
			.collect(Collectors.toSet());
		set2.stream().filter(userBan -> !set.contains(userBan)).forEach(userBan -> minecraftApi.banListService().removeUserBan(userBan.player(), clientInfo));
		set.stream().filter(userBan -> !set2.contains(userBan)).forEach(userBan -> {
			minecraftApi.banListService().addUserBan(userBan.toBanEntry(), clientInfo);
			ServerPlayer serverPlayer = minecraftApi.playerListService().getPlayer(userBan.player().id());
			if (serverPlayer != null) {
				serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
			}
		});
		return get(minecraftApi);
	}

	record UserBan(NameAndId player, @Nullable String reason, String source, Optional<Instant> expires) {
		static BanlistService.UserBan from(UserBanListEntry userBanListEntry) {
			return new BanlistService.UserBan(
				(NameAndId)Objects.requireNonNull(userBanListEntry.getUser()),
				userBanListEntry.getReason(),
				userBanListEntry.getSource(),
				Optional.ofNullable(userBanListEntry.getExpires()).map(Date::toInstant)
			);
		}

		UserBanListEntry toBanEntry() {
			return new UserBanListEntry(
				new NameAndId(this.player().id(), this.player().name()), null, this.source(), (Date)this.expires().map(Date::from).orElse(null), this.reason()
			);
		}
	}

	public record UserBanDto(PlayerDto player, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {
		public static final MapCodec<BanlistService.UserBanDto> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PlayerDto.CODEC.codec().fieldOf("player").forGetter(BanlistService.UserBanDto::player),
					Codec.STRING.optionalFieldOf("reason").forGetter(BanlistService.UserBanDto::reason),
					Codec.STRING.optionalFieldOf("source").forGetter(BanlistService.UserBanDto::source),
					ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(BanlistService.UserBanDto::expires)
				)
				.apply(instance, BanlistService.UserBanDto::new)
		);

		private static BanlistService.UserBanDto from(BanlistService.UserBan userBan) {
			return new BanlistService.UserBanDto(
				PlayerDto.from(userBan.player()), Optional.ofNullable(userBan.reason()), Optional.of(userBan.source()), userBan.expires()
			);
		}

		public static BanlistService.UserBanDto from(UserBanListEntry userBanListEntry) {
			return from(BanlistService.UserBan.from(userBanListEntry));
		}

		private BanlistService.UserBan toUserBan(NameAndId nameAndId) {
			return new BanlistService.UserBan(nameAndId, (String)this.reason().orElse(null), (String)this.source().orElse("Management server"), this.expires());
		}
	}
}
