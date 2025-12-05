package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsJoinInformation;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.RealmsBrokenWorldScreen;
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoConnectTaskScreen;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import com.mojang.realmsclient.gui.screens.RealmsTermsScreen;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GetServerDetailsTask extends LongRunningTask {
	private static final Component APPLYING_PACK_TEXT = Component.translatable("multiplayer.applyingPack");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.translatable("mco.connect.connecting");
	private final RealmsServer server;
	private final Screen lastScreen;

	public GetServerDetailsTask(Screen screen, RealmsServer realmsServer) {
		this.lastScreen = screen;
		this.server = realmsServer;
	}

	public void run() {
		RealmsJoinInformation realmsJoinInformation;
		try {
			realmsJoinInformation = this.fetchServerAddress();
		} catch (CancellationException var4) {
			LOGGER.info("User aborted connecting to realms");
			return;
		} catch (RealmsServiceException var5) {
			switch (var5.realmsError.errorCode()) {
				case 6002:
					setScreen(new RealmsTermsScreen(this.lastScreen, this.server));
					return;
				case 6006:
					boolean bl = Minecraft.getInstance().isLocalPlayer(this.server.ownerUUID);
					setScreen(
						(Screen)(bl
							? new RealmsBrokenWorldScreen(this.lastScreen, this.server.id, this.server.isMinigameActive())
							: new RealmsGenericErrorScreen(
								Component.translatable("mco.brokenworld.nonowner.title"), Component.translatable("mco.brokenworld.nonowner.error"), this.lastScreen
							))
					);
					return;
				default:
					this.error(var5);
					LOGGER.error("Couldn't connect to world", (Throwable)var5);
					return;
			}
		} catch (TimeoutException var6) {
			this.error(Component.translatable("mco.errorMessage.connectionFailure"));
			return;
		} catch (Exception var7) {
			LOGGER.error("Couldn't connect to world", (Throwable)var7);
			this.error(var7);
			return;
		}

		if (realmsJoinInformation.address() == null) {
			this.error(Component.translatable("mco.errorMessage.connectionFailure"));
		} else {
			boolean bl2 = realmsJoinInformation.resourcePackUrl() != null && realmsJoinInformation.resourcePackHash() != null;
			Screen screen = (Screen)(bl2
				? this.resourcePackDownloadConfirmationScreen(realmsJoinInformation, generatePackId(this.server), this::connectScreen)
				: this.connectScreen(realmsJoinInformation));
			setScreen(screen);
		}
	}

	private static UUID generatePackId(RealmsServer realmsServer) {
		return realmsServer.minigameName != null
			? UUID.nameUUIDFromBytes(("minigame:" + realmsServer.minigameName).getBytes(StandardCharsets.UTF_8))
			: UUID.nameUUIDFromBytes(
				("realms:" + (String)Objects.requireNonNullElse(realmsServer.name, "") + ":" + realmsServer.activeSlot).getBytes(StandardCharsets.UTF_8)
			);
	}

	@Override
	public Component getTitle() {
		return TITLE;
	}

	private RealmsJoinInformation fetchServerAddress() throws RealmsServiceException, TimeoutException, CancellationException {
		RealmsClient realmsClient = RealmsClient.getOrCreate();

		for (int i = 0; i < 40; i++) {
			if (this.aborted()) {
				throw new CancellationException();
			}

			try {
				return realmsClient.join(this.server.id);
			} catch (RetryCallException var4) {
				pause(var4.delaySeconds);
			}
		}

		throw new TimeoutException();
	}

	public RealmsLongRunningMcoTaskScreen connectScreen(RealmsJoinInformation realmsJoinInformation) {
		return new RealmsLongRunningMcoConnectTaskScreen(this.lastScreen, realmsJoinInformation, new ConnectTask(this.lastScreen, this.server, realmsJoinInformation));
	}

	private PopupScreen resourcePackDownloadConfirmationScreen(
		RealmsJoinInformation realmsJoinInformation, UUID uUID, Function<RealmsJoinInformation, Screen> function
	) {
		Component component = Component.translatable("mco.configure.world.resourcepack.question");
		return RealmsPopups.infoPopupScreen(
			this.lastScreen,
			component,
			popupScreen -> {
				setScreen(new GenericMessageScreen(APPLYING_PACK_TEXT));
				this.scheduleResourcePackDownload(realmsJoinInformation, uUID)
					.thenRun(() -> setScreen((Screen)function.apply(realmsJoinInformation)))
					.exceptionally(throwable -> {
						Minecraft.getInstance().getDownloadedPackSource().cleanupAfterDisconnect();
						LOGGER.error("Failed to download resource pack from {}", realmsJoinInformation, throwable);
						setScreen(new RealmsGenericErrorScreen(Component.translatable("mco.download.resourcePack.fail"), this.lastScreen));
						return null;
					});
			}
		);
	}

	private CompletableFuture<?> scheduleResourcePackDownload(RealmsJoinInformation realmsJoinInformation, UUID uUID) {
		try {
			if (realmsJoinInformation.resourcePackUrl() == null) {
				return CompletableFuture.failedFuture(new IllegalStateException("resourcePackUrl was null"));
			} else if (realmsJoinInformation.resourcePackHash() == null) {
				return CompletableFuture.failedFuture(new IllegalStateException("resourcePackHash was null"));
			} else {
				DownloadedPackSource downloadedPackSource = Minecraft.getInstance().getDownloadedPackSource();
				CompletableFuture<Void> completableFuture = downloadedPackSource.waitForPackFeedback(uUID);
				downloadedPackSource.allowServerPacks();
				downloadedPackSource.pushPack(uUID, new URL(realmsJoinInformation.resourcePackUrl()), realmsJoinInformation.resourcePackHash());
				return completableFuture;
			}
		} catch (Exception var5) {
			return CompletableFuture.failedFuture(var5);
		}
	}
}
