package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.DataFixer;
import net.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ServerChunkCache extends ChunkSource {
	private static final Logger LOGGER = LogUtils.getLogger();
	public final DistanceManager distanceManager;
	private final ServerLevel level;
	final Thread mainThread;
	final ThreadedLevelLightEngine lightEngine;
	private final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
	public final ChunkMap chunkMap;
	private final DimensionDataStorage dataStorage;
	private final TicketStorage ticketStorage;
    private final NativeChunkGeneration.RequestGraph requestGraph;
	private long lastInhabitedUpdate;
	private boolean spawnEnemies = true;
	private static final int CACHE_SIZE = 4;
	private final ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
	private final ChunkAccess[] lastChunk = new ChunkAccess[4];
	private final List<LevelChunk> spawningChunks = new ObjectArrayList<>();
	private final Set<ChunkHolder> chunkHoldersToBroadcast = new ReferenceOpenHashSet<>();
	@Nullable
	@VisibleForDebug
	private NaturalSpawner.SpawnState lastSpawnState;

	public ServerChunkCache(
		ServerLevel serverLevel,
		LevelStorageSource.LevelStorageAccess levelStorageAccess,
		DataFixer dataFixer,
		StructureTemplateManager structureTemplateManager,
		Executor executor,
		ChunkGenerator chunkGenerator,
		int i,
		int j,
		boolean bl,
		ChunkStatusUpdateListener chunkStatusUpdateListener,
		Supplier<DimensionDataStorage> supplier
	) {
		this.level = serverLevel;
		this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(serverLevel);
		this.mainThread = Thread.currentThread();
		Path path = levelStorageAccess.getDimensionPath(serverLevel.dimension()).resolve("data");

		try {
			FileUtil.createDirectoriesSafe(path);
		} catch (IOException var14) {
			LOGGER.error("Failed to create dimension data storage directory", (Throwable)var14);
		}

		this.dataStorage = new DimensionDataStorage(new SavedData.Context(serverLevel), path, dataFixer, serverLevel.registryAccess());
		this.ticketStorage = this.dataStorage.computeIfAbsent(TicketStorage.TYPE);
		this.chunkMap = new ChunkMap(
			serverLevel,
			levelStorageAccess,
			dataFixer,
			structureTemplateManager,
			executor,
			this.mainThreadProcessor,
			this,
			chunkGenerator,
			chunkStatusUpdateListener,
			supplier,
			this.ticketStorage,
			i,
			bl
		);
		this.lightEngine = this.chunkMap.getLightEngine();
		this.distanceManager = this.chunkMap.getDistanceManager();
		this.distanceManager.updateSimulationDistance(j);
        this.requestGraph = new NativeChunkGeneration.RequestGraph(this.chunkMap, this.ticketStorage);
		this.clearCache();
	}

	public ThreadedLevelLightEngine getLightEngine() {
		return this.lightEngine;
	}

	@Nullable
	private ChunkHolder getVisibleChunkIfPresent(long l) {
		return this.chunkMap.getVisibleChunkIfPresent(l);
	}

    private void storeInCache(long key, @Nullable ChunkAccess chunk, ChunkStatus status) {
        int slot = NativeChunkGeneration.changeRequestCache(this.requestGraph, key, false);
        this.lastChunkStatus[slot] = status;
        this.lastChunk[slot] = chunk;
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
        return (ChunkAccess)this.runChunkCaller(3, x, z, status, create);
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int x, int z) {
        return (LevelChunk)this.runChunkCaller(4, x, z, ChunkStatus.FULL, false);
    }

    private void clearCache() {
        NativeChunkGeneration.changeRequestCache(this.requestGraph, 0L, true);
        Arrays.fill(this.lastChunkStatus, null);
        Arrays.fill(this.lastChunk, null);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int x, int z, ChunkStatus status, boolean create) {
        return (CompletableFuture<ChunkResult<ChunkAccess>>)this.runChunkCaller(5, x, z, status, create);
    }

    /** Actual VM resources/effects only; keys, order and all caller decisions are native. */
    private Object runChunkCaller(int operation, int x, int z, ChunkStatus status, boolean create) {
        NativeChunkGeneration.RequestGraph graph = this.requestGraph;
        long key = ChunkPos.asLong(x, z);
        long issued = NativeChunkGeneration.beginCaller(graph, operation, Thread.currentThread() == this.mainThread, key, create);
        long active = issued < 0 ? 0 : issued & ~127L;
        long command = issued < 0 ? issued : issued & 127L;
        ChunkResult<ChunkAccess> result = null;
        ChunkAccess chunk = null;
        LevelChunk now = null;
        ProfilerFiller profiler = null;
        try {
            while (true) {
                if (command < 0 && (command & (1L << 62)) == 0) active = 0;
                int op = (int)(command & 255L), slot = (int)(command >>> 8 & 65535L);
                long response = 0;
                switch (op) {
                    case 80: return CompletableFuture.supplyAsync(() -> this.getChunk(x, z, status, create), this.mainThreadProcessor).join();
                    case 81: return null;
                    case 82: return CompletableFuture.supplyAsync(() -> this.getChunkFutureMainThread(x, z, status, create), this.mainThreadProcessor).thenCompose(future -> future);
                    case 83: profiler = Profiler.get(); profiler.incrementCounter("getChunk"); break;
                    case 84: Profiler.get().incrementCounter("getChunkNow"); break;
                    case 85:
                        // Unconditional opaque reference observations have no callback.
                        // Native selects keys, insertion order, null policy and the winner.
                        for (int i = 0; i < CACHE_SIZE; i++) {
                            response |= this.lastChunkStatus[i] == status ? 1L << i : 0;
                            response |= this.lastChunk[i] != null ? 1L << (i + 4) : 0;
                            response |= this.lastChunk[i] instanceof LevelChunk ? 1L << (i + 8) : 0;
                        }
                        break;
                    case 86: return this.lastChunk[slot];
                    case 87:
                        profiler.incrementCounter("getChunkCacheMiss");
                        CompletableFuture<ChunkResult<ChunkAccess>> future = this.getChunkFutureMainThread(x, z, status, create);
                        this.mainThreadProcessor.managedBlock(future::isDone);
                        result = future.join(); chunk = result.orElse(null);
                        response = chunk == null ? 0 : 1;
                        break;
                    case 88: throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + result.getError()));
                    case 89:
                        this.lastChunkStatus[slot] = status; this.lastChunk[slot] = chunk; return chunk;
                    case 90:
                        RequestFrame frame = new RequestFrame(new ChunkPos(x, z), key, ChunkStatus.FULL, 0);
                        NativeChunkGeneration.request(graph, 0, false, 2, frame); now = frame.now; break;
                    case 91: response = this.distanceManager.runAllUpdates(this.chunkMap) ? 1 : 0; break;
                    case 92: response = this.chunkMap.promoteChunkMap() ? 1 : 0; break;
                    case 93: this.chunkMap.runGenerationTasks(); break;
                    case 94: this.clearCache(); return Boolean.TRUE;
                    case 95: return Boolean.FALSE;
                    case 96: return (LevelChunk)this.lastChunk[slot];
                    case 97:
                        CompletableFuture<ChunkResult<ChunkAccess>> mainFuture = this.getChunkFutureMainThread(x, z, status, create);
                        this.mainThreadProcessor.managedBlock(mainFuture::isDone); return mainFuture;
                    case 98: return now;
                    default: throw new IllegalStateException("Unknown native chunk caller effect " + op);
                }
                command = NativeChunkGeneration.nextRequest(graph, active, response, 0, op);
            }
        } finally {
            NativeChunkGeneration.abortRequest(active);
            java.lang.ref.Reference.reachabilityFence(graph);
            java.lang.ref.Reference.reachabilityFence(chunk);
            java.lang.ref.Reference.reachabilityFence(now);
            java.lang.ref.Reference.reachabilityFence(result);
        }
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int i, int j, ChunkStatus chunkStatus, boolean create) {
        ChunkPos chunkPos = new ChunkPos(i, j);
        long key = chunkPos.toLong();
        int level = ChunkLevel.byStatus(chunkStatus);
        NativeChunkGeneration.RequestGraph graph = this.requestGraph;
        long issued = NativeChunkGeneration.beginRequest(graph, level, create, 0);
        long active = issued & ~127L, command = issued & 127L;
        ChunkHolder chunkHolder = null;
        try {
            while (true) {
                if (command < 0 && (command & (1L << 62)) == 0) active = 0;
                int op = (int)(command & 255L);
                long response;
                switch (op) {
                    case 50:
                        chunkHolder = this.getVisibleChunkIfPresent(key);
                        response = requestHolderBinding(chunkHolder);
                        break;
                    case 68:
                        chunkHolder = this.getVisibleChunkIfPresent(key);
                        this.addGenerationRequestTicket(chunkPos, level);
                        response = requestHolderBinding(chunkHolder);
                        break;
                    case 52: response = chunkHolder.getTicketLevel(); break;
                    case 71:
                        ProfilerFiller profilerFiller = Profiler.get();
                        profilerFiller.push("chunkLoad");
                        this.runDistanceManagerUpdates();
                        chunkHolder = this.getVisibleChunkIfPresent(key);
                        profilerFiller.pop();
                        response = requestHolderBinding(chunkHolder);
                        break;
                    case 57: throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                    case 58: return chunkHolder.scheduleChunkGenerationTask(chunkStatus, this.chunkMap);
                    case 59: return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
                    default: throw new IllegalStateException("Unknown native request effect " + op);
                }
                command = NativeChunkGeneration.nextRequest(graph, active, response, 0, op);
            }
        } finally {
            NativeChunkGeneration.abortRequest(active);
            java.lang.ref.Reference.reachabilityFence(chunkHolder);
            java.lang.ref.Reference.reachabilityFence(graph);
        }
    }

    private static long requestHolderBinding(@Nullable ChunkHolder holder) {
        if (holder == null) return 0L;
        NativeChunkGeneration.Holder binding = holder.getClass() == ChunkHolder.class ? GenerationChunkHolder.generationOwner(holder) : null;
        return binding == null ? 1L : binding.handleId();
    }
    private void addGenerationRequestTicket(ChunkPos pos, int level) {
        // The actual virtual admission enters the native storage controller.
        // A cached descriptor cannot replace stored Ticket queries/reset effects.
        this.addTicket(new Ticket(TicketType.UNKNOWN, level), pos);
    }

    @Override
    public boolean hasChunk(int i, int j) {
        ChunkPos pos = new ChunkPos(i, j);
        RequestFrame frame = new RequestFrame(pos, pos.toLong(), ChunkStatus.FULL, 0);
        NativeChunkGeneration.request(this.requestGraph, 0, false, 1, frame);
        return frame.present;
    }

    /** Opaque JVM bindings and original effects; native request control owns all branches. */
    private final class RequestFrame implements NativeChunkGeneration.LifecycleEffect {
        final ChunkPos pos;
        final long key;
        final ChunkStatus status;
        int level;
        ChunkHolder holder;
        ProfilerFiller profiler;
        CompletableFuture<ChunkResult<ChunkAccess>> future;
        ChunkAccess chunk;
        LevelChunk now;
        boolean present;
        RequestFrame(ChunkPos pos, long key, ChunkStatus status, int level) {
            this.pos = pos; this.key = key; this.status = status; this.level = level;
        }
        private long bindHolder() {
            holder = ServerChunkCache.this.getVisibleChunkIfPresent(key);
            return holderBinding();
        }
        private long holderBinding() {
            if (holder == null) return 0L;
            NativeChunkGeneration.Holder binding = holder.getClass() == ChunkHolder.class ? GenerationChunkHolder.generationOwner(holder) : null;
            return binding == null ? 1L : binding.handleId();
        }
        public long apply(int op, int slot, int kind) {
            switch (op) {
                case 50, 55: return bindHolder();
                case 68:
                    holder = ServerChunkCache.this.getVisibleChunkIfPresent(key);
                    ServerChunkCache.this.addGenerationRequestTicket(pos, level);
                    return holderBinding();
                case 69: ServerChunkCache.this.storeInCache(key, chunk, ChunkStatus.FULL); now = (LevelChunk)chunk; break;
                case 70: ServerChunkCache.this.storeInCache(key, chunk, ChunkStatus.FULL); now = null; break;
                case 71:
                    profiler = Profiler.get(); profiler.push("chunkLoad");
                    ServerChunkCache.this.runDistanceManagerUpdates();
                    holder = ServerChunkCache.this.getVisibleChunkIfPresent(key);
                    profiler.pop();
                    return holderBinding();
                case 72:
                    holder = ServerChunkCache.this.getVisibleChunkIfPresent(key);
                    level = ChunkLevel.byStatus(ChunkStatus.FULL);
                    return holderBinding();
                case 73: return level;
                case 51: ServerChunkCache.this.addGenerationRequestTicket(pos, level); break;
                case 52: return holder.getTicketLevel();
                case 53: profiler = Profiler.get(); profiler.push("chunkLoad"); break;
                case 54: ServerChunkCache.this.runDistanceManagerUpdates(); break;
                case 56: profiler.pop(); break;
                case 57: throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                case 58: future = holder.scheduleChunkGenerationTask(status, ServerChunkCache.this.chunkMap); break;
                case 59: future = GenerationChunkHolder.UNLOADED_CHUNK_FUTURE; break;
                case 62: return ChunkLevel.byStatus(ChunkStatus.FULL);
                case 63:
                    chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
                    return chunk == null ? 0L : chunk instanceof LevelChunk ? 2L : 1L;
                case 64: ServerChunkCache.this.storeInCache(key, chunk, ChunkStatus.FULL); break;
                case 65: now = null; break;
                case 66: now = (LevelChunk)chunk; break;
                case 67: present = slot != 0; break;
                default: throw new IllegalStateException("Unknown native request effect " + op);
            }
            return 0;
        }
    }

	@Nullable
	@Override
	public LightChunk getChunkForLighting(int i, int j) {
		long l = ChunkPos.asLong(i, j);
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
		return chunkHolder == null ? null : chunkHolder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
	}

	public Level getLevel() {
		return this.level;
	}

	public boolean pollTask() {
		return this.mainThreadProcessor.pollTask();
	}

    boolean runDistanceManagerUpdates() {
        return (Boolean)this.runChunkCaller(6, 0, 0, null, false);
    }

	public boolean isPositionTicking(long l) {
		if (!this.level.shouldTickBlocksAt(l)) {
			return false;
		} else {
			ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
			return chunkHolder == null ? false : ((ChunkResult)chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).isSuccess();
		}
	}

	public void save(boolean bl) {
		this.runDistanceManagerUpdates();
		this.chunkMap.saveAllChunks(bl);
	}

	@Override
	public void close() throws IOException {
		this.save(true);
		this.dataStorage.close();
		this.lightEngine.close();
		this.chunkMap.close();
	}

	@Override
	public void tick(BooleanSupplier booleanSupplier, boolean bl) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("purge");
		if (this.level.tickRateManager().runsNormally() || !bl) {
			this.ticketStorage.purgeStaleTickets(this.chunkMap);
		}

		this.runDistanceManagerUpdates();
		profilerFiller.popPush("chunks");
		if (bl) {
			this.tickChunks();
			this.chunkMap.tick();
		}

		profilerFiller.popPush("unload");
		this.chunkMap.tick(booleanSupplier);
		profilerFiller.pop();
		this.clearCache();
	}

	private void tickChunks() {
		long l = this.level.getGameTime();
		long m = l - this.lastInhabitedUpdate;
		this.lastInhabitedUpdate = l;
		if (!this.level.isDebug()) {
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("pollingChunks");
			if (this.level.tickRateManager().runsNormally()) {
				profilerFiller.push("tickingChunks");
				this.tickChunks(profilerFiller, m);
				profilerFiller.pop();
			}

			this.broadcastChangedChunks(profilerFiller);
			profilerFiller.pop();
		}
	}

	private void broadcastChangedChunks(ProfilerFiller profilerFiller) {
		profilerFiller.push("broadcast");

		for (ChunkHolder chunkHolder : this.chunkHoldersToBroadcast) {
			LevelChunk levelChunk = chunkHolder.getTickingChunk();
			if (levelChunk != null) {
				chunkHolder.broadcastChanges(levelChunk);
			}
		}

		this.chunkHoldersToBroadcast.clear();
		profilerFiller.pop();
	}

	private void tickChunks(ProfilerFiller profilerFiller, long l) {
		profilerFiller.push("naturalSpawnCount");
		int i = this.distanceManager.getNaturalSpawnChunkCount();
		NaturalSpawner.SpawnState spawnState = NaturalSpawner.createState(
			i, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)
		);
		this.lastSpawnState = spawnState;
		boolean bl = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
		int j = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		List<MobCategory> list;
		if (bl) {
			boolean bl2 = this.level.getLevelData().getGameTime() % 400L == 0L;
			list = NaturalSpawner.getFilteredSpawningCategories(spawnState, true, this.spawnEnemies, bl2);
		} else {
			list = List.of();
		}

		List<LevelChunk> list2 = this.spawningChunks;

		try {
			profilerFiller.popPush("filteringSpawningChunks");
			this.chunkMap.collectSpawningChunks(list2);
			profilerFiller.popPush("shuffleSpawningChunks");
			Util.shuffle(list2, this.level.random);
			profilerFiller.popPush("tickSpawningChunks");

			for (LevelChunk levelChunk : list2) {
				this.tickSpawningChunk(levelChunk, l, list, spawnState);
			}
		} finally {
			list2.clear();
		}

		profilerFiller.popPush("tickTickingChunks");
		this.chunkMap.forEachBlockTickingChunk(levelChunkx -> this.level.tickChunk(levelChunkx, j));
		if (bl) {
			profilerFiller.popPush("customSpawners");
			this.level.tickCustomSpawners(this.spawnEnemies);
		}

		profilerFiller.pop();
	}

	private void tickSpawningChunk(LevelChunk levelChunk, long l, List<MobCategory> list, NaturalSpawner.SpawnState spawnState) {
		ChunkPos chunkPos = levelChunk.getPos();
		levelChunk.incrementInhabitedTime(l);
		if (this.distanceManager.inEntityTickingRange(chunkPos.toLong())) {
			this.level.tickThunder(levelChunk);
		}

		if (!list.isEmpty()) {
			if (this.level.canSpawnEntitiesInChunk(chunkPos)) {
				NaturalSpawner.spawnForChunk(this.level, levelChunk, spawnState, list);
			}
		}
	}

	private void getFullChunk(long l, Consumer<LevelChunk> consumer) {
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
		if (chunkHolder != null) {
			((ChunkResult)chunkHolder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).ifSuccess(consumer);
		}
	}

	@Override
	public String gatherStats() {
		return Integer.toString(this.getLoadedChunksCount());
	}

	@VisibleForTesting
	public int getPendingTasksCount() {
		return this.mainThreadProcessor.getPendingTasksCount();
	}

	public ChunkGenerator getGenerator() {
		return this.chunkMap.generator();
	}

	public ChunkGeneratorStructureState getGeneratorState() {
		return this.chunkMap.generatorState();
	}

	public RandomState randomState() {
		return this.chunkMap.randomState();
	}

	@Override
	public int getLoadedChunksCount() {
		return this.chunkMap.size();
	}

	public void blockChanged(BlockPos blockPos) {
		int i = SectionPos.blockToSectionCoord(blockPos.getX());
		int j = SectionPos.blockToSectionCoord(blockPos.getZ());
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(ChunkPos.asLong(i, j));
		if (chunkHolder != null && chunkHolder.blockChanged(blockPos)) {
			this.chunkHoldersToBroadcast.add(chunkHolder);
		}
	}

	@Override
	public void onLightUpdate(LightLayer lightLayer, SectionPos sectionPos) {
		this.mainThreadProcessor.execute(() -> {
			ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(sectionPos.chunk().toLong());
			if (chunkHolder != null && chunkHolder.sectionLightChanged(lightLayer, sectionPos.y())) {
				this.chunkHoldersToBroadcast.add(chunkHolder);
			}
		});
	}

	public boolean hasActiveTickets() {
		return this.ticketStorage.shouldKeepDimensionActive();
	}

	public void addTicket(Ticket ticket, ChunkPos chunkPos) {
		this.ticketStorage.addTicket(ticket, chunkPos);
	}

	public CompletableFuture<?> addTicketAndLoadWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		if (!ticketType.doesLoad()) {
			throw new IllegalStateException("Ticket type " + ticketType + " does not trigger chunk loading");
		} else if (ticketType.canExpireIfUnloaded()) {
			throw new IllegalStateException("Ticket type " + ticketType + " can expire before it loads, cannot fetch asynchronously");
		} else {
			this.addTicketWithRadius(ticketType, chunkPos, i);
			this.runDistanceManagerUpdates();
			ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos.toLong());
			Objects.requireNonNull(chunkHolder, "No chunk was scheduled for loading");
			return this.chunkMap.getChunkRangeFuture(chunkHolder, i, ix -> ChunkStatus.FULL);
		}
	}

	public void addTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		this.ticketStorage.addTicketWithRadius(ticketType, chunkPos, i);
	}

	public void removeTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		this.ticketStorage.removeTicketWithRadius(ticketType, chunkPos, i);
	}

	@Override
	public boolean updateChunkForced(ChunkPos chunkPos, boolean bl) {
		return this.ticketStorage.updateChunkForced(chunkPos, bl);
	}

	@Override
	public LongSet getForceLoadedChunks() {
		return this.ticketStorage.getForceLoadedChunks();
	}

	public void move(ServerPlayer serverPlayer) {
		if (!serverPlayer.isRemoved()) {
			this.chunkMap.move(serverPlayer);
			if (serverPlayer.isReceivingWaypoints()) {
				this.level.getWaypointManager().updatePlayer(serverPlayer);
			}
		}
	}

	public void removeEntity(Entity entity) {
		this.chunkMap.removeEntity(entity);
	}

	public void addEntity(Entity entity) {
		this.chunkMap.addEntity(entity);
	}

	public void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		this.chunkMap.sendToTrackingPlayersAndSelf(entity, packet);
	}

	public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		this.chunkMap.sendToTrackingPlayers(entity, packet);
	}

	public void setViewDistance(int i) {
		this.chunkMap.setServerViewDistance(i);
	}

	public void setSimulationDistance(int i) {
		this.distanceManager.updateSimulationDistance(i);
	}

	@Override
	public void setSpawnSettings(boolean bl) {
		this.spawnEnemies = bl;
	}

	public String getChunkDebugData(ChunkPos chunkPos) {
		return this.chunkMap.getChunkDebugData(chunkPos);
	}

	public DimensionDataStorage getDataStorage() {
		return this.dataStorage;
	}

	public PoiManager getPoiManager() {
		return this.chunkMap.getPoiManager();
	}

	public ChunkScanAccess chunkScanner() {
		return this.chunkMap.chunkScanner();
	}

	@Nullable
	@VisibleForDebug
	public NaturalSpawner.SpawnState getLastSpawnState() {
		return this.lastSpawnState;
	}

	public void deactivateTicketsOnClosing() {
		this.ticketStorage.deactivateTicketsOnClosing();
	}

	public void onChunkReadyToSend(ChunkHolder chunkHolder) {
		if (chunkHolder.hasChangesToBroadcast()) {
			this.chunkHoldersToBroadcast.add(chunkHolder);
		}
	}

	final class MainThreadExecutor extends BlockableEventLoop<Runnable> {
		MainThreadExecutor(final Level level) {
			super("Chunk source main thread executor for " + level.dimension().location());
		}

		@Override
		public void managedBlock(BooleanSupplier booleanSupplier) {
			super.managedBlock(() -> MinecraftServer.throwIfFatalException() && booleanSupplier.getAsBoolean());
		}

		@Override
		public Runnable wrapRunnable(Runnable runnable) {
			return runnable;
		}

		@Override
		protected boolean shouldRun(Runnable runnable) {
			return true;
		}

		@Override
		protected boolean scheduleExecutables() {
			return true;
		}

		@Override
		protected Thread getRunningThread() {
			return ServerChunkCache.this.mainThread;
		}

		@Override
		protected void doRunTask(Runnable runnable) {
			Profiler.get().incrementCounter("runTask");
			super.doRunTask(runnable);
		}

		@Override
		public boolean pollTask() {
			if (ServerChunkCache.this.runDistanceManagerUpdates()) {
				return true;
			} else {
				ServerChunkCache.this.lightEngine.tryScheduleUpdate();
				return super.pollTask();
			}
		}
	}
}
