package net.minecraft.client.multiplayer;

import net.minecraft.server.profile.PlayerProfile;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.security.PublicKey;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.Util;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.Crypt;
import net.minecraft.world.flag.FeatureFlags;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientHandshakePacketListenerImpl implements ClientLoginPacketListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Minecraft minecraft;
	@Nullable
	private final ServerData serverData;
	@Nullable
	private final Screen parent;
	private final Consumer<Component> updateStatus;
	private final Connection connection;
	private final boolean newWorld;
	@Nullable
	private final Duration worldLoadDuration;
	@Nullable
	private String minigameName;
	private final LevelLoadTracker levelLoadTracker;
	private final Map<ResourceLocation, byte[]> cookies;
	private final boolean wasTransferredTo;
	private final Map<UUID, PlayerInfo> seenPlayers;
	private final boolean seenInsecureChatWarning;
	private final AtomicReference<ClientHandshakePacketListenerImpl.State> state = new AtomicReference(ClientHandshakePacketListenerImpl.State.CONNECTING);

	public ClientHandshakePacketListenerImpl(
		Connection connection,
		Minecraft minecraft,
		@Nullable ServerData serverData,
		@Nullable Screen screen,
		boolean bl,
		@Nullable Duration duration,
		Consumer<Component> consumer,
		LevelLoadTracker levelLoadTracker,
		@Nullable TransferState transferState
	) {
		this.connection = connection;
		this.minecraft = minecraft;
		this.serverData = serverData;
		this.parent = screen;
		this.updateStatus = consumer;
		this.newWorld = bl;
		this.worldLoadDuration = duration;
		this.levelLoadTracker = levelLoadTracker;
		this.cookies = transferState != null ? new HashMap(transferState.cookies()) : new HashMap();
		this.seenPlayers = transferState != null ? transferState.seenPlayers() : Map.of();
		this.seenInsecureChatWarning = transferState != null ? transferState.seenInsecureChatWarning() : false;
		this.wasTransferredTo = transferState != null;
	}

	private void switchState(ClientHandshakePacketListenerImpl.State state) {
		ClientHandshakePacketListenerImpl.State state2 = (ClientHandshakePacketListenerImpl.State)this.state.updateAndGet(state2x -> {
			if (!state.fromStates.contains(state2x)) {
				throw new IllegalStateException("Tried to switch to " + state + " from " + state2x + ", but expected one of " + state.fromStates);
			} else {
				return state;
			}
		});
		this.updateStatus.accept(state2.message);
	}

	public void handleHello(ClientboundHelloPacket clientboundHelloPacket) {
		this.switchState(ClientHandshakePacketListenerImpl.State.AUTHORIZING);

		Cipher cipher;
		Cipher cipher2;
		String string;
		ServerboundKeyPacket serverboundKeyPacket;
		try {
			SecretKey secretKey = Crypt.generateSecretKey();
			PublicKey publicKey = clientboundHelloPacket.getPublicKey();
			string = new BigInteger(Crypt.digestData(clientboundHelloPacket.getServerId(), publicKey, secretKey)).toString(16);
			cipher = Crypt.getCipher(2, secretKey);
			cipher2 = Crypt.getCipher(1, secretKey);
			byte[] bs = clientboundHelloPacket.getChallenge();
			serverboundKeyPacket = new ServerboundKeyPacket(secretKey, publicKey, bs);
		} catch (Exception var9) {
			throw new IllegalStateException("Protocol error", var9);
		}

		if (clientboundHelloPacket.shouldAuthenticate()) {
			Util.ioPool().execute(() -> {
				Component component = this.authenticateServer(string);
				if (component != null) {
					if (this.serverData == null || !this.serverData.isLan()) {
						this.connection.disconnect(component);
						return;
					}

					LOGGER.warn(component.getString());
				}

				this.setEncryption(serverboundKeyPacket, cipher, cipher2);
			});
		} else {
			this.setEncryption(serverboundKeyPacket, cipher, cipher2);
		}
	}

	private void setEncryption(ServerboundKeyPacket serverboundKeyPacket, Cipher cipher, Cipher cipher2) {
		this.switchState(ClientHandshakePacketListenerImpl.State.ENCRYPTING);
		this.connection.send(serverboundKeyPacket, PacketSendListener.thenRun(() -> this.connection.setEncryptionKey(cipher, cipher2)));
	}

	@Nullable
	private Component authenticateServer(String string) {
		try {
			this.minecraft.services().sessionService().joinServer(this.minecraft.getUser().getProfileId(), this.minecraft.getUser().getAccessToken(), string);
			return null;
		} catch (AuthenticationUnavailableException var3) {
			return Component.translatable("disconnect.loginFailedInfo", new Object[]{Component.translatable("disconnect.loginFailedInfo.serversUnavailable")});
		} catch (InvalidCredentialsException var4) {
			return Component.translatable("disconnect.loginFailedInfo", new Object[]{Component.translatable("disconnect.loginFailedInfo.invalidSession")});
		} catch (InsufficientPrivilegesException var5) {
			return Component.translatable("disconnect.loginFailedInfo", new Object[]{Component.translatable("disconnect.loginFailedInfo.insufficientPrivileges")});
		} catch (ForcedUsernameChangeException | UserBannedException var6) {
			return Component.translatable("disconnect.loginFailedInfo", new Object[]{Component.translatable("disconnect.loginFailedInfo.userBanned")});
		} catch (AuthenticationException var7) {
			return Component.translatable("disconnect.loginFailedInfo", new Object[]{var7.getMessage()});
		}
	}

	public void handleLoginFinished(ClientboundLoginFinishedPacket clientboundLoginFinishedPacket) {
		this.switchState(ClientHandshakePacketListenerImpl.State.JOINING);
		PlayerProfile playerProfile = clientboundLoginFinishedPacket.gameProfile();
		this.connection
			.setupInboundProtocol(
				ConfigurationProtocols.CLIENTBOUND,
				new ClientConfigurationPacketListenerImpl(
					this.minecraft,
					this.connection,
					new CommonListenerCookie(
						this.levelLoadTracker,
						gameProfile,
						ClientRegistryLayer.createRegistryAccess().compositeAccess(),
						FeatureFlags.DEFAULT_FLAGS,
						null,
						this.serverData,
						this.parent,
						this.cookies,
						null,
						Map.of(),
						ServerLinks.EMPTY,
						this.seenPlayers,
						false
					)
				)
			);
		this.connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
		this.connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
		this.connection.send(new ServerboundCustomPayloadPacket(new BrandPayload(ClientBrandRetriever.getClientModName())));
		this.connection.send(new ServerboundClientInformationPacket(this.minecraft.options.buildPlayerInformation()));
	}

	public void onDisconnect(DisconnectionDetails disconnectionDetails) {
		Component component = this.wasTransferredTo ? CommonComponents.TRANSFER_CONNECT_FAILED : CommonComponents.CONNECT_FAILED;
		this.minecraft.setScreen(new DisconnectedScreen(this.parent, component, disconnectionDetails));
	}

	public boolean isAcceptingMessages() {
		return this.connection.isConnected();
	}

	public void handleDisconnect(ClientboundLoginDisconnectPacket clientboundLoginDisconnectPacket) {
		this.connection.disconnect(clientboundLoginDisconnectPacket.reason());
	}

	public void handleCompression(ClientboundLoginCompressionPacket clientboundLoginCompressionPacket) {
		if (!this.connection.isMemoryConnection()) {
			this.connection.setupCompression(clientboundLoginCompressionPacket.getCompressionThreshold(), false);
		}
	}

	public void handleCustomQuery(ClientboundCustomQueryPacket clientboundCustomQueryPacket) {
		this.updateStatus.accept(Component.translatable("connect.negotiating"));
		this.connection.send(new ServerboundCustomQueryAnswerPacket(clientboundCustomQueryPacket.transactionId(), null));
	}

	public void setMinigameName(@Nullable String string) {
		this.minigameName = string;
	}

	public void handleRequestCookie(ClientboundCookieRequestPacket clientboundCookieRequestPacket) {
		this.connection
			.send(new ServerboundCookieResponsePacket(clientboundCookieRequestPacket.key(), (byte[])this.cookies.get(clientboundCookieRequestPacket.key())));
	}

	public void fillListenerSpecificCrashDetails(CrashReport crashReport, CrashReportCategory crashReportCategory) {
		crashReportCategory.setDetail("Server type", () -> this.serverData != null ? this.serverData.type().toString() : "<unknown>");
		crashReportCategory.setDetail("Login phase", () -> ((ClientHandshakePacketListenerImpl.State)this.state.get()).toString());
		crashReportCategory.setDetail("Is Local", () -> String.valueOf(this.connection.isMemoryConnection()));
	}

	@Environment(EnvType.CLIENT)
	static enum State {
		CONNECTING(Component.translatable("connect.connecting"), Set.of()),
		AUTHORIZING(Component.translatable("connect.authorizing"), Set.of(CONNECTING)),
		ENCRYPTING(Component.translatable("connect.encrypting"), Set.of(AUTHORIZING)),
		JOINING(Component.translatable("connect.joining"), Set.of(ENCRYPTING, CONNECTING));

		final Component message;
		final Set<ClientHandshakePacketListenerImpl.State> fromStates;

		private State(final Component component, final Set<ClientHandshakePacketListenerImpl.State> set) {
			this.message = component;
			this.fromStates = set;
		}
	}
}
