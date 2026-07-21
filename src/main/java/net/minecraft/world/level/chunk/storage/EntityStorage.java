package net.minecraft.world.level.chunk.storage;

import net.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.storage.NativeEntityValueInput;
import net.minecraft.world.level.storage.NativeEntityValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.slf4j.Logger;

/**
 * Entity storage keeps entity policy in Java while current-version entity chunk
 * bytes are owned by the Rust storage stack. Java still constructs, loads, and
 * saves Entity objects through the registry-aware ValueInput/ValueOutput APIs;
 * Rust owns the current-version entity-chunk envelope, NBT encoding,
 * compression, and region publication. Old-version reads remain Java/DFU-owned,
 * and failed native-tape construction can fall back before any write is
 * published.
 */
public class EntityStorage implements EntityPersistentStorage<Entity> {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final String ENTITIES_TAG = "Entities";
	private static final String POSITION_TAG = "Position";
	private final ServerLevel level;
	private final SimpleRegionStorage simpleRegionStorage;
	private final LongSet emptyChunks = new LongOpenHashSet();
	private final ConsecutiveExecutor entityDeserializerQueue;

	public EntityStorage(SimpleRegionStorage simpleRegionStorage, ServerLevel serverLevel, Executor executor) {
		this.simpleRegionStorage = simpleRegionStorage;
		this.level = serverLevel;
		this.entityDeserializerQueue = new ConsecutiveExecutor(executor, "entity-deserializer");
	}

	@Override
	public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos chunkPos) {
		if (this.emptyChunks.contains(chunkPos.toLong())) {
			return CompletableFuture.completedFuture(emptyChunk(chunkPos));
		} else {
			return this.loadEntitiesWithRustPath(chunkPos);
		}
	}

	private static ChunkEntities<Entity> emptyChunk(ChunkPos chunkPos) {
		return new ChunkEntities<>(chunkPos, List.of());
	}

	private CompletableFuture<ChunkEntities<Entity>> loadEntitiesWithJavaPath(ChunkPos chunkPos) {
		CompletableFuture<Optional<CompoundTag>> completableFuture = this.simpleRegionStorage.read(chunkPos);
		this.reportLoadFailureIfPresent(completableFuture, chunkPos);
		return completableFuture.thenApplyAsync(optional -> this.loadEntitiesFromOptionalTag(chunkPos, optional), this.entityDeserializerQueue::schedule);
	}

	private CompletableFuture<ChunkEntities<Entity>> loadEntitiesWithRustPath(ChunkPos chunkPos) {
		long nativeStarted = EntityReadDiagnostics.now();
		CompletableFuture<Optional<NativeEntityStorage.DecodeResult>> nativeFuture = this.simpleRegionStorage.readEntityTape(chunkPos);
		this.reportLoadFailureIfPresent(nativeFuture, chunkPos);
		nativeFuture.exceptionally(throwable -> {
			EntityReadDiagnostics.nativeError(chunkPos, throwable);
			return Optional.empty();
		});
		return nativeFuture.thenCompose(optionalResult -> {
			long nativeNanos = EntityReadDiagnostics.elapsed(nativeStarted);
			if (optionalResult.isEmpty()) {
				EntityReadDiagnostics.pendingWriteFallback(chunkPos);
				return this.loadEntitiesWithJavaPath(chunkPos);
			}

			NativeEntityStorage.DecodeResult result = optionalResult.orElseThrow();
			if (!result.result().present()) {
				this.emptyChunks.add(chunkPos.toLong());
				EntityReadDiagnostics.absent(chunkPos);
				return CompletableFuture.completedFuture(emptyChunk(chunkPos));
			}
			if (result.result().requiresDfu()) {
				EntityReadDiagnostics.oldVersionFallback(chunkPos, result.result().dataVersion());
				return this.loadEntitiesWithJavaPath(chunkPos);
			}

			CompletableFuture<Optional<CompoundTag>> javaFuture = EntityStorageValidation.readJavaBaselineIfEnabled(this.simpleRegionStorage, chunkPos);
			long javaReadStarted = EntityReadDiagnostics.now();
			if (EntityReadDiagnostics.validationEnabled()) {
				this.reportLoadFailureIfPresent(javaFuture, chunkPos);
			}
			return javaFuture.thenApplyAsync(
				javaOptional -> {
					try {
						ChunkEntities<Entity> rustEntities = this.loadEntitiesFromNative(chunkPos, result, nativeNanos);
						if (EntityReadDiagnostics.validationEnabled()) {
							EntityStorageValidation.validateNativeParity(this, chunkPos, javaOptional, rustEntities, EntityReadDiagnostics.elapsed(javaReadStarted));
						}
						return rustEntities;
					} catch (IOException exception) {
						EntityReadDiagnostics.malformed(chunkPos, exception);
						throw new CompletionException(exception);
					}
				},
				this.entityDeserializerQueue::schedule
			);
		});
	}

	private ChunkEntities<Entity> loadEntitiesFromOptionalTag(ChunkPos chunkPos, Optional<CompoundTag> optional) {
		if (optional.isEmpty()) {
			this.emptyChunks.add(chunkPos.toLong());
			return emptyChunk(chunkPos);
		}
		return this.loadEntitiesFromTag(chunkPos, optional.orElseThrow());
	}

	ChunkEntities<Entity> loadEntitiesFromTag(ChunkPos chunkPos, CompoundTag compoundTag) {
		this.reportStoredPosition(chunkPos, compoundTag);
		CompoundTag upgraded = this.simpleRegionStorage.upgradeChunkTag(compoundTag, -1);

		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkPos), LOGGER)) {
			ValueInput valueInput = TagValueInput.create(scopedCollector, this.level.registryAccess(), upgraded);
			return this.loadEntitiesFromInputList(chunkPos, valueInput.childrenListOrEmpty(ENTITIES_TAG));
		}
	}

	private ChunkEntities<Entity> loadEntitiesFromNative(ChunkPos chunkPos, NativeEntityStorage.DecodeResult result, long nativeNanos) throws IOException {
		this.reportStoredPosition(chunkPos, result);
		long constructStarted = EntityReadDiagnostics.now();
		ChunkEntities<Entity> entities;
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkPos), LOGGER)) {
			ValueInput.ValueInputList valueInputList = NativeEntityValueInput.createListFromSlices(
				scopedCollector,
				this.level.registryAccess(),
				result.entityTapeSlices()
			);
			entities = this.loadEntitiesFromInputList(chunkPos, valueInputList);
		}
		EntityReadDiagnostics.rustDecoded(chunkPos, result, (int)entities.getEntities().count(), nativeNanos, EntityReadDiagnostics.elapsed(constructStarted));
		return entities;
	}

	private ChunkEntities<Entity> loadEntitiesFromInputList(ChunkPos chunkPos, ValueInput.ValueInputList valueInputList) {
		List<Entity> list = EntityType.loadEntitiesRecursive(valueInputList, this.level, EntitySpawnReason.LOAD).toList();
		return new ChunkEntities<>(chunkPos, list);
	}

	private void reportStoredPosition(ChunkPos chunkPos, CompoundTag compoundTag) {
		try {
			ChunkPos stored = compoundTag.read(POSITION_TAG, ChunkPos.CODEC).orElseThrow();
			if (!Objects.equals(chunkPos, stored)) {
				LOGGER.error("Chunk file at {} is in the wrong location. (Expected {}, got {})", chunkPos, chunkPos, stored);
				this.level.getServer().reportMisplacedChunk(stored, chunkPos, this.simpleRegionStorage.storageInfo());
			}
		} catch (Exception exception) {
			LOGGER.warn("Failed to parse chunk {} position info", chunkPos, exception);
			this.level.getServer().reportChunkLoadFailure(exception, this.simpleRegionStorage.storageInfo(), chunkPos);
		}
	}

	private void reportStoredPosition(ChunkPos chunkPos, NativeEntityStorage.DecodeResult result) {
		if (result.result().chunkX() == chunkPos.x && result.result().chunkZ() == chunkPos.z) {
			return;
		}
		ChunkPos stored = new ChunkPos(result.result().chunkX(), result.result().chunkZ());
		LOGGER.error("Chunk file at {} is in the wrong location. (Expected {}, got {})", chunkPos, chunkPos, stored);
		this.level.getServer().reportMisplacedChunk(stored, chunkPos, this.simpleRegionStorage.storageInfo());
	}

	@Override
	public void storeEntities(ChunkEntities<Entity> chunkEntities) {
		ChunkPos chunkPos = chunkEntities.getPos();
		if (chunkEntities.isEmpty()) {
			if (this.emptyChunks.add(chunkPos.toLong())) {
				this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkPos, null), chunkPos);
			}
		} else {
			NativeEntityStorage.WriteRequest request;
			try {
				request = this.createNativeEntityWriteRequest(chunkEntities);
			} catch (Exception exception) {
				EntityReadDiagnostics.writeFallback(chunkPos, exception.getMessage());
				LOGGER.warn("Falling back to Java entity chunk write for {} because native tape construction failed", chunkPos, exception);
				this.storeEntitiesWithJavaPath(chunkEntities);
				return;
			}
			this.reportSaveFailureIfPresent(this.simpleRegionStorage.writeEntityTapes(chunkPos, request), chunkPos);
			this.emptyChunks.remove(chunkPos.toLong());
		}
	}

	private void storeEntitiesWithJavaPath(ChunkEntities<Entity> chunkEntities) {
		ChunkPos chunkPos = chunkEntities.getPos();
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkPos), LOGGER)) {
			CompoundTag compoundTag = this.buildJavaEntityChunkTag(chunkEntities, scopedCollector);
			this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkPos, compoundTag), chunkPos);
			this.emptyChunks.remove(chunkPos.toLong());
		}
	}

	private NativeEntityStorage.WriteRequest createNativeEntityWriteRequest(ChunkEntities<Entity> chunkEntities) throws IOException {
		ChunkPos chunkPos = chunkEntities.getPos();
		List<byte[]> tapes = new java.util.ArrayList<>();
		List<CompoundTag> debugTags = new java.util.ArrayList<>();
		long saveTraversalNanos = 0L;
		long tapeConstructionNanos = 0L;
		long codecSubtreeNanos = 0L;
		long codecSubtreeMaterializations = 0L;
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkPos), LOGGER)) {
			for (Entity entity : chunkEntities.getEntities().toList()) {
				NativeEntityValueOutput output = NativeEntityValueOutput.createWithContext(
					scopedCollector.forChild(entity.problemPath()),
					entity.registryAccess()
				);
				long saveStarted = EntityReadDiagnostics.now();
				boolean saved = entity.save(output);
				saveTraversalNanos += EntityReadDiagnostics.elapsed(saveStarted);
				codecSubtreeNanos += output.codecSubtreeNanos();
				codecSubtreeMaterializations += output.codecSubtreeMaterializations();
				if (saved) {
					long tapeStarted = EntityReadDiagnostics.now();
					tapes.add(output.buildTape());
					tapeConstructionNanos += EntityReadDiagnostics.elapsed(tapeStarted);
					debugTags.add(output.buildDebugTag());
				}
			}
		}

		CompoundTag pendingTag = this.entityRoot(chunkPos, debugTags);
		long shadowValidationNanos = EntityStorageValidation.validateWriteShadow(this, chunkEntities, pendingTag);

		return new NativeEntityStorage.WriteRequest(
			tapes,
			pendingTag,
			tapes.size(),
			saveTraversalNanos,
			tapeConstructionNanos,
			codecSubtreeNanos,
			codecSubtreeMaterializations,
			shadowValidationNanos
		);
	}

	CompoundTag buildJavaEntityChunkTag(ChunkEntities<Entity> chunkEntities, ProblemReporter.ScopedCollector scopedCollector) {
		List<CompoundTag> entities = new java.util.ArrayList<>();
		chunkEntities.getEntities().forEach(entity -> {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector.forChild(entity.problemPath()), entity.registryAccess());
			if (entity.save(tagValueOutput)) {
				entities.add(tagValueOutput.buildResult());
			}
		});
		return this.entityRoot(chunkEntities.getPos(), entities);
	}

	private CompoundTag entityRoot(ChunkPos chunkPos, List<CompoundTag> entities) {
		CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
		ListTag listTag = new ListTag();
		entities.forEach(listTag::add);
		compoundTag.put("Entities", listTag);
		compoundTag.store("Position", ChunkPos.CODEC, chunkPos);
		return compoundTag;
	}

	private void reportSaveFailureIfPresent(CompletableFuture<?> completableFuture, ChunkPos chunkPos) {
		completableFuture.exceptionally(throwable -> {
			LOGGER.error("Failed to store entity chunk {}", chunkPos, throwable);
			this.level.getServer().reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), chunkPos);
			return null;
		});
	}

	private void reportLoadFailureIfPresent(CompletableFuture<?> completableFuture, ChunkPos chunkPos) {
		completableFuture.exceptionally(throwable -> {
			LOGGER.error("Failed to load entity chunk {}", chunkPos, throwable);
			this.level.getServer().reportChunkLoadFailure(throwable, this.simpleRegionStorage.storageInfo(), chunkPos);
			return null;
		});
	}

	@Override
	public void flush(boolean bl) {
		this.simpleRegionStorage.synchronize(bl).join();
		this.entityDeserializerQueue.runAll();
	}

	@Override
	public void close() throws IOException {
		this.simpleRegionStorage.close();
	}
}
