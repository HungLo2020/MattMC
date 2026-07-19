package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.storage.StoragePerfDiagnostics;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only controller for storage performance save/load runs.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.storagePerfRun=true}. It waits for a
 * usable quick-play singleplayer world, freezes local movement input, lets the
 * world idle for a fixed number of ticks, then requests a normal client stop so
 * the integrated server runs the production shutdown/save path.</p>
 */
public final class StoragePerfRunController {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.storagePerfRun");
	private static final int SETTLE_TICKS = Math.max(1, Integer.getInteger("mattmc.dev.storagePerfRun.settleTicks", 240));
	private static final int MAX_READY_WAIT_TICKS = Math.max(20, Integer.getInteger("mattmc.dev.storagePerfRun.maxReadyWaitTicks", 2400));
	private static final int RENDER_DISTANCE = Math.max(2, Integer.getInteger("mattmc.dev.storagePerfRun.renderDistance", 12));
	private static final int SIMULATION_DISTANCE = Math.max(2, Integer.getInteger("mattmc.dev.storagePerfRun.simulationDistance", 12));
	private static final int STABLE_STORAGE_TICKS = Math.max(0, Integer.getInteger("mattmc.dev.storagePerfRun.stableStorageTicks", 0));
	private static final boolean FREEZE_GAME_TICKS = Boolean.parseBoolean(System.getProperty("mattmc.dev.storagePerfRun.freezeGameTicks", "true"));

	private static int ticks;
	private static int readyTicks;
	private static int stableStorageTicks;
	private static int lastLoadedChunkCount = -1;
	private static boolean readyRecorded;
	private static boolean stopIssued;
	private static boolean failureRecorded;

	private StoragePerfRunController() {
	}

	public static void beforeTick(Minecraft minecraft) {
		if (!ENABLED) {
			return;
		}

		ticks++;
		minecraft.options.pauseOnLostFocus = false;
		minecraft.options.renderDistance().set(RENDER_DISTANCE);
		minecraft.options.simulationDistance().set(SIMULATION_DISTANCE);
		freezeGameTicks(minecraft.getSingleplayerServer());
		freezeInput(minecraft.player);
		if (stopIssued) {
			return;
		}

		if (!isWorldReady(minecraft) || !isStorageStable(minecraft.getSingleplayerServer())) {
			if (ticks > MAX_READY_WAIT_TICKS && !failureRecorded) {
				failureRecorded = true;
				StoragePerfDiagnostics.recordRunError("Timed out waiting for loaded singleplayer world");
				StoragePerfDiagnostics.writeStatus();
				minecraft.stop();
			}
			return;
		}

		if (!readyRecorded) {
			readyRecorded = true;
			StoragePerfDiagnostics.recordWorldReady();
			StoragePerfDiagnostics.writeStatus();
		}

		readyTicks++;
		if (readyTicks >= SETTLE_TICKS) {
			stopIssued = true;
			StoragePerfDiagnostics.recordSaveRequested();
			StoragePerfDiagnostics.recordShutdownRequested();
			StoragePerfDiagnostics.recordStopped();
			StoragePerfDiagnostics.writeStatus();
			minecraft.stop();
		}
	}

	private static boolean isWorldReady(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		return minecraft.level != null
			&& minecraft.player != null
			&& minecraft.getConnection() != null
			&& server != null
			&& server.isReady();
	}

	private static void freezeGameTicks(IntegratedServer server) {
		if (!FREEZE_GAME_TICKS || server == null || server.tickRateManager().isFrozen()) {
			return;
		}

		server.tickRateManager().setFrozen(true);
	}

	private static boolean isStorageStable(IntegratedServer server) {
		if (STABLE_STORAGE_TICKS == 0) {
			return true;
		}
		if (server == null) {
			return false;
		}

		int loadedChunks = 0;
		boolean hasWork = false;
		for (ServerLevel level : server.getAllLevels()) {
			loadedChunks += level.getChunkSource().getLoadedChunksCount();
			hasWork |= level.getChunkSource().chunkMap.hasWork();
		}

		if (hasWork || loadedChunks != lastLoadedChunkCount) {
			lastLoadedChunkCount = loadedChunks;
			stableStorageTicks = 0;
			return false;
		}

		stableStorageTicks++;
		return stableStorageTicks >= STABLE_STORAGE_TICKS;
	}

	private static void freezeInput(LocalPlayer player) {
		if (player == null) {
			return;
		}

		player.input.keyPresses = Input.EMPTY;
		player.xxa = 0.0F;
		player.zza = 0.0F;
		player.setSprinting(false);
		player.setShiftKeyDown(false);
		player.setDeltaMovement(Vec3.ZERO);
	}
}
