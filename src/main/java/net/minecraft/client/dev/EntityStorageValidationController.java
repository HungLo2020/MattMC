package net.minecraft.client.dev;

import net.logging.LogUtils;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.UUIDUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.ai.village.poi.PoiReadDiagnostics;
import net.minecraft.world.level.chunk.storage.ChunkSectionReadDiagnostics;
import net.minecraft.world.level.chunk.storage.EntityReadDiagnostics;
import net.minecraft.world.level.storage.NativeEntityValueInput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Development-only copied-world validator for Rust entity reads.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.entityValidation=true}. It waits for a
 * usable integrated-server world, keeps input stationary while entity regions
 * are read, then requests a normal save/shutdown. The actual parity checks run
 * inside {@link net.minecraft.world.level.chunk.storage.EntityStorage}, where a
 * real {@link net.minecraft.server.level.ServerLevel} and runtime registries
 * are already available.
 */
public final class EntityStorageValidationController {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.entityValidation");
	private static final int SETTLE_TICKS = Math.max(1, Integer.getInteger("mattmc.dev.entityValidation.settleTicks", 240));
	private static final int MAX_READY_WAIT_TICKS = Math.max(20, Integer.getInteger("mattmc.dev.entityValidation.maxReadyWaitTicks", 2400));

	private static int ticks;
	private static int readyTicks;
	private static boolean readyRecorded;
	private static boolean generatedParityChecked;
	private static CompletableFuture<Integer> entitySaveFuture;
	private static boolean stopIssued;
	private static boolean failureRecorded;

	private EntityStorageValidationController() {
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
				EntityReadDiagnostics.recordFailure("Timed out waiting for loaded singleplayer world");
				minecraft.stop();
			}
			return;
		}

		if (!readyRecorded) {
			readyRecorded = true;
			EntityReadDiagnostics.recordWorldReady();
		}

		if (!generatedParityChecked) {
			generatedParityChecked = true;
			try {
				runGeneratedBehaviorParity(minecraft.getSingleplayerServer().overworld());
			} catch (Exception exception) {
				failureRecorded = true;
				EntityReadDiagnostics.recordFailure("Generated entity parity failed: " + exception.getMessage());
				minecraft.stop();
				return;
			}
		}

		readyTicks++;
		if (readyTicks >= SETTLE_TICKS) {
			if (entitySaveFuture == null) {
				entitySaveFuture = requestEntitySave(minecraft);
				EntityReadDiagnostics.recordSaveRequested();
				return;
			}

			if (!entitySaveFuture.isDone()) {
				return;
			}

			int savedChunks;
			try {
				savedChunks = entitySaveFuture.join();
			} catch (Exception exception) {
				failureRecorded = true;
				EntityReadDiagnostics.recordFailure("Entity validation save failed: " + exception.getMessage());
				minecraft.stop();
				return;
			}

			if (savedChunks <= 0) {
				failureRecorded = true;
				EntityReadDiagnostics.recordFailure("Entity validation found no loaded saveable entity chunks to write");
				minecraft.stop();
				return;
			}

			stopIssued = true;
			EntityReadDiagnostics.recordShutdownRequested();
			EntityReadDiagnostics.recordStopped();
			stopWhenAllStorageValidationFinished(minecraft);
		}
	}

	private static void stopWhenAllStorageValidationFinished(Minecraft minecraft) {
		if (!PoiReadDiagnostics.validationAwaitingShutdown() && !ChunkSectionReadDiagnostics.validationAwaitingShutdown()) {
			minecraft.stop();
		}
	}

	private static CompletableFuture<Integer> requestEntitySave(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			return CompletableFuture.completedFuture(0);
		}

		return server.submit(() -> {
			int i = 0;
			for (ServerLevel level : server.getAllLevels()) {
				i += level.entityManager.saveLoadedChunksForStorageValidation();
			}
			return i;
		});
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

	private static void runGeneratedBehaviorParity(ServerLevel level) throws IOException {
		compareGeneratedCase(level, "valid-nested-passenger", List.of(validEntity("minecraft:marker", validEntity("minecraft:marker"))));
		compareGeneratedCase(level, "unknown-id", List.of(entityWithId("mattmc:not_registered")));
		compareGeneratedCase(level, "malformed-id", List.of(entityWithMalformedId()));
	}

	private static void compareGeneratedCase(ServerLevel level, String caseName, List<CompoundTag> entityTags) throws IOException {
		List<Entity> javaEntities = loadJava(level, entityTags);
		List<Entity> rustEntities = loadRust(level, entityTags);
		String mismatch = compareEntityLists(javaEntities, rustEntities);
		if (mismatch == null) {
			EntityReadDiagnostics.generatedBehaviorMatch(caseName, javaEntities.size(), rustEntities.size());
			return;
		}
		EntityReadDiagnostics.generatedBehaviorMismatch(caseName, mismatch);
		throw new IOException(caseName + ": " + mismatch);
	}

	private static List<Entity> loadJava(ServerLevel level, List<CompoundTag> entityTags) {
		CompoundTag root = new CompoundTag();
		ListTag list = new ListTag();
		entityTags.forEach(list::add);
		root.put("Entities", list);
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
			ValueInput input = TagValueInput.create(scopedCollector, level.registryAccess(), root);
			return EntityType.loadEntitiesRecursive(input.childrenListOrEmpty("Entities"), level, EntitySpawnReason.LOAD).toList();
		}
	}

	private static List<Entity> loadRust(ServerLevel level, List<CompoundTag> entityTags) throws IOException {
		List<byte[]> tapes = entityTags.stream().map(tag -> {
			try {
				return NativeNbtRegionAccess.writeTape(tag);
			} catch (IOException exception) {
				throw new RuntimeException(exception);
			}
		}).toList();
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
			ValueInput.ValueInputList input = NativeEntityValueInput.createList(scopedCollector, level.registryAccess(), tapes);
			return EntityType.loadEntitiesRecursive(input, level, EntitySpawnReason.LOAD).toList();
		} catch (RuntimeException exception) {
			if (exception.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			throw exception;
		}
	}

	private static String compareEntityLists(List<Entity> javaEntities, List<Entity> rustEntities) throws IOException {
		if (javaEntities.size() != rustEntities.size()) {
			return "entity count mismatch: java=" + javaEntities.size() + " rust=" + rustEntities.size();
		}
		for (int i = 0; i < javaEntities.size(); i++) {
			GeneratedSnapshot javaSnapshot = GeneratedSnapshot.create(javaEntities.get(i));
			GeneratedSnapshot rustSnapshot = GeneratedSnapshot.create(rustEntities.get(i));
			if (!javaSnapshot.equals(rustSnapshot)) {
				return "entity " + i + " mismatch: java=" + javaSnapshot + " rust=" + rustSnapshot;
			}
		}
		return null;
	}

	private static CompoundTag validEntity(String id, CompoundTag... passengers) {
		CompoundTag tag = entityWithId(id);
		tag.putIntArray("UUID", UUIDUtil.uuidToIntArray(java.util.UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		tag.put("Pos", doubleList(0.5, 64.0, 0.5));
		tag.put("Rotation", floatList(0.0F, 0.0F));
		if (passengers.length > 0) {
			ListTag list = new ListTag();
			for (CompoundTag passenger : passengers) {
				list.add(passenger);
			}
			tag.put("Passengers", list);
		}
		return tag;
	}

	private static CompoundTag entityWithId(String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		return tag;
	}

	private static CompoundTag entityWithMalformedId() {
		CompoundTag tag = new CompoundTag();
		tag.put("id", IntTag.valueOf(7));
		return tag;
	}

	private static ListTag doubleList(double... values) {
		ListTag list = new ListTag();
		for (double value : values) {
			list.add(DoubleTag.valueOf(value));
		}
		return list;
	}

	private static ListTag floatList(float... values) {
		ListTag list = new ListTag();
		for (float value : values) {
			list.add(FloatTag.valueOf(value));
		}
		return list;
	}

	private record GeneratedSnapshot(String type, String uuid, int passengers, long fingerprint) {
		static GeneratedSnapshot create(Entity entity) throws IOException {
			CompoundTag saved = saveEntity(entity);
			return new GeneratedSnapshot(
				EntityType.getKey(entity.getType()).toString(),
				entity.getUUID().toString(),
				entity.getPassengers().size(),
				NativeNbtRegionAccess.rawFingerprint(saved)
			);
		}

		private static CompoundTag saveEntity(Entity entity) throws IOException {
			try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
				TagValueOutput output = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
				if (!entity.saveAsPassenger(output)) {
					throw new IOException("Entity refused to save for validation: " + EntityType.getKey(entity.getType()));
				}
				return output.buildResult();
			}
		}
	}
}
