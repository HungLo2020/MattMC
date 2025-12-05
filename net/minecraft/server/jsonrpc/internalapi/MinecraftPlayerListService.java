package net.minecraft.server.jsonrpc.internalapi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.Nullable;

public interface MinecraftPlayerListService {
	List<ServerPlayer> getPlayers();

	@Nullable
	ServerPlayer getPlayer(UUID uUID);

	default CompletableFuture<Optional<NameAndId>> getUser(Optional<UUID> optional, Optional<String> optional2) {
		if (optional.isPresent()) {
			Optional<NameAndId> optional3 = this.getCachedUserById((UUID)optional.get());
			return optional3.isPresent()
				? CompletableFuture.completedFuture(optional3)
				: CompletableFuture.supplyAsync(() -> this.fetchUserById((UUID)optional.get()), Util.nonCriticalIoPool());
		} else {
			return optional2.isPresent()
				? CompletableFuture.supplyAsync(() -> this.fetchUserByName((String)optional2.get()), Util.nonCriticalIoPool())
				: CompletableFuture.completedFuture(Optional.empty());
		}
	}

	Optional<NameAndId> fetchUserByName(String string);

	Optional<NameAndId> fetchUserById(UUID uUID);

	Optional<NameAndId> getCachedUserById(UUID uUID);

	Optional<ServerPlayer> getPlayer(Optional<UUID> optional, Optional<String> optional2);

	List<ServerPlayer> getPlayersWithAddress(String string);

	@Nullable
	ServerPlayer getPlayerByName(String string);

	void remove(ServerPlayer serverPlayer, ClientInfo clientInfo);
}
