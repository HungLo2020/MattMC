package net.minecraft.server.network;

import net.minecraft.server.profile.PlayerProfile;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.config.JoinWorldTask;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.network.config.ServerCodeOfConductConfigurationTask;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import net.minecraft.server.network.config.SynchronizeRegistriesTask;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.flag.FeatureFlags;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ServerConfigurationPacketListenerImpl
	extends ServerCommonPacketListenerImpl
	implements ServerConfigurationPacketListener,
	TickablePacketListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component DISCONNECT_REASON_INVALID_DATA = Component.translatable("multiplayer.disconnect.invalid_player_data");
	private static final Component DISCONNECT_REASON_CONFIGURATION_ERROR = Component.translatable("multiplayer.disconnect.configuration_error");
	private final PlayerProfile playerProfile;
	private final Queue<ConfigurationTask> configurationTasks = new ConcurrentLinkedQueue();
	@Nullable
	private ConfigurationTask currentTask;
	private ClientInformation clientInformation;
	@Nullable
	private SynchronizeRegistriesTask synchronizeRegistriesTask;
	@Nullable
	private PrepareSpawnTask prepareSpawnTask;

	public ServerConfigurationPacketListenerImpl(MinecraftServer minecraftServer, Connection connection, CommonListenerCookie commonListenerCookie) {
		super(minecraftServer, connection, commonListenerCookie);
		this.gameProfile = commonListenerCookie.gameProfile();
		this.clientInformation = commonListenerCookie.clientInformation();
	}

	@Override
	protected PlayerProfile playerProfile() {
		return this.gameProfile;
	}

	@Override
	public void onDisconnect(DisconnectionDetails disconnectionDetails) {
		LOGGER.info("{} ({}) lost connection: {}", this.gameProfile.getName(), this.gameProfile.getId(), disconnectionDetails.reason().getString());
		if (this.prepareSpawnTask != null) {
			this.prepareSpawnTask.close();
			this.prepareSpawnTask = null;
		}

		super.onDisconnect(disconnectionDetails);
	}

	@Override
	public boolean isAcceptingMessages() {
		return this.connection.isConnected();
	}

	public void startConfiguration() {
		this.send(new ClientboundCustomPayloadPacket(new BrandPayload(this.server.getServerModName())));
		ServerLinks serverLinks = this.server.serverLinks();
		if (!serverLinks.isEmpty()) {
			this.send(new ClientboundServerLinksPacket(serverLinks.untrust()));
		}

		LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = this.server.registries();
		List<KnownPack> list = this.server.getResourceManager().listPacks().flatMap(packResources -> packResources.location().knownPackInfo().stream()).toList();
		this.send(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(this.server.getWorldData().enabledFeatures())));
		this.synchronizeRegistriesTask = new SynchronizeRegistriesTask(list, layeredRegistryAccess);
		this.configurationTasks.add(this.synchronizeRegistriesTask);
		this.addOptionalTasks();
		this.returnToWorld();
	}

	public void returnToWorld() {
		this.prepareSpawnTask = new PrepareSpawnTask(this.server, new NameAndId(this.gameProfile));
		this.configurationTasks.add(this.prepareSpawnTask);
		this.configurationTasks.add(new JoinWorldTask());
		this.startNextTask();
	}

	private void addOptionalTasks() {
		Map<String, String> map = this.server.getCodeOfConducts();
		if (!map.isEmpty()) {
			this.configurationTasks.add(new ServerCodeOfConductConfigurationTask(() -> {
				String string = (String)map.get(this.clientInformation.language().toLowerCase(Locale.ROOT));
				if (string == null) {
					string = (String)map.get("en_us");
				}

				if (string == null) {
					string = (String)map.values().iterator().next();
				}

				return string;
			}));
		}

		this.server
			.getServerResourcePack()
			.ifPresent(serverResourcePackInfo -> this.configurationTasks.add(new ServerResourcePackConfigurationTask(serverResourcePackInfo)));
	}

	@Override
	public void handleClientInformation(ServerboundClientInformationPacket serverboundClientInformationPacket) {
		this.clientInformation = serverboundClientInformationPacket.information();
	}

	@Override
	public void handleResourcePackResponse(ServerboundResourcePackPacket serverboundResourcePackPacket) {
		super.handleResourcePackResponse(serverboundResourcePackPacket);
		if (serverboundResourcePackPacket.action().isTerminal()) {
			this.finishCurrentTask(ServerResourcePackConfigurationTask.TYPE);
		}
	}

	@Override
	public void handleSelectKnownPacks(ServerboundSelectKnownPacks serverboundSelectKnownPacks) {
		PacketUtils.ensureRunningOnSameThread(serverboundSelectKnownPacks, this, this.server.packetProcessor());
		if (this.synchronizeRegistriesTask == null) {
			throw new IllegalStateException("Unexpected response from client: received pack selection, but no negotiation ongoing");
		} else {
			this.synchronizeRegistriesTask.handleResponse(serverboundSelectKnownPacks.knownPacks(), this::send);
			this.finishCurrentTask(SynchronizeRegistriesTask.TYPE);
		}
	}

	@Override
	public void handleAcceptCodeOfConduct(ServerboundAcceptCodeOfConductPacket serverboundAcceptCodeOfConductPacket) {
		this.finishCurrentTask(ServerCodeOfConductConfigurationTask.TYPE);
	}

	@Override
	public void handleConfigurationFinished(ServerboundFinishConfigurationPacket serverboundFinishConfigurationPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundFinishConfigurationPacket, this, this.server.packetProcessor());
		this.finishCurrentTask(JoinWorldTask.TYPE);
		this.connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess())));

		try {
			PlayerList playerList = this.server.getPlayerList();
			if (playerList.getPlayer(this.gameProfile.getId()) != null) {
				this.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
				return;
			}

			Component component = playerList.canPlayerLogin(this.connection.getRemoteAddress(), new NameAndId(this.gameProfile));
			if (component != null) {
				this.disconnect(component);
				return;
			}

			((PrepareSpawnTask)Objects.requireNonNull(this.prepareSpawnTask)).spawnPlayer(this.connection, this.createCookie(this.clientInformation));
		} catch (Exception var4) {
			LOGGER.error("Couldn't place player in world", (Throwable)var4);
			this.disconnect(DISCONNECT_REASON_INVALID_DATA);
		}
	}

	@Override
	public void tick() {
		this.keepConnectionAlive();
		ConfigurationTask configurationTask = this.currentTask;
		if (configurationTask != null) {
			try {
				if (configurationTask.tick()) {
					this.finishCurrentTask(configurationTask.type());
				}
			} catch (Exception var3) {
				LOGGER.error("Failed to tick configuration task {}", configurationTask.type(), var3);
				this.disconnect(DISCONNECT_REASON_CONFIGURATION_ERROR);
			}
		}

		if (this.prepareSpawnTask != null) {
			this.prepareSpawnTask.keepAlive();
		}
	}

	private void startNextTask() {
		if (this.currentTask != null) {
			throw new IllegalStateException("Task " + this.currentTask.type().id() + " has not finished yet");
		} else if (this.isAcceptingMessages()) {
			ConfigurationTask configurationTask = (ConfigurationTask)this.configurationTasks.poll();
			if (configurationTask != null) {
				this.currentTask = configurationTask;

				try {
					configurationTask.start(this::send);
				} catch (Exception var3) {
					LOGGER.error("Failed to start configuration task {}", configurationTask.type(), var3);
					this.disconnect(DISCONNECT_REASON_CONFIGURATION_ERROR);
				}
			}
		}
	}

	private void finishCurrentTask(ConfigurationTask.Type type) {
		ConfigurationTask.Type type2 = this.currentTask != null ? this.currentTask.type() : null;
		if (!type.equals(type2)) {
			throw new IllegalStateException("Unexpected request for task finish, current task: " + type2 + ", requested: " + type);
		} else {
			this.currentTask = null;
			this.startNextTask();
		}
	}
}
