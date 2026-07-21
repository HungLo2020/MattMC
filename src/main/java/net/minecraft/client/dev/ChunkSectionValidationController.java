package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.ai.village.poi.PoiReadDiagnostics;
import net.minecraft.world.level.chunk.storage.ChunkSectionReadDiagnostics;
import net.minecraft.world.level.chunk.storage.EntityReadDiagnostics;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only copied-world validator for Rust typed chunk-section reads.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.chunkSectionValidation=true}. It keeps
 * the player still, waits for a usable singleplayer world, lets normal chunk
 * loading run through the Rust section path, then asks the client to stop
 * cleanly so the capture wrapper can relaunch the copied world.
 */
public final class ChunkSectionValidationController {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.chunkSectionValidation");
	private static final String CUSTOM_ROOT_FIELD = "mattmc:chunk_section_validation_custom";
	private static final int SETTLE_TICKS = Math.max(1, Integer.getInteger("mattmc.dev.chunkSectionValidation.settleTicks", 240));
	private static final int MAX_READY_WAIT_TICKS = Math.max(20, Integer.getInteger("mattmc.dev.chunkSectionValidation.maxReadyWaitTicks", 2400));

	private static int ticks;
	private static int readyTicks;
	private static boolean readyRecorded;
	private static boolean chunksForcedDirty;
	private static boolean stopIssued;
	private static boolean failureRecorded;

	private ChunkSectionValidationController() {
	}

	public static void beforeTick(Minecraft minecraft) {
		if (!ENABLED) {
			return;
		}

		ticks++;
		minecraft.options.pauseOnLostFocus = false;
		freezeInput(minecraft.player);
		if (stopIssued) {
			return;
		}

		if (!isWorldReady(minecraft)) {
			if (ticks > MAX_READY_WAIT_TICKS && !failureRecorded) {
				failureRecorded = true;
				ChunkSectionReadDiagnostics.recordFailure("Timed out waiting for loaded singleplayer world");
				minecraft.stop();
			}
			return;
		}

		if (!readyRecorded) {
			readyRecorded = true;
			ChunkSectionReadDiagnostics.recordWorldReady();
		}
		if (ChunkSectionReadDiagnostics.writeValidationEnabled() && !chunksForcedDirty) {
			chunksForcedDirty = forceLoadedChunksDirty(minecraft) > 0;
		}

		readyTicks++;
		if (readyTicks >= SETTLE_TICKS) {
			if (EntityReadDiagnostics.validationAwaitingShutdown()) {
				return;
			}
			stopIssued = true;
			ChunkSectionReadDiagnostics.recordSaveRequested();
			ChunkSectionReadDiagnostics.recordShutdownRequested();
			ChunkSectionReadDiagnostics.recordStopped();
			stopWhenAllStorageValidationFinished(minecraft);
		}
	}

	private static void stopWhenAllStorageValidationFinished(Minecraft minecraft) {
		if (!EntityReadDiagnostics.validationAwaitingShutdown() && !PoiReadDiagnostics.validationAwaitingShutdown()) {
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

	private static int forceLoadedChunksDirty(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		LocalPlayer player = minecraft.player;
		if (server == null || player == null) {
			return 0;
		}

		ChunkPos center = player.chunkPosition();
		int forced = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (int dx = -8; dx <= 8; dx++) {
				for (int dz = -8; dz <= 8; dz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
					if (chunk == null) {
						continue;
					}
					CompoundTag residual = chunk.getRustChunkSectionResidual();
					if (residual == null) {
						continue;
					}
					boolean observed = residual.contains(CUSTOM_ROOT_FIELD);
					boolean injected = false;
					if (!observed) {
						residual.putString(CUSTOM_ROOT_FIELD, System.getProperty("mattmc.dev.runCaptureId", "unknown"));
						chunk.setRustChunkSectionResidual(residual);
						injected = true;
					}
					chunk.markUnsaved();
					ChunkSectionReadDiagnostics.forcedValidationChunk(injected, observed);
					forced++;
				}
			}
		}
		return forced;
	}
}
