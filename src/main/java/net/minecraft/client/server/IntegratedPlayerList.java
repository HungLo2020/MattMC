package net.minecraft.client.server;

import com.mojang.logging.LogUtils;
import java.net.SocketAddress;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter.ScopedCollector;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class IntegratedPlayerList extends PlayerList {
	private static final Logger LOGGER = LogUtils.getLogger();
	@Nullable
	private CompoundTag playerData;

	public IntegratedPlayerList(IntegratedServer integratedServer, LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess, PlayerDataStorage playerDataStorage) {
		super(integratedServer, layeredRegistryAccess, playerDataStorage, integratedServer.notificationManager());
		this.setViewDistance(10);
	}

	protected void save(ServerPlayer serverPlayer) {
		if (this.getServer().isSingleplayerOwner(serverPlayer.nameAndId())) {
			ScopedCollector scopedCollector = new ScopedCollector(serverPlayer.problemPath(), LOGGER);

			try {
				TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, serverPlayer.registryAccess());
				serverPlayer.saveWithoutId(tagValueOutput);
				this.playerData = tagValueOutput.buildResult();
			} catch (Throwable var6) {
				try {
					scopedCollector.close();
				} catch (Throwable var5) {
					var6.addSuppressed(var5);
				}

				throw var6;
			}

			scopedCollector.close();
		}

		super.save(serverPlayer);
	}

	public Component canPlayerLogin(SocketAddress socketAddress, NameAndId nameAndId) {
		return (Component)(this.getServer().isSingleplayerOwner(nameAndId) && this.getPlayerByName(nameAndId.name()) != null
			? Component.translatable("multiplayer.disconnect.name_taken")
			: super.canPlayerLogin(socketAddress, nameAndId));
	}

	public IntegratedServer getServer() {
		return (IntegratedServer)super.getServer();
	}

	@Nullable
	public CompoundTag getSingleplayerData() {
		return this.playerData;
	}
}
