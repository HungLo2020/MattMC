package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.entity.ai.village.poi.PoiReadDiagnostics;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.chunk.storage.ChunkSectionReadDiagnostics;
import net.minecraft.world.level.chunk.storage.EntityReadDiagnostics;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only POI save/load validator.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.poiValidation=true}. It waits for a
 * usable copied singleplayer world, keeps the player still, lets POI chunks
 * load and become dirty through {@link PoiReadDiagnostics}, then requests a
 * normal client stop so shutdown performs the production save path.
 */
public final class PoiStorageValidationController {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.poiValidation");
	private static final int SETTLE_TICKS = Math.max(1, Integer.getInteger("mattmc.dev.poiValidation.settleTicks", 240));
	private static final int MAX_READY_WAIT_TICKS = Math.max(20, Integer.getInteger("mattmc.dev.poiValidation.maxReadyWaitTicks", 2400));

	private static int ticks;
	private static int readyTicks;
	private static boolean readyRecorded;
	private static boolean stopIssued;
	private static boolean failureRecorded;

	private PoiStorageValidationController() {
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
				PoiReadDiagnostics.recordFailure("Timed out waiting for loaded singleplayer world");
				minecraft.stop();
			}
			return;
		}

		if (!readyRecorded) {
			readyRecorded = true;
			PoiReadDiagnostics.recordWorldReady();
		}

		readyTicks++;
		if (readyTicks >= SETTLE_TICKS) {
			if (EntityReadDiagnostics.validationAwaitingShutdown()) {
				return;
			}
			stopIssued = true;
			PoiReadDiagnostics.recordSaveRequested();
			PoiReadDiagnostics.recordShutdownRequested();
			PoiReadDiagnostics.recordStopped();
			stopWhenAllStorageValidationFinished(minecraft);
		}
	}

	private static void stopWhenAllStorageValidationFinished(Minecraft minecraft) {
		if (!EntityReadDiagnostics.validationAwaitingShutdown() && !ChunkSectionReadDiagnostics.validationAwaitingShutdown()) {
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
}
