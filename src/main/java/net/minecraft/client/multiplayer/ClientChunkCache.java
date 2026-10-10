package net.minecraft.client.multiplayer;

import net.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.NativeBiomeWorld;
import net.minecraft.world.level.chunk.NativeClientResidency;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientChunkCache extends ChunkSource {
	static final Logger LOGGER = LogUtils.getLogger();
	private final LevelChunk emptyChunk;
	private final LevelLightEngine lightEngine;
	volatile ClientChunkCache.Storage storage;
	final ClientLevel level;
	private final NativeBiomeWorld nativeBiomes;
	private boolean readingBiomePacket;

	public ClientChunkCache(ClientLevel clientLevel, int i) {
		this.level = clientLevel;
		this.emptyChunk = new EmptyLevelChunk(clientLevel, new ChunkPos(0, 0), clientLevel.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
		this.lightEngine = new LevelLightEngine(this, true, clientLevel.dimensionType().hasSkyLight());
		this.storage = new ClientChunkCache.Storage(calculateStorageRange(i));
		this.nativeBiomes = clientLevel.getClass() == ClientLevel.class
			? NativeBiomeWorld.create(clientLevel.palettedContainerFactory(), clientLevel.getMinY(), clientLevel.getHeight()) : null;
		if (this.nativeBiomes != null) this.nativeBiomes.range(0, 0, this.storage.chunkRadius);
	}

	public net.minecraft.world.phys.Vec3 sampleNativeSky(net.minecraft.world.phys.Vec3 position) {
		return this.nativeBiomes == null ? null : this.nativeBiomes.sample(position);
	}

	public net.minecraft.world.phys.Vec3 sampleNativeFog(net.minecraft.world.phys.Vec3 position) {
		return this.nativeBiomes == null ? null : this.nativeBiomes.sampleFog(position);
	}

	private void publishNativeBiomes(LevelChunk chunk) {
		if (this.nativeBiomes == null || this.readingBiomePacket) return;
		var storage = this.storage;
		if (storage.getChunk(storage.getIndex(chunk.getPos().x, chunk.getPos().z)) != chunk) return;
		this.nativeBiomes.replace(chunk);
	}

	public LevelLightEngine getLightEngine() {
		return this.lightEngine;
	}

	private static boolean isValidChunk(@Nullable LevelChunk levelChunk, int i, int j) {
		if (levelChunk == null) {
			return false;
		} else {
			ChunkPos chunkPos = levelChunk.getPos();
			return chunkPos.x == i && chunkPos.z == j;
		}
	}

	public void drop(ChunkPos chunkPos) {
		if (this.storage.inRange(chunkPos.x, chunkPos.z)) {
			int i = this.storage.getIndex(chunkPos.x, chunkPos.z);
			LevelChunk levelChunk = this.storage.getChunk(i);
			if (isValidChunk(levelChunk, chunkPos.x, chunkPos.z)) {
				this.storage.drop(i, levelChunk);
			}
		}
	}

	@Nullable
	public LevelChunk getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl) {
		if (this.storage.inRange(i, j)) {
			LevelChunk levelChunk = this.storage.getChunk(this.storage.getIndex(i, j));
			if (isValidChunk(levelChunk, i, j)) {
				return levelChunk;
			}
		}

		return bl ? this.emptyChunk : null;
	}

	public BlockGetter getLevel() {
		return this.level;
	}

	public void replaceBiomes(int i, int j, FriendlyByteBuf friendlyByteBuf) {
		if (!this.storage.inRange(i, j)) {
			LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", i, j);
		} else {
			int k = this.storage.getIndex(i, j);
			LevelChunk levelChunk = (LevelChunk)this.storage.chunks.get(k);
			if (!isValidChunk(levelChunk, i, j)) {
				LOGGER.warn("Ignoring chunk since it's not present: {}, {}", i, j);
			} else {
				this.readingBiomePacket = true;
				try { levelChunk.replaceBiomes(friendlyByteBuf); }
				finally { this.readingBiomePacket = false; this.publishNativeBiomes(levelChunk); }
			}
		}
	}

	@Nullable
	public LevelChunk replaceWithPacketData(int i, int j, FriendlyByteBuf friendlyByteBuf, Map<Types, long[]> map, Consumer<BlockEntityTagOutput> consumer) {
		if (!this.storage.inRange(i, j)) {
			LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", i, j);
			return null;
		} else {
			int k = this.storage.getIndex(i, j);
			LevelChunk levelChunk = (LevelChunk)this.storage.chunks.get(k);
			ChunkPos chunkPos = new ChunkPos(i, j);
			if (!isValidChunk(levelChunk, i, j)) {
				levelChunk = new LevelChunk(this.level, chunkPos);
				this.readingBiomePacket = true;
				try { levelChunk.replaceWithPacketData(friendlyByteBuf, map, consumer); }
				finally { this.readingBiomePacket = false; this.publishNativeBiomes(levelChunk); }
				this.storage.replace(k, levelChunk);
				this.publishNativeBiomes(levelChunk);
				if (this.nativeBiomes != null) {
					LevelChunk installed = levelChunk;
					installed.setNativeSectionArrayEscapeListener(() -> this.publishNativeBiomes(installed));
					for (var section : installed.getSectionsForRead()) section.setNativeBiomeListener(() -> this.publishNativeBiomes(installed));
				}
			} else {
				this.readingBiomePacket = true;
				try { levelChunk.replaceWithPacketData(friendlyByteBuf, map, consumer); }
				finally { this.readingBiomePacket = false; this.publishNativeBiomes(levelChunk); }
				this.storage.refreshEmptySections(levelChunk);
			}

			this.level.onChunkLoaded(chunkPos);
			
			// Call registered hooks for chunk loaded
			for (net.minecraft.hooks.ClientLevelHooks hook : net.minecraft.hooks.HookRegistry.getClientLevelHooks()) {
				hook.onChunkLoaded(this.level, i, j);
			}
			
			return levelChunk;
		}
	}

	public void tick(BooleanSupplier booleanSupplier, boolean bl) {
	}

	public void updateViewCenter(int i, int j) {
		this.storage.center(i, j);
		if (this.nativeBiomes != null) this.nativeBiomes.range(i, j, this.storage.chunkRadius);
	}

	public void updateViewRadius(int i) {
		int j = this.storage.chunkRadius;
		int k = calculateStorageRange(i);
		if (j != k) {
			ClientChunkCache.Storage storage = new ClientChunkCache.Storage(k);
			storage.center(this.storage.centerX(), this.storage.centerZ());

			for (int l = 0; l < this.storage.chunks.length(); l++) {
				LevelChunk levelChunk = (LevelChunk)this.storage.chunks.get(l);
				if (levelChunk != null) {
					ChunkPos chunkPos = levelChunk.getPos();
					if (storage.inRange(chunkPos.x, chunkPos.z)) {
						storage.replace(storage.getIndex(chunkPos.x, chunkPos.z), levelChunk);
					}
				}
			}

			if (this.nativeBiomes != null) {
				for (int l = 0; l < this.storage.chunks.length(); l++) {
					var old = this.storage.chunks.get(l);
					if (old != null && !storage.inRange(old.getPos().x, old.getPos().z)) {
						this.nativeBiomes.remove(old.getPos().x, old.getPos().z);
						old.setNativeSectionArrayEscapeListener(null);
						for (var section : old.getSectionsForRead()) section.setNativeBiomeListener(null);
					}
				}
				this.nativeBiomes.range(storage.centerX(), storage.centerZ(), storage.chunkRadius);
			}
			this.storage = storage;
		}
	}

	private static int calculateStorageRange(int i) {
		return Math.max(2, i) + 3;
	}

	public String gatherStats() {
		return this.storage.chunks.length() + ", " + this.getLoadedChunksCount();
	}

	public int getLoadedChunksCount() {
		return this.storage.count();
	}

	/**
	 * Diagnostic identity for the packet-backed client chunk window. Counts alone
	 * cannot distinguish two equally sized windows while a benchmark camera is
	 * relocated and the server replaces their chunks in place.
	 */
	public long getLoadedChunkPositionFingerprint() {
		Storage storage = this.storage;
		long fingerprint = storage.fingerprint();
		fingerprint = mixChunkIdentity(fingerprint ^ Integer.toUnsignedLong(storage.count()));
		fingerprint ^= mixChunkIdentity(ChunkPos.asLong(storage.centerX(), storage.centerZ()));
		return fingerprint;
	}

	private static long mixChunkIdentity(long value) {
		value ^= value >>> 30;
		value *= 0xbf58476d1ce4e5b9L;
		value ^= value >>> 27;
		value *= 0x94d049bb133111ebL;
		return value ^ value >>> 31;
	}

	public void onLightUpdate(LightLayer lightLayer, SectionPos sectionPos) {
		Minecraft.getInstance().levelRenderer.setSectionDirty(sectionPos.x(), sectionPos.y(), sectionPos.z());
	}

	public LongOpenHashSet getLoadedEmptySections() {
		return this.storage.emptySections();
	}

	public void onSectionEmptinessChanged(int i, int j, int k, boolean bl) {
		this.storage.onSectionEmptinessChanged(i, j, k, bl);
	}

	@Environment(EnvType.CLIENT)
	final class Storage {
		final AtomicReferenceArray<LevelChunk> chunks;
		final NativeClientResidency nativeResidency;
		final LongOpenHashSet loadedEmptySections;
		final int chunkRadius;
		private final int viewRange;
		volatile int viewCenterX;
		volatile int viewCenterZ;
		int chunkCount;
		long loadedChunkPositionFingerprint;

		Storage(final int i) {
			this.chunkRadius = i;
			this.viewRange = i * 2 + 1;
			this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange);
            this.nativeResidency = NativeClientResidency.create(i);
            this.loadedEmptySections = this.nativeResidency == null ? new LongOpenHashSet() : null;
		}

        int count() { return this.nativeResidency == null ? this.chunkCount : this.nativeResidency.count(); }
        long fingerprint() { return this.nativeResidency == null ? this.loadedChunkPositionFingerprint : this.nativeResidency.fingerprint(); }
        int centerX() { return this.nativeResidency == null ? this.viewCenterX : this.nativeResidency.centerX(); }
        int centerZ() { return this.nativeResidency == null ? this.viewCenterZ : this.nativeResidency.centerZ(); }
        void center(int x, int z) {
            if (this.nativeResidency == null) { this.viewCenterX = x; this.viewCenterZ = z; }
            else this.nativeResidency.center(x, z);
        }
        LongOpenHashSet emptySections() {
            return this.nativeResidency == null ? this.loadedEmptySections : this.nativeResidency.emptySections();
        }
        boolean emptyChanged(long key, boolean empty) {
            return this.nativeResidency == null ? (empty ? this.loadedEmptySections.add(key) : this.loadedEmptySections.remove(key))
                : this.nativeResidency.empty(key, empty);
        }
        void positionChanged(ChunkPos pos) {
            if (this.nativeResidency == null) this.loadedChunkPositionFingerprint ^= mixChunkIdentity(ChunkPos.asLong(pos.x, pos.z));
            else this.nativeResidency.position(pos.x, pos.z);
        }

		int getIndex(int i, int j) {
			return Math.floorMod(j, this.viewRange) * this.viewRange + Math.floorMod(i, this.viewRange);
		}

		void replace(int i, @Nullable LevelChunk levelChunk) {
			LevelChunk levelChunk2 = this.chunks.getAndSet(i, levelChunk);
            long id = this.nativeResidency == null ? 0 : this.nativeResidency.publish(i, levelChunk != null);
			if (levelChunk2 != null) {
				if (this.nativeResidency == null) this.chunkCount--;
				ChunkPos oldPos = levelChunk2.getPos();
				levelChunk2.setNativeSectionArrayEscapeListener(null);
				if (ClientChunkCache.this.nativeBiomes != null) ClientChunkCache.this.nativeBiomes.remove(oldPos.x, oldPos.z);
				for (var section : levelChunk2.getSectionsForRead()) section.setNativeBiomeListener(null);
				this.positionChanged(oldPos);
				this.dropEmptySections(levelChunk2);
				ClientChunkCache.this.level.unload(levelChunk2);
			}

			if (levelChunk != null) {
				if (this.nativeResidency == null) this.chunkCount++;
                else this.nativeResidency.admitted();
				ChunkPos newPos = levelChunk.getPos();
				this.positionChanged(newPos);
				this.addEmptySections(levelChunk, i, id);
			}
		}

		void drop(int i, LevelChunk levelChunk) {
			if (this.chunks.compareAndSet(i, levelChunk, null)) {
                if (this.nativeResidency == null) this.chunkCount--;
                else this.nativeResidency.dropped(i);
				ChunkPos chunkPos = levelChunk.getPos();
				levelChunk.setNativeSectionArrayEscapeListener(null);
				if (ClientChunkCache.this.nativeBiomes != null) ClientChunkCache.this.nativeBiomes.remove(chunkPos.x, chunkPos.z);
				for (var section : levelChunk.getSectionsForRead()) section.setNativeBiomeListener(null);
				this.positionChanged(chunkPos);
				this.dropEmptySections(levelChunk);
			}

			ClientChunkCache.this.level.unload(levelChunk);
			
			// Call registered hooks for chunk drop
			for (net.minecraft.hooks.ClientLevelHooks hook : net.minecraft.hooks.HookRegistry.getClientLevelHooks()) {
				hook.onChunkDropped(ClientChunkCache.this.level, levelChunk.getPos().x, levelChunk.getPos().z);
			}
		}

		public void onSectionEmptinessChanged(int i, int j, int k, boolean bl) {
			if (this.inRange(i, k)) {
				long l = SectionPos.asLong(i, j, k);
				if (bl) {
					this.emptyChanged(l, true);
				} else if (this.emptyChanged(l, false)) {
					ClientChunkCache.this.level.onSectionBecomingNonEmpty(l);
				}
			}
		}

		private void dropEmptySections(LevelChunk levelChunk) {
            if (this.nativeResidency != null) { this.nativeResidency.dropEmpty(levelChunk); return; }
			LevelChunkSection[] levelChunkSections = levelChunk.getSectionsForRead();

			for (int i = 0; i < levelChunkSections.length; i++) {
				ChunkPos chunkPos = levelChunk.getPos();
				this.loadedEmptySections.remove(SectionPos.asLong(chunkPos.x, levelChunk.getSectionYFromSectionIndex(i), chunkPos.z));
			}
		}

		private void addEmptySections(LevelChunk levelChunk, int index, long id) {
            if (this.nativeResidency != null) { this.nativeResidency.addEmpty(levelChunk, index, id); return; }
			LevelChunkSection[] levelChunkSections = levelChunk.getSectionsForRead();

			for (int i = 0; i < levelChunkSections.length; i++) {
				LevelChunkSection levelChunkSection = levelChunkSections[i];
				if (levelChunkSection.hasOnlyAir()) {
					ChunkPos chunkPos = levelChunk.getPos();
					this.loadedEmptySections.add(SectionPos.asLong(chunkPos.x, levelChunk.getSectionYFromSectionIndex(i), chunkPos.z));
				}
			}
		}

		void refreshEmptySections(LevelChunk levelChunk) {
            if (this.nativeResidency != null) {
                int index = -1;
                if (levelChunk.getClass() == LevelChunk.class) {
                    ChunkPos pos = levelChunk.getPos();
                    index = this.getIndex(pos.x, pos.z);
                    if (this.chunks.get(index) != levelChunk) index = -1;
                }
                this.nativeResidency.refresh(levelChunk, index, this.nativeResidency.identity(index), ClientChunkCache.this.level::onSectionBecomingNonEmpty);
                return;
            }
			ChunkPos chunkPos = levelChunk.getPos();
			LevelChunkSection[] levelChunkSections = levelChunk.getSectionsForRead();

			for (int i = 0; i < levelChunkSections.length; i++) {
				LevelChunkSection levelChunkSection = levelChunkSections[i];
				long l = SectionPos.asLong(chunkPos.x, levelChunk.getSectionYFromSectionIndex(i), chunkPos.z);
				if (levelChunkSection.hasOnlyAir()) {
					this.emptyChanged(l, true);
				} else if (this.emptyChanged(l, false)) {
					ClientChunkCache.this.level.onSectionBecomingNonEmpty(l);
				}
			}
		}

		boolean inRange(int i, int j) {
			return Math.abs(i - this.centerX()) <= this.chunkRadius && Math.abs(j - this.centerZ()) <= this.chunkRadius;
		}

		@Nullable
		protected LevelChunk getChunk(int i) {
			return (LevelChunk)this.chunks.get(i);
		}

		private void dumpChunks(String string) {
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(string);

				try {
					int i = ClientChunkCache.this.storage.chunkRadius;

					for (int j = this.centerZ() - i; j <= this.centerZ() + i; j++) {
						for (int k = this.centerX() - i; k <= this.centerX() + i; k++) {
							LevelChunk levelChunk = (LevelChunk)ClientChunkCache.this.storage.chunks.get(ClientChunkCache.this.storage.getIndex(k, j));
							if (levelChunk != null) {
								ChunkPos chunkPos = levelChunk.getPos();
								fileOutputStream.write((chunkPos.x + "\t" + chunkPos.z + "\t" + levelChunk.isEmpty() + "\n").getBytes(StandardCharsets.UTF_8));
							}
						}
					}
				} catch (Throwable var9) {
					try {
						fileOutputStream.close();
					} catch (Throwable var8) {
						var9.addSuppressed(var8);
					}

					throw var9;
				}

				fileOutputStream.close();
			} catch (IOException var10) {
				ClientChunkCache.LOGGER.error("Failed to dump chunks to file {}", string, var10);
			}
		}
	}
}
