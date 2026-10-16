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
	private boolean hasChangedSections;
	private final ShortSet[] changedBlocksPerSection;
	private final BitSet blockChangedLightSectionFilter = new BitSet();
	private final BitSet skyChangedLightSectionFilter = new BitSet();
	private final LevelLightEngine lightEngine;
	private final ChunkHolder.LevelChangeListener onLevelChange;
	private final ChunkHolder.PlayerProvider playerProvider;

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
		this.levelHeightAccessor = levelHeightAccessor;
		this.lightEngine = levelLightEngine;
		this.onLevelChange = levelChangeListener;
		this.playerProvider = playerProvider;
        this.generationOwner().initializeLifecycle(registry == null ? ChunkLevel.MAX_LEVEL + 1 : i,
            ChunkLevel.MAX_LEVEL + 1, UNLOADED_LEVEL_CHUNK_FUTURE);
        if (registry == null) this.setTicketLevel(i);
		this.changedBlocksPerSection = new ShortSet[levelHeightAccessor.getSectionsCount()];
	}

	public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
		return this.readyFuture(1);
	}

	public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
		return this.readyFuture(2);
	}

	public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() {
		return this.readyFuture(0);
	}

	@Nullable
	public LevelChunk getTickingChunk() {
		return (LevelChunk)((ChunkResult)this.getTickingChunkFuture().getNow(UNLOADED_LEVEL_CHUNK)).orElse(null);
	}

	@Nullable
	public LevelChunk getChunkToSend() {
		return !this.sync(NativeChunkGeneration.Holder.READY_BASE + 4, "isDone()").isDone() ? null : this.getTickingChunk();
	}

	public CompletableFuture<?> getSendSyncFuture() {
		return this.generationOwner().binding(NativeChunkGeneration.Holder.READY_BASE + 4);
	}

    public void addSendDependency(CompletableFuture<?> completableFuture) {
        this.combineDependency(NativeChunkGeneration.Holder.READY_BASE + 4, 1, completableFuture);
    }

	public CompletableFuture<?> getSaveSyncFuture() {
		return this.generationOwner().binding(NativeChunkGeneration.Holder.READY_BASE + 5);
	}

	public boolean isReadyForSaving() {
		return this.sync(NativeChunkGeneration.Holder.READY_BASE + 5, "isDone()").isDone();
	}

	@Override
    protected void addSaveDependency(CompletableFuture<?> completableFuture) {
        this.combineDependency(NativeChunkGeneration.Holder.READY_BASE + 5, 2, completableFuture);
    }

    private void combineDependency(int slot, int operation, CompletableFuture<?> dependency) {
        NativeChunkGeneration.Holder owner = this.generationOwner();
        boolean done = this.sync(slot, "isDone()").isDone();
        long command = owner.dependencyCommand(operation, done);
        int target = (int)(command >>> 8);
        switch ((int)(command & 255)) {
            case 31: owner.binding(target, dependency); break;
            case 32: owner.binding(target, this.sync(target,
                "thenCombine(java.util.concurrent.CompletionStage, java.util.function.BiFunction)").thenCombine(dependency, (a, b) -> null)); break;
            default: throw new IllegalStateException("Unknown native dependency command");
        }
    }
    private CompletableFuture<ChunkResult<LevelChunk>> readyFuture(int index) {
        return this.generationOwner().binding(NativeChunkGeneration.Holder.READY_BASE + index);
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
    public int getTicketLevel() { return this.generationOwner().level(0); }
    @Override
    public int getQueueLevel() { return this.generationOwner().level(2); }
    private void setQueueLevel(int value) { this.generationOwner().levelChange(1, value); }
    public void setTicketLevel(int value) { this.generationOwner().levelChange(0, value); }

    /** Detach affects registry membership only: the same incarnation retains all state. */
    void detach(long unused) {}
    /** Loading already changed the same native incarnation; no Java mirror is required. */
    void mirrorTicketLevel(int unused) {}

    protected void updateFutures(ChunkMap map, Executor executor) {
        this.generationOwner().lifecycle(0, new LifecycleFrame(map, executor, null));
    }
    public boolean wasAccessibleSinceLastSave() { return this.generationOwner().level(3) != 0; }
    public void refreshAccessibility() { this.generationOwner().levelChange(2, 0); }

    /** Only actual engine effects; Rust owns branching, membership and commit order. */
    private final class LifecycleFrame implements NativeChunkGeneration.LifecycleEffect {
        final ChunkMap map;
        final Executor executor;
        final CompletableFuture<?> dependency;
        CompletableFuture<Void> confirmation;
        CompletableFuture<ChunkResult<LevelChunk>> promotion;
        LifecycleFrame(ChunkMap map, Executor executor, CompletableFuture<?> dependency) {
            this.map = map; this.executor = executor; this.dependency = dependency;
        }
        private CompletableFuture<?> future(int slot) { return ChunkHolder.this.generationOwner().binding(slot); }
        public long apply(int op, int slot, int kind) {
            NativeChunkGeneration.Holder owner = ChunkHolder.this.generationOwner();
            switch (op) {
                case 30: return ChunkHolder.this.sync(slot, "isDone()").isDone() ? 1 : 0;
                case 31: owner.binding(slot, dependency); break;
                case 32: owner.binding(slot, ChunkHolder.this.sync(slot, "thenCombine(java.util.concurrent.CompletionStage, java.util.function.BiFunction)").thenCombine(dependency, (a, b) -> null)); break;
                case 33:
                    owner.binding(slot, switch (kind) {
                        case 0 -> map.prepareAccessibleChunk(ChunkHolder.this);
                        case 1 -> map.prepareTickingChunk(ChunkHolder.this);
                        case 2 -> map.prepareEntityTickingChunk(ChunkHolder.this);
                        default -> throw new IllegalStateException();
                    }); break;
                case 34: promotion = ChunkHolder.this.binding(slot); break;
                case 35: ChunkHolder.this.addSaveDependency(future(slot)); break;
                case 36: future(slot).cancel(false); break;
                case 37:
                    CompletableFuture<Void> next = new CompletableFuture<>();
                    FullChunkStatus status = FullChunkStatus.values()[kind];
                    ChunkMap chunkMap = map;
                    ChunkHolder chunkHolder = ChunkHolder.this;
                    next.thenRunAsync(() -> chunkMap.onFullChunkStatusChange(chunkHolder.pos, status), executor);
                    owner.binding(slot, next);
                    confirmation = next;
                    break;
                case 38:
                    CompletableFuture<Void> captured = confirmation;
                    if (promotion == null) throw new NullPointerException("Cannot invoke \"java.util.concurrent.CompletableFuture.thenAccept(java.util.function.Consumer)\" because \"completableFuture\" is null");
                    promotion.thenAccept(result -> result.ifSuccess(chunk -> captured.complete(null)));
                    break;
                case 39: ChunkHolder.this.<CompletableFuture<ChunkResult<LevelChunk>>>binding(slot).complete(UNLOADED_LEVEL_CHUNK); break;
                case 40: owner.binding(slot, UNLOADED_LEVEL_CHUNK_FUTURE); break;
                case 41: return future(slot) == UNLOADED_LEVEL_CHUNK_FUTURE ? 1 : 0;
                case 42: throw (IllegalStateException)Util.pauseInIde(new IllegalStateException());
                case 43: map.onFullChunkStatusChange(ChunkHolder.this.pos, FullChunkStatus.values()[slot]); break;
                case 44: ChunkHolder.this.onLevelChange.onLevelChange(ChunkHolder.this.pos,
                    ChunkHolder.this::getQueueLevel, owner.level(0), ChunkHolder.this::setQueueLevel); break;
                default: throw new IllegalStateException("Unknown native holder effect " + op);
            }
            return 0;
        }
    }
    private <T> T binding(int slot) { return this.generationOwner().binding(slot); }
    private CompletableFuture<?> sync(int slot, String method) {
        CompletableFuture<?> value = this.binding(slot);
        if (value == null) {
            String field = slot == NativeChunkGeneration.Holder.READY_BASE + 4 ? "sendSync" : "saveSync";
            throw new NullPointerException("Cannot invoke \"java.util.concurrent.CompletableFuture." + method + "\" because \"this." + field + "\" is null");
        }
        return value;
    }


	@FunctionalInterface
	public interface LevelChangeListener {
		void onLevelChange(ChunkPos chunkPos, IntSupplier intSupplier, int i, IntConsumer intConsumer);
	}

	public interface PlayerProvider {
		List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean bl);
	}
}
