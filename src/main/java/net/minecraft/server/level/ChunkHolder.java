package net.minecraft.server.level;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;

public class ChunkHolder extends GenerationChunkHolder {
	public static final ChunkResult<LevelChunk> UNLOADED_LEVEL_CHUNK = ChunkResult.error("Unloaded level chunk");
	private static final CompletableFuture<ChunkResult<LevelChunk>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_LEVEL_CHUNK);
	private final LevelHeightAccessor levelHeightAccessor;
	private volatile CompletableFuture<ChunkResult<LevelChunk>> fullChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
	private volatile CompletableFuture<ChunkResult<LevelChunk>> tickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
	private volatile CompletableFuture<ChunkResult<LevelChunk>> entityTickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
	/** The native registry owning this holder's level state while it holds the holder's id;
	 * null for a standalone holder, or once the unload completes and the state moves here. */
	@Nullable
	private NativeChunkHolders registry;
	private final int registryId;
	/** A standalone holder's state; a registry holder's is native. */
	private int oldTicketLevel;
	/** The level; for a registry holder, a mirror of the native level, written where the original sets it. */
	private int ticketLevel;
	private int queueLevel;
	private boolean hasChangedSections;
	private final ShortSet[] changedBlocksPerSection;
	private final BitSet blockChangedLightSectionFilter = new BitSet();
	private final BitSet skyChangedLightSectionFilter = new BitSet();
	private final LevelLightEngine lightEngine;
	private final ChunkHolder.LevelChangeListener onLevelChange;
	private final ChunkHolder.PlayerProvider playerProvider;
	/** A standalone holder's state; a registry holder's is native. */
	private boolean wasAccessibleSinceLastSave;
	private CompletableFuture<?> pendingFullStateConfirmation = CompletableFuture.completedFuture(null);
	private CompletableFuture<?> sendSync = CompletableFuture.completedFuture(null);
	private CompletableFuture<?> saveSync = CompletableFuture.completedFuture(null);

	public ChunkHolder(
		ChunkPos chunkPos,
		int i,
		LevelHeightAccessor levelHeightAccessor,
		LevelLightEngine levelLightEngine,
		ChunkHolder.LevelChangeListener levelChangeListener,
		ChunkHolder.PlayerProvider playerProvider
	) {
		this(chunkPos, i, levelHeightAccessor, levelLightEngine, levelChangeListener, playerProvider, null, -1);
	}

	/** A holder whose level state the registry already holds for {@code registryId}. */
	ChunkHolder(
		ChunkPos chunkPos,
		int i,
		LevelHeightAccessor levelHeightAccessor,
		LevelLightEngine levelLightEngine,
		ChunkHolder.LevelChangeListener levelChangeListener,
		ChunkHolder.PlayerProvider playerProvider,
		@Nullable NativeChunkHolders registry,
		int registryId
	) {
		super(chunkPos);
		this.registry = registry;
		this.registryId = registryId;
		this.levelHeightAccessor = levelHeightAccessor;
		this.lightEngine = levelLightEngine;
		this.onLevelChange = levelChangeListener;
		this.playerProvider = playerProvider;
		this.oldTicketLevel = ChunkLevel.MAX_LEVEL + 1;
		this.ticketLevel = this.oldTicketLevel;
		this.queueLevel = this.oldTicketLevel;
		if (registry == null) {
			this.setTicketLevel(i);
		} else {
			// The registry set this level and the unloaded old level when it allocated the id.
			this.ticketLevel = i;
		}
		this.changedBlocksPerSection = new ShortSet[levelHeightAccessor.getSectionsCount()];
	}

	public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
		return this.tickingChunkFuture;
	}

	public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
		return this.entityTickingChunkFuture;
	}

	public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() {
		return this.fullChunkFuture;
	}

	@Nullable
	public LevelChunk getTickingChunk() {
		return (LevelChunk)((ChunkResult)this.getTickingChunkFuture().getNow(UNLOADED_LEVEL_CHUNK)).orElse(null);
	}

	@Nullable
	public LevelChunk getChunkToSend() {
		return !this.sendSync.isDone() ? null : this.getTickingChunk();
	}

	public CompletableFuture<?> getSendSyncFuture() {
		return this.sendSync;
	}

	public void addSendDependency(CompletableFuture<?> completableFuture) {
		if (this.sendSync.isDone()) {
			this.sendSync = completableFuture;
		} else {
			this.sendSync = this.sendSync.thenCombine(completableFuture, (object, object2) -> null);
		}
	}

	public CompletableFuture<?> getSaveSyncFuture() {
		return this.saveSync;
	}

	public boolean isReadyForSaving() {
		return this.saveSync.isDone();
	}

	@Override
	protected void addSaveDependency(CompletableFuture<?> completableFuture) {
		if (this.saveSync.isDone()) {
			this.saveSync = completableFuture;
		} else {
			this.saveSync = this.saveSync.thenCombine(completableFuture, (object, object2) -> null);
		}
	}

	public boolean blockChanged(BlockPos blockPos) {
		LevelChunk levelChunk = this.getTickingChunk();
		if (levelChunk == null) {
			return false;
		} else {
			boolean bl = this.hasChangedSections;
			int i = this.levelHeightAccessor.getSectionIndex(blockPos.getY());
			if (this.changedBlocksPerSection[i] == null) {
				this.hasChangedSections = true;
				this.changedBlocksPerSection[i] = new ShortOpenHashSet();
			}

			this.changedBlocksPerSection[i].add(SectionPos.sectionRelativePos(blockPos));
			return !bl;
		}
	}

	public boolean sectionLightChanged(LightLayer lightLayer, int i) {
		ChunkAccess chunkAccess = this.getChunkIfPresent(ChunkStatus.INITIALIZE_LIGHT);
		if (chunkAccess == null) {
			return false;
		} else {
			chunkAccess.markUnsaved();
			LevelChunk levelChunk = this.getTickingChunk();
			if (levelChunk == null) {
				return false;
			} else {
				int j = this.lightEngine.getMinLightSection();
				int k = this.lightEngine.getMaxLightSection();
				if (i >= j && i <= k) {
					BitSet bitSet = lightLayer == LightLayer.SKY ? this.skyChangedLightSectionFilter : this.blockChangedLightSectionFilter;
					int l = i - j;
					if (!bitSet.get(l)) {
						bitSet.set(l);
						return true;
					} else {
						return false;
					}
				} else {
					return false;
				}
			}
		}
	}

	public boolean hasChangesToBroadcast() {
		return this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty();
	}

	public void broadcastChanges(LevelChunk levelChunk) {
		if (this.hasChangesToBroadcast()) {
			Level level = levelChunk.getLevel();
			if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
				List<ServerPlayer> list = this.playerProvider.getPlayers(this.pos, true);
				if (!list.isEmpty()) {
					ClientboundLightUpdatePacket clientboundLightUpdatePacket = new ClientboundLightUpdatePacket(
						levelChunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter
					);
					this.broadcast(list, clientboundLightUpdatePacket);
				}

				this.skyChangedLightSectionFilter.clear();
				this.blockChangedLightSectionFilter.clear();
			}

			if (this.hasChangedSections) {
				List<ServerPlayer> list = this.playerProvider.getPlayers(this.pos, false);

				for (int i = 0; i < this.changedBlocksPerSection.length; i++) {
					ShortSet shortSet = this.changedBlocksPerSection[i];
					if (shortSet != null) {
						this.changedBlocksPerSection[i] = null;
						if (!list.isEmpty()) {
							int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
							SectionPos sectionPos = SectionPos.of(levelChunk.getPos(), j);
							if (shortSet.size() == 1) {
								BlockPos blockPos = sectionPos.relativeToBlockPos(shortSet.iterator().nextShort());
								BlockState blockState = level.getBlockState(blockPos);
								this.broadcast(list, new ClientboundBlockUpdatePacket(blockPos, blockState));
								this.broadcastBlockEntityIfNeeded(list, level, blockPos, blockState);
							} else {
								LevelChunkSection levelChunkSection = levelChunk.getSection(i);
								ClientboundSectionBlocksUpdatePacket clientboundSectionBlocksUpdatePacket = new ClientboundSectionBlocksUpdatePacket(
									sectionPos, shortSet, levelChunkSection
								);
								this.broadcast(list, clientboundSectionBlocksUpdatePacket);
								clientboundSectionBlocksUpdatePacket.runUpdates((blockPos, blockState) -> this.broadcastBlockEntityIfNeeded(list, level, blockPos, blockState));
							}
						}
					}
				}

				this.hasChangedSections = false;
			}
		}
	}

	private void broadcastBlockEntityIfNeeded(List<ServerPlayer> list, Level level, BlockPos blockPos, BlockState blockState) {
		if (blockState.hasBlockEntity()) {
			this.broadcastBlockEntity(list, level, blockPos);
		}
	}

	private void broadcastBlockEntity(List<ServerPlayer> list, Level level, BlockPos blockPos) {
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		if (blockEntity != null) {
			Packet<?> packet = blockEntity.getUpdatePacket();
			if (packet != null) {
				this.broadcast(list, packet);
			}
		}
	}

	private void broadcast(List<ServerPlayer> list, Packet<?> packet) {
		list.forEach(serverPlayer -> serverPlayer.connection.send(packet));
	}

	@Override
	public int getTicketLevel() {
		return this.ticketLevel;
	}

	@Override
	public int getQueueLevel() {
		return this.queueLevel;
	}

	private void setQueueLevel(int i) {
		this.queueLevel = i;
	}

	public void setTicketLevel(int i) {
		if (this.registry != null) {
			this.registry.holderSet(this.registryId, 0, i);
		}

		this.ticketLevel = i;
	}

	/**
	 * The registry is freeing this holder's id (its unload completed): its state moves
	 * to these fields, since a reference may outlive the unload (for example in a
	 * pending set a throwing pass kept) and the id may be reused by another holder.
	 */
	void detach(long levels) {
		this.ticketLevel = (int)(levels & 0xFFFF);
		this.oldTicketLevel = (int)(levels >>> 16 & 0xFFFF);
		this.wasAccessibleSinceLastSave = (levels >>> 32 & 1) != 0;
		this.registry = null;
	}

	/** The registry already holds level {@code i}: update the mirror. */
	void mirrorTicketLevel(int i) {
		this.ticketLevel = i;
	}

	private void scheduleFullChunkPromotion(
		ChunkMap chunkMap, CompletableFuture<ChunkResult<LevelChunk>> completableFuture, Executor executor, FullChunkStatus fullChunkStatus
	) {
		this.pendingFullStateConfirmation.cancel(false);
		CompletableFuture<Void> completableFuture2 = new CompletableFuture();
		completableFuture2.thenRunAsync(() -> chunkMap.onFullChunkStatusChange(this.pos, fullChunkStatus), executor);
		this.pendingFullStateConfirmation = completableFuture2;
		completableFuture.thenAccept(chunkResult -> chunkResult.ifSuccess(levelChunk -> completableFuture2.complete(null)));
	}

	private void demoteFullChunk(ChunkMap chunkMap, FullChunkStatus fullChunkStatus) {
		this.pendingFullStateConfirmation.cancel(false);
		chunkMap.onFullChunkStatusChange(this.pos, fullChunkStatus);
	}

	/**
	 * Rust decides the transition from the old and current levels (and marks the holder
	 * accessible); this executes its effects in the original order. The old level is
	 * committed only after the effects and the listener, so a throwing effect leaves the
	 * transition to be decided again.
	 */
	protected void updateFutures(ChunkMap chunkMap, Executor executor) {
		long effects;
		if (this.registry != null) {
			effects = this.registry.beginTransition(this.registryId);
		} else {
			effects = NativeChunkHolders.transition(this.oldTicketLevel, this.ticketLevel);
			this.wasAccessibleSinceLastSave = this.wasAccessibleSinceLastSave | ChunkLevel.fullStatus(this.ticketLevel).isOrAfter(FullChunkStatus.FULL);
		}

		if ((effects & NativeChunkHolders.PREPARE_FULL) != 0) {
			this.fullChunkFuture = chunkMap.prepareAccessibleChunk(this);
			this.scheduleFullChunkPromotion(chunkMap, this.fullChunkFuture, executor, FullChunkStatus.FULL);
			this.addSaveDependency(this.fullChunkFuture);
		}

		if ((effects & NativeChunkHolders.UNLOAD_FULL) != 0) {
			this.fullChunkFuture.complete(UNLOADED_LEVEL_CHUNK);
			this.fullChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
		}

		if ((effects & NativeChunkHolders.PREPARE_TICKING) != 0) {
			this.tickingChunkFuture = chunkMap.prepareTickingChunk(this);
			this.scheduleFullChunkPromotion(chunkMap, this.tickingChunkFuture, executor, FullChunkStatus.BLOCK_TICKING);
			this.addSaveDependency(this.tickingChunkFuture);
		}

		if ((effects & NativeChunkHolders.UNLOAD_TICKING) != 0) {
			this.tickingChunkFuture.complete(UNLOADED_LEVEL_CHUNK);
			this.tickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
		}

		if ((effects & NativeChunkHolders.PREPARE_ENTITY) != 0) {
			if (this.entityTickingChunkFuture != UNLOADED_LEVEL_CHUNK_FUTURE) {
				throw (IllegalStateException)Util.pauseInIde(new IllegalStateException());
			}

			this.entityTickingChunkFuture = chunkMap.prepareEntityTickingChunk(this);
			this.scheduleFullChunkPromotion(chunkMap, this.entityTickingChunkFuture, executor, FullChunkStatus.ENTITY_TICKING);
			this.addSaveDependency(this.entityTickingChunkFuture);
		}

		if ((effects & NativeChunkHolders.UNLOAD_ENTITY) != 0) {
			this.entityTickingChunkFuture.complete(UNLOADED_LEVEL_CHUNK);
			this.entityTickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
		}

		FullChunkStatus demotion = NativeChunkHolders.demotion(effects);
		if (demotion != null) {
			this.demoteFullChunk(chunkMap, demotion);
		}

		this.onLevelChange.onLevelChange(this.pos, this::getQueueLevel, this.ticketLevel, this::setQueueLevel);
		if (this.registry != null) {
			this.registry.holderSet(this.registryId, 1, 0);
		} else {
			this.oldTicketLevel = this.ticketLevel;
		}
	}

	public boolean wasAccessibleSinceLastSave() {
		return this.registry != null ? (this.registry.holderLevels(this.registryId) >>> 32 & 1) != 0 : this.wasAccessibleSinceLastSave;
	}

	public void refreshAccessibility() {
		if (this.registry != null) {
			this.registry.holderSet(this.registryId, 2, 0);
		} else {
			this.wasAccessibleSinceLastSave = ChunkLevel.fullStatus(this.ticketLevel).isOrAfter(FullChunkStatus.FULL);
		}
	}

	@FunctionalInterface
	public interface LevelChangeListener {
		void onLevelChange(ChunkPos chunkPos, IntSupplier intSupplier, int i, IntConsumer intConsumer);
	}

	public interface PlayerProvider {
		List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean bl);
	}
}
