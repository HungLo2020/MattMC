package net.minecraft.client.server;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayer.SavedPosition;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.Stats;
import net.minecraft.util.ModCheck;
import net.minecraft.util.ProblemReporter.ScopedCollector;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class IntegratedServer extends MinecraftServer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MIN_SIM_DISTANCE = 2;
	public static final int MAX_PLAYERS = 8;
	private final Minecraft minecraft;
	private boolean paused = true;
	private int publishedPort = -1;
	@Nullable
	private GameType publishedGameType;
	@Nullable
	private LanServerPinger lanPinger;
	@Nullable
	private UUID uuid;
	private int previousSimulationDistance = 0;

	public IntegratedServer(
		Thread thread,
		Minecraft minecraft,
		LevelStorageAccess levelStorageAccess,
		PackRepository packRepository,
		WorldStem worldStem,
		Services services,
		LevelLoadListener levelLoadListener
	) {
		super(thread, levelStorageAccess, packRepository, worldStem, minecraft.getProxy(), minecraft.getFixerUpper(), services, levelLoadListener);
		this.setSingleplayerProfile(minecraft.getGameProfile());
		this.setDemo(minecraft.isDemo());
		this.setPlayerList(new IntegratedPlayerList(this, this.registries(), this.playerDataStorage));
		this.minecraft = minecraft;
	}

	public boolean initServer() {
		LOGGER.info("Starting integrated minecraft server version {}", SharedConstants.getCurrentVersion().name());
		this.setUsesAuthentication(true);
		this.initializeKeyPair();
		this.loadLevel();
		GameProfile gameProfile = this.getSingleplayerProfile();
		String string = this.getWorldData().getLevelName();
			this.setMotd(gameProfile != null ? gameProfile.getName() + " - " + string : string);
		return true;
	}

	public boolean isPaused() {
		return this.paused;
	}

	public void tickServer(BooleanSupplier booleanSupplier) {
		boolean bl = this.paused;
		this.paused = Minecraft.getInstance().isPaused() || this.getPlayerList().getPlayers().isEmpty();
		ProfilerFiller profilerFiller = Profiler.get();
		if (!bl && this.paused) {
			profilerFiller.push("autoSave");
			LOGGER.info("Saving and pausing game...");
			this.saveEverything(false, false, false);
			profilerFiller.pop();
		}

		if (this.paused) {
			this.tickPaused();
		} else {
			if (bl) {
				this.forceTimeSynchronization();
			}

			super.tickServer(booleanSupplier);
			int i = Math.max(2, this.minecraft.options.renderDistance().get());
			if (i != this.getPlayerList().getViewDistance()) {
				LOGGER.info("Changing view distance to {}, from {}", i, this.getPlayerList().getViewDistance());
				this.getPlayerList().setViewDistance(i);
			}

			int j = Math.max(2, this.minecraft.options.simulationDistance().get());
			if (j != this.previousSimulationDistance) {
				LOGGER.info("Changing simulation distance to {}, from {}", j, this.previousSimulationDistance);
				this.getPlayerList().setSimulationDistance(j);
				this.previousSimulationDistance = j;
			}
		}
	}

	protected LocalSampleLogger getTickTimeLogger() {
		return this.minecraft.getDebugOverlay().getTickTimeLogger();
	}

	public boolean isTickTimeLoggingEnabled() {
		return true;
	}

	private void tickPaused() {
		this.tickConnection();

		for (ServerPlayer serverPlayer : this.getPlayerList().getPlayers()) {
			serverPlayer.awardStat(Stats.TOTAL_WORLD_TIME);
		}
	}

	public boolean shouldRconBroadcast() {
		return true;
	}

	public boolean shouldInformAdmins() {
		return true;
	}

	public Path getServerDirectory() {
		return this.minecraft.gameDirectory.toPath();
	}

	public boolean isDedicatedServer() {
		return false;
	}

	public int getRateLimitPacketsPerSecond() {
		return 0;
	}

	public boolean isEpollEnabled() {
		return false;
	}

	public void onServerCrash(CrashReport crashReport) {
		this.minecraft.delayCrashRaw(crashReport);
	}

	public SystemReport fillServerSystemReport(SystemReport systemReport) {
		systemReport.setDetail("Type", "Integrated Server (map_client.txt)");
		systemReport.setDetail("Is Modded", () -> this.getModdedStatus().fullDescription());
		systemReport.setDetail("Launched Version", this.minecraft::getLaunchedVersion);
		return systemReport;
	}

	public ModCheck getModdedStatus() {
		return Minecraft.checkModStatus().merge(super.getModdedStatus());
	}

	public boolean publishServer(@Nullable GameType gameType, boolean bl, int i) {
		try {
			this.minecraft.prepareForMultiplayer();
			this.minecraft.getConnection().prepareKeyPair();
			this.getConnection().startTcpServerListener(null, i);
			LOGGER.info("Started serving on {}", i);
			this.publishedPort = i;
			this.lanPinger = new LanServerPinger(this.getMotd(), i + "");
			this.lanPinger.start();
			this.publishedGameType = gameType;
			this.getPlayerList().setAllowCommandsForAllPlayers(bl);
			int j = this.getProfilePermissions(this.minecraft.player.nameAndId());
			this.minecraft.player.setPermissionLevel(j);

			for (ServerPlayer serverPlayer : this.getPlayerList().getPlayers()) {
				this.getCommands().sendCommands(serverPlayer);
			}

			return true;
		} catch (IOException var7) {
			return false;
		}
	}

	public void stopServer() {
		super.stopServer();
		if (this.lanPinger != null) {
			this.lanPinger.interrupt();
			this.lanPinger = null;
		}
	}

	public void halt(boolean bl) {
		this.executeBlocking(() -> {
			for (ServerPlayer serverPlayer : Lists.newArrayList(this.getPlayerList().getPlayers())) {
				if (!serverPlayer.getUUID().equals(this.uuid)) {
					this.getPlayerList().remove(serverPlayer);
				}
			}
		});
		super.halt(bl);
		if (this.lanPinger != null) {
			this.lanPinger.interrupt();
			this.lanPinger = null;
		}
	}

	public boolean isPublished() {
		return this.publishedPort > -1;
	}

	public int getPort() {
		return this.publishedPort;
	}

	public void setDefaultGameType(GameType gameType) {
		super.setDefaultGameType(gameType);
		this.publishedGameType = null;
	}

	public int operatorUserPermissionLevel() {
		return 2;
	}

	public int getFunctionCompilationLevel() {
		return 2;
	}

	public void setUUID(UUID uUID) {
		this.uuid = uUID;
	}

		public boolean isSingleplayerOwner(NameAndId nameAndId) {
		return this.getSingleplayerProfile() != null && nameAndId.name().equalsIgnoreCase(this.getSingleplayerProfile().getName());
	}

	public int getScaledTrackingDistance(int i) {
		return (int)(this.minecraft.options.entityDistanceScaling().get() * i);
	}

	public boolean forceSynchronousWrites() {
		return this.minecraft.options.syncWrites;
	}

	@Nullable
	public GameType getForcedGameType() {
		return this.isPublished() && !this.isHardcore() ? MoreObjects.firstNonNull(this.publishedGameType, this.worldData.getGameType()) : null;
	}

	public GlobalPos selectLevelLoadFocusPos() {
		CompoundTag compoundTag = this.worldData.getLoadedPlayerTag();
		if (compoundTag == null) {
			return super.selectLevelLoadFocusPos();
		} else {
			ScopedCollector scopedCollector = new ScopedCollector(LOGGER);

			GlobalPos var5;
			label31: {
				try {
					ValueInput valueInput = TagValueInput.create(scopedCollector, this.registryAccess(), compoundTag);
					SavedPosition savedPosition = (SavedPosition)valueInput.read(SavedPosition.MAP_CODEC).orElse(SavedPosition.EMPTY);
					if (savedPosition.dimension().isPresent() && savedPosition.position().isPresent()) {
						var5 = new GlobalPos((ResourceKey)savedPosition.dimension().get(), BlockPos.containing((Position)savedPosition.position().get()));
						break label31;
					}
				} catch (Throwable var7) {
					try {
						scopedCollector.close();
					} catch (Throwable var6) {
						var7.addSuppressed(var6);
					}

					throw var7;
				}

				scopedCollector.close();
				return super.selectLevelLoadFocusPos();
			}

			scopedCollector.close();
			return var5;
		}
	}

	public boolean saveEverything(boolean bl, boolean bl2, boolean bl3) {
		boolean bl4 = super.saveEverything(bl, bl2, bl3);
		this.warnOnLowDiskSpace();
		return bl4;
	}

	private void warnOnLowDiskSpace() {
		if (this.storageSource.checkForLowDiskSpace()) {
			this.minecraft.execute(() -> SystemToast.onLowDiskSpace(this.minecraft));
		}
	}

	public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
		super.reportChunkLoadFailure(throwable, regionStorageInfo, chunkPos);
		this.warnOnLowDiskSpace();
		this.minecraft.execute(() -> SystemToast.onChunkLoadFailure(this.minecraft, chunkPos));
	}

	public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
		super.reportChunkSaveFailure(throwable, regionStorageInfo, chunkPos);
		this.warnOnLowDiskSpace();
		this.minecraft.execute(() -> SystemToast.onChunkSaveFailure(this.minecraft, chunkPos));
	}

	public int getMaxPlayers() {
		return 8;
	}
}
