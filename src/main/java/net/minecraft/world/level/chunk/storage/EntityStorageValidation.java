package net.minecraft.world.level.chunk.storage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Dev-only parity and shadow helpers for Rust entity storage.
 *
 * <p>Production entity storage owns loading/saving policy in {@link EntityStorage};
 * this class owns the optional Java/Rust comparisons and fingerprint work.
 */
final class EntityStorageValidation {
	private EntityStorageValidation() {
	}

	static CompletableFuture<Optional<CompoundTag>> readJavaBaselineIfEnabled(SimpleRegionStorage storage, ChunkPos chunkPos) {
		return EntityReadDiagnostics.validationEnabled() ? storage.read(chunkPos) : CompletableFuture.completedFuture(Optional.empty());
	}

	static void validateNativeParity(
		EntityStorage storage,
		ChunkPos chunkPos,
		Optional<CompoundTag> javaOptional,
		ChunkEntities<Entity> rustEntities,
		long javaReadNanos
	) throws IOException {
		long javaLoadStarted = EntityReadDiagnostics.now();
		if (javaOptional.isEmpty()) {
			EntityReadDiagnostics.parityMismatch(
				chunkPos,
				"Java path returned no entity chunk while Rust returned entities",
				javaReadNanos,
				EntityReadDiagnostics.elapsed(javaLoadStarted),
				0L
			);
			throw new IOException("Rust entity read parity mismatch for " + chunkPos + ": Java path returned no entity chunk");
		}
		ChunkEntities<Entity> javaEntities = storage.loadEntitiesFromTag(chunkPos, javaOptional.orElseThrow());
		long javaLoadNanos = EntityReadDiagnostics.elapsed(javaLoadStarted);
		List<Entity> javaList = javaEntities.getEntities().toList();
		List<Entity> rustList = rustEntities.getEntities().toList();
		long validationStarted = EntityReadDiagnostics.now();
		String mismatch = compareEntityLists(javaList, rustList);
		long validationNanos = EntityReadDiagnostics.elapsed(validationStarted);
		if (mismatch == null) {
			EntityReadDiagnostics.parityMatch(chunkPos, javaList.size(), rustList.size(), javaReadNanos, javaLoadNanos, validationNanos);
			return;
		}
		EntityReadDiagnostics.parityMismatch(chunkPos, mismatch, javaReadNanos, javaLoadNanos, validationNanos);
		throw new IOException("Rust entity read parity mismatch for " + chunkPos + ": " + mismatch);
	}

	static long validateWriteShadow(EntityStorage storage, ChunkEntities<Entity> chunkEntities, CompoundTag pendingTag) throws IOException {
		if (!EntityReadDiagnostics.writeShadowEnabled()) {
			return 0L;
		}
		ChunkPos chunkPos = chunkEntities.getPos();
		long shadowStarted = EntityReadDiagnostics.now();
		CompoundTag javaTag;
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkPos), EntityStorage.LOGGER)) {
			javaTag = storage.buildJavaEntityChunkTag(chunkEntities, scopedCollector);
		}
		long javaFingerprint = fingerprint(javaTag);
		long rustFingerprint = fingerprint(pendingTag);
		long shadowValidationNanos = EntityReadDiagnostics.elapsed(shadowStarted);
		if (javaFingerprint != rustFingerprint) {
			String message = "Java/Rust entity write shadow mismatch: java="
				+ Long.toUnsignedString(javaFingerprint)
				+ " rust="
				+ Long.toUnsignedString(rustFingerprint);
			EntityReadDiagnostics.writeShadowMismatch(chunkPos, message, shadowValidationNanos);
			throw new IOException(message);
		}
		EntityReadDiagnostics.writeShadowMatch(chunkPos, shadowValidationNanos);
		return shadowValidationNanos;
	}

	private static String compareEntityLists(List<Entity> javaEntities, List<Entity> rustEntities) throws IOException {
		if (javaEntities.size() != rustEntities.size()) {
			return "entity count mismatch: java=" + javaEntities.size() + " rust=" + rustEntities.size();
		}
		for (int i = 0; i < javaEntities.size(); i++) {
			EntitySnapshot javaSnapshot = EntitySnapshot.create(javaEntities.get(i));
			EntitySnapshot rustSnapshot = EntitySnapshot.create(rustEntities.get(i));
			if (!javaSnapshot.equals(rustSnapshot)) {
				return "entity " + i + " mismatch: java=" + javaSnapshot + " rust=" + rustSnapshot;
			}
		}
		return null;
	}

	private static long fingerprint(CompoundTag tag) throws IOException {
		return NativeNbtRegionAccess.rawFingerprint(tag);
	}

	private record EntitySnapshot(
		String type,
		String uuid,
		double x,
		double y,
		double z,
		float yRot,
		float xRot,
		int directPassengers,
		int totalPassengers,
		long savedFingerprint
	) {
		static EntitySnapshot create(Entity entity) throws IOException {
			CompoundTag saved = saveEntity(entity);
			return new EntitySnapshot(
				EntityType.getKey(entity.getType()).toString(),
				entity.getUUID().toString(),
				entity.getX(),
				entity.getY(),
				entity.getZ(),
				entity.getYRot(),
				entity.getXRot(),
				entity.getPassengers().size(),
				(int)entity.getPassengersAndSelf().count() - 1,
				fingerprint(saved)
			);
		}

		private static CompoundTag saveEntity(Entity entity) throws IOException {
			try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), EntityStorage.LOGGER)) {
				TagValueOutput output = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
				if (!entity.saveAsPassenger(output)) {
					throw new IOException("Entity refused to save for parity comparison: " + EntityType.getKey(entity.getType()));
				}
				return output.buildResult();
			}
		}
	}
}
