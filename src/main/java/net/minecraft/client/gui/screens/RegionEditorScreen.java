package net.minecraft.client.gui.screens;

import com.mojang.serialization.Codec;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RegionEditorScreen extends Screen {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.literal("Region Editor");
	private static final Pattern REGION_FILE_PATTERN = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
	private static final int CHUNKS_PER_REGION = 32;
	private static final int CHUNK_COUNT_PER_REGION = CHUNKS_PER_REGION * CHUNKS_PER_REGION;
	private static final int WORLD_BUTTON_LIMIT = 8;
	private static final int REGION_BATCH_SIZE = 16;
	private static final int MAX_BATCHES_APPLIED_PER_TICK = 4;
	private static final int MAX_PENDING_BATCH_UPDATES = 16;
	private static final int MAX_PARALLEL_REGION_READERS = 8;
	private static final int MAX_REGION_CELLS_DRAWN_PER_FRAME = 40000;
	private static final int MAX_CHUNK_DETAIL_REQUESTS_PER_FRAME = 96;
	private static final int MAX_CHUNK_DETAILS_APPLIED_PER_TICK = 96;
	private static final float MIN_MAP_ZOOM = 0.25F;
	private static final float MAX_MAP_ZOOM = 128.0F;
	private static final float MAP_ZOOM_STEP = 1.2F;
	private static final long PROGRESS_LOG_INTERVAL_NANOS = 500_000_000L;
	private static final long RENDER_LOG_INTERVAL_NANOS = 1_000_000_000L;
	private static final long SLOW_FRAME_THRESHOLD_NANOS = 33_000_000L;
	private static final long EXTREME_FRAME_THRESHOLD_NANOS = 250_000_000L;
	private static final int DEFAULT_CHUNK_COLOR = 0xFF4A8F4A;
	private static final int CHUNK_SAMPLE_STEP = 4;
	private static final int FALLBACK_MIN_Y = -64;
	private static final int BLOCKS_PER_CHUNK_EDGE = 16;
	private static final int BLOCKS_PER_REGION_EDGE = CHUNKS_PER_REGION * BLOCKS_PER_CHUNK_EDGE;
	private static final BlockState AIR_BLOCK_STATE = Blocks.AIR.defaultBlockState();
	private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES_CODEC =
		PalettedContainer.codecRW(BlockState.CODEC, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), AIR_BLOCK_STATE);
	private static final RegionStorageInfo REGION_EDITOR_STORAGE_INFO = new RegionStorageInfo("region_editor", Level.OVERWORLD, "region_editor");

	private final Screen lastScreen;
	private Button fileButton;
	private Button loadWorldButton;
	private Button backButton;

	private boolean fileMenuOpen;
	private boolean worldPickerOpen;
	private boolean loadingWorldList;
	private final List<Button> worldButtons = new ArrayList<>();

	private List<LevelSummary> availableWorlds = List.of();
	private String loadedWorldName;
	private String loadStatus = "No world loaded. Use File -> Load World.";
	private boolean loadingWorld;
	private CompletableFuture<List<LevelSummary>> pendingWorldListLoad;
	private CompletableFuture<Void> pendingLoadTask;
	private final ConcurrentLinkedQueue<RegionBatchUpdate> pendingRegionUpdates = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<ChunkDetailUpdate> pendingChunkDetailUpdates = new ConcurrentLinkedQueue<>();
	private final ConcurrentHashMap<Long, Boolean> chunkDetailsInFlight = new ConcurrentHashMap<>();

	private final Map<Long, int[]> chunkColorsByRegion = new HashMap<>();
	private final Map<Long, int[]> blockColorsByChunk = new HashMap<>();
	private Path currentRegionDir;
	private int minRegionX;
	private int maxRegionX;
	private int minRegionZ;
	private int maxRegionZ;
	private int loadRequestCounter;
	private volatile int activeLoadRequest;
	private boolean loggedFirstBatchApply;
	private int loadFilesScanned;
	private int loadRegionsApplied;
	private long worldLoadStartNanos;
	private long lastProgressLogNanos;
	private long lastRenderLogNanos;
	private long renderFrameCounter;
	private long slowRenderFrameCounter;
	private float mapZoom = 1.0F;
	private double mapPanX;
	private double mapPanZ;
	private int mapInnerLeft;
	private int mapInnerTop;
	private int mapInnerRight;
	private int mapInnerBottom;

	public RegionEditorScreen(Screen lastScreen) {
		super(TITLE);
		this.lastScreen = lastScreen;
	}

	@Override
	protected void init() {
		this.trace("init(): width=" + this.width + " height=" + this.height);
		this.fileButton = this.addRenderableWidget(
			Button.builder(Component.literal("File"), button -> this.toggleFileMenu())
				.bounds(8, 8, 50, 20)
				.build()
		);

		this.loadWorldButton = this.addRenderableWidget(
			Button.builder(Component.literal("Load World"), button -> this.openWorldPicker())
				.bounds(12, 32, 120, 20)
				.build()
		);
		this.loadWorldButton.visible = false;
		this.loadWorldButton.active = false;

		this.backButton = this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen))
				.bounds(this.width - 68, 8, 60, 20)
				.build()
		);

		this.rebuildWorldButtons();
	}

	private void toggleFileMenu() {
		this.fileMenuOpen = !this.fileMenuOpen;
		this.trace("toggleFileMenu(): fileMenuOpen=" + this.fileMenuOpen + " worldPickerOpen=" + this.worldPickerOpen);
		if (!this.fileMenuOpen) {
			this.worldPickerOpen = false;
		}
		this.refreshMenuVisibility();
	}

	private void openWorldPicker() {
		long startNanos = System.nanoTime();
		this.loadingWorldList = true;
		this.availableWorlds = List.of();
		this.loadStatus = "Loading world list...";
		this.trace("openWorldPicker(): begin async world list load");
		this.pendingWorldListLoad = CompletableFuture.supplyAsync(this::loadAvailableWorlds, Util.backgroundExecutor());
		this.worldPickerOpen = true;
		this.rebuildWorldButtons();
		this.refreshMenuVisibility();
		this.traceNanos("openWorldPicker(): setup complete", startNanos);
	}

	private void refreshMenuVisibility() {
		this.loadWorldButton.visible = this.fileMenuOpen;
		this.loadWorldButton.active = this.fileMenuOpen && !this.loadingWorldList;

		boolean showWorldButtons = this.fileMenuOpen && this.worldPickerOpen;
		for (Button button : this.worldButtons) {
			button.visible = showWorldButtons;
			button.active = showWorldButtons;
		}
	}

	private List<LevelSummary> loadAvailableWorlds() {
		long startNanos = System.nanoTime();
		try {
			LevelStorageSource levelStorageSource = this.minecraft.getLevelSource();
			LevelStorageSource.LevelCandidates candidates = levelStorageSource.findLevelCandidates();
			List<LevelSummary> worlds = levelStorageSource.loadLevelSummaries(candidates).join();
			this.traceNanos("loadAvailableWorlds(): loaded " + worlds.size() + " entries", startNanos);
			return worlds;
		} catch (LevelStorageException exception) {
			LOGGER.error("Failed to enumerate worlds for Region Editor", exception);
			this.trace("loadAvailableWorlds(): failed with " + exception);
			return List.of();
		}
	}

	private void rebuildWorldButtons() {
		for (Button button : this.worldButtons) {
			this.removeWidget(button);
		}
		this.worldButtons.clear();

		int y = 56;
		int count = Math.min(WORLD_BUTTON_LIMIT, this.availableWorlds.size());
		for (int i = 0; i < count; i++) {
			LevelSummary summary = this.availableWorlds.get(i);
			Button worldButton = this.addRenderableWidget(
				Button.builder(Component.literal(summary.getLevelName()), button -> this.loadWorld(summary))
					.bounds(12, y, 180, 20)
					.build()
			);
			worldButton.visible = false;
			worldButton.active = false;
			this.worldButtons.add(worldButton);
			y += 22;
		}
	}

	private void loadWorld(LevelSummary summary) {
		long startNanos = System.nanoTime();
		this.loadedWorldName = summary.getLevelName();
		this.loadingWorld = true;
		this.loadStatus = "Loading world: " + this.loadedWorldName + "...";
		this.chunkColorsByRegion.clear();
		this.blockColorsByChunk.clear();
		this.pendingChunkDetailUpdates.clear();
		this.chunkDetailsInFlight.clear();
		this.mapZoom = 1.0F;
		this.mapPanX = 0.0;
		this.mapPanZ = 0.0;
		this.minRegionX = 0;
		this.maxRegionX = 0;
		this.minRegionZ = 0;
		this.maxRegionZ = 0;
		this.loadFilesScanned = 0;
		this.loadRegionsApplied = 0;
		this.pendingRegionUpdates.clear();
		this.loggedFirstBatchApply = false;
		this.worldLoadStartNanos = System.nanoTime();
		this.lastProgressLogNanos = this.worldLoadStartNanos;
		Path worldPath = this.minecraft.getLevelSource().getBaseDir().resolve(summary.getLevelId());
		Path regionDir = worldPath.resolve("region");
		this.currentRegionDir = regionDir;
		final int requestId = ++this.loadRequestCounter;
		this.activeLoadRequest = requestId;
		this.trace("loadWorld(): requestId=" + requestId + " world=" + this.loadedWorldName + " regionDir=" + regionDir);
		this.pendingLoadTask = CompletableFuture.runAsync(() -> {
			try {
				this.streamRegionData(regionDir, requestId);
			} catch (IOException exception) {
				throw new CompletionException(exception);
			}
		}, Util.ioPool()).whenComplete((unused, throwable) -> {
			if (requestId != this.activeLoadRequest) {
				return;
			}

			if (throwable != null) {
				Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
				LOGGER.error("Region Editor: world load failed for {}", this.loadedWorldName, cause);
				this.trace("loadWorld(): failed requestId=" + requestId + " cause=" + cause);
				this.pendingRegionUpdates.add(RegionBatchUpdate.failure(this.loadFilesScanned, this.loadRegionsApplied));
				return;
			}

			this.trace("loadWorld(): scan complete requestId=" + requestId + " regions=" + this.loadRegionsApplied + " files=" + this.loadFilesScanned);
			this.pendingRegionUpdates.add(RegionBatchUpdate.complete(this.loadFilesScanned, this.loadRegionsApplied));
		});
		this.fileMenuOpen = false;
		this.worldPickerOpen = false;
		this.refreshMenuVisibility();
		this.traceNanos("loadWorld(): queued background work for requestId=" + requestId, startNanos);
	}

	private void streamRegionData(Path regionDir, int requestId) throws IOException {
		long startNanos = System.nanoTime();
		this.trace("streamRegionData(): requestId=" + requestId + " dir=" + regionDir);
		if (!Files.isDirectory(regionDir)) {
			this.trace("streamRegionData(): no region directory for requestId=" + requestId);
			return;
		}

		Map<Long, int[]> batch = new LinkedHashMap<>();
		int filesScanned = 0;
		int regionsLoaded = 0;
		int lastLoggedFiles = 0;

		int workerCount = Math.max(1, Math.min(MAX_PARALLEL_REGION_READERS, Util.maxAllowedExecutorThreads()));
		this.trace("streamRegionData(): requestId=" + requestId + " using workerCount=" + workerCount + " maxInFlight=" + (workerCount * 4));
		ExecutorService executorService = Executors.newFixedThreadPool(workerCount, runnable -> {
			Thread thread = new Thread(runnable, "RegionEditor-IO");
			thread.setDaemon(true);
			return thread;
		});
		CompletionService<RegionFileResult> completionService = new ExecutorCompletionService<>(executorService);
		int inFlight = 0;
		int maxInFlight = workerCount * 4;

		try {
			try (var stream = Files.list(regionDir)) {
				for (Path regionFile : (Iterable<Path>)stream::iterator) {
					if (requestId != this.activeLoadRequest) {
						this.trace("streamRegionData(): requestId=" + requestId + " cancelled while scanning");
						return;
					}

					String fileName = regionFile.getFileName().toString();
					Matcher matcher = REGION_FILE_PATTERN.matcher(fileName);
					if (!matcher.matches()) {
						continue;
					}

					int regionX = Integer.parseInt(matcher.group(1));
					int regionZ = Integer.parseInt(matcher.group(2));

					while (inFlight >= maxInFlight) {
						RegionFileResult result = takeCompletedRegion(completionService, requestId);
						inFlight--;
						if (result != null) {
							batch.put(result.packedRegion(), result.chunkColors());
							regionsLoaded++;
						}
						if (batch.size() >= REGION_BATCH_SIZE) {
							waitForQueueCapacity(requestId);
							this.publishBatch(requestId, new LinkedHashMap<>(batch), filesScanned, regionsLoaded, false);
							batch.clear();
						}
					}

					completionService.submit(() -> readRegionFile(regionFile, regionX, regionZ));
					inFlight++;
					filesScanned++;

					if (filesScanned - lastLoggedFiles >= 256) {
						waitForQueueCapacity(requestId);
						lastLoggedFiles = filesScanned;
						this.publishProgress(requestId, filesScanned, regionsLoaded, false);
					}
				}
			}

			while (inFlight > 0) {
				RegionFileResult result = takeCompletedRegion(completionService, requestId);
				inFlight--;
				if (result != null) {
					batch.put(result.packedRegion(), result.chunkColors());
					regionsLoaded++;
				}
				if (batch.size() >= REGION_BATCH_SIZE) {
					waitForQueueCapacity(requestId);
					this.publishBatch(requestId, new LinkedHashMap<>(batch), filesScanned, regionsLoaded, false);
					batch.clear();
				}
			}
		} finally {
			executorService.shutdownNow();
		}

		if (!batch.isEmpty()) {
			waitForQueueCapacity(requestId);
			this.publishBatch(requestId, new LinkedHashMap<>(batch), filesScanned, regionsLoaded, true);
		} else {
			waitForQueueCapacity(requestId);
			this.publishProgress(requestId, filesScanned, regionsLoaded, true);
		}

		this.traceNanos(
			"streamRegionData(): finished requestId=" + requestId + " filesScanned=" + filesScanned + " regionsLoaded=" + regionsLoaded,
			startNanos
		);
	}

	private RegionFileResult takeCompletedRegion(CompletionService<RegionFileResult> completionService, int requestId) throws IOException {
		if (requestId != this.activeLoadRequest) {
			this.trace("takeCompletedRegion(): requestId=" + requestId + " stale/cancelled");
			return null;
		}

		try {
			Future<RegionFileResult> future = completionService.take();
			return future.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			this.trace("takeCompletedRegion(): interrupted requestId=" + requestId);
			return null;
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof IOException ioException) {
				throw ioException;
			}
			throw new IOException("Failed to scan region file", cause);
		}
	}

	private void waitForQueueCapacity(int requestId) {
		int waits = 0;
		while (requestId == this.activeLoadRequest && this.pendingRegionUpdates.size() >= MAX_PENDING_BATCH_UPDATES) {
			waits++;
			if (waits == 1 || waits % 250 == 0) {
				this.trace(
					"waitForQueueCapacity(): requestId=" + requestId
						+ " waits=" + waits
						+ " queueSize=" + this.pendingRegionUpdates.size()
						+ " maxQueue=" + MAX_PENDING_BATCH_UPDATES
				);
			}
			try {
				Thread.sleep(1L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				this.trace("waitForQueueCapacity(): interrupted requestId=" + requestId + " after waits=" + waits);
				return;
			}
		}

		if (waits > 0) {
			this.trace("waitForQueueCapacity(): resumed requestId=" + requestId + " totalWaits=" + waits);
		}
	}

	private static BitSet readChunkPresence(Path regionFile) {
		try {
			byte[] header = new byte[4096];
			try (InputStream inputStream = Files.newInputStream(regionFile)) {
				int read = inputStream.readNBytes(header, 0, header.length);
				if (read < 4096) {
					return null;
				}
			}

			if (header.length < 4096) {
				return null;
			}

			BitSet bitSet = new BitSet(CHUNK_COUNT_PER_REGION);
			for (int i = 0; i < CHUNK_COUNT_PER_REGION; i++) {
				int base = i * 4;
				int sectorOffset = ((header[base] & 255) << 16) | ((header[base + 1] & 255) << 8) | (header[base + 2] & 255);
				if (sectorOffset != 0) {
					bitSet.set(i);
				}
			}
			return bitSet;
		} catch (IOException exception) {
			LOGGER.error("Failed to read region header {}", regionFile, exception);
			return null;
		}
	}

	private static RegionFileResult readRegionFile(Path regionFile, int regionX, int regionZ) {
		long startNanos = System.nanoTime();
		BitSet chunkPresence = readChunkPresence(regionFile);
		if (chunkPresence == null) {
			return null;
		}

		int[] chunkColors = new int[CHUNK_COUNT_PER_REGION];
		boolean hasAnyChunk = false;
		Path regionFolder = regionFile.getParent();

		try (RegionFile anvilRegion = new RegionFile(REGION_EDITOR_STORAGE_INFO, regionFile, regionFolder, false)) {
			for (int idx = chunkPresence.nextSetBit(0); idx >= 0; idx = chunkPresence.nextSetBit(idx + 1)) {
				hasAnyChunk = true;
				int chunkLocalX = idx & 31;
				int chunkLocalZ = idx >> 5;
				int chunkX = regionX * CHUNKS_PER_REGION + chunkLocalX;
				int chunkZ = regionZ * CHUNKS_PER_REGION + chunkLocalZ;
				ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

				try (DataInputStream chunkInput = anvilRegion.getChunkDataInputStream(chunkPos)) {
					if (chunkInput == null) {
						chunkColors[idx] = DEFAULT_CHUNK_COLOR;
						continue;
					}

					CompoundTag chunkTag = NbtIo.read(chunkInput);
					chunkColors[idx] = extractChunkTopColor(chunkTag);
				} catch (Exception exception) {
					chunkColors[idx] = DEFAULT_CHUNK_COLOR;
					LOGGER.debug("Region Editor: failed to decode chunk color for {}", chunkPos, exception);
				}
			}
		} catch (Exception exception) {
			LOGGER.warn("Region Editor: failed to decode region {}, using fallback occupancy colors", regionFile.getFileName(), exception);
			for (int idx = chunkPresence.nextSetBit(0); idx >= 0; idx = chunkPresence.nextSetBit(idx + 1)) {
				hasAnyChunk = true;
				chunkColors[idx] = DEFAULT_CHUNK_COLOR;
			}
		}

		if (!hasAnyChunk) {
			return null;
		}

		long elapsedNanos = System.nanoTime() - startNanos;
		if (elapsedNanos >= 5_000_000L) {
			LOGGER.info("Region Editor: readRegionFile slow path file={} took={}ms", regionFile.getFileName(), elapsedNanos / 1_000_000L);
			System.out.println("[RegionEditor] readRegionFile slow path file=" + regionFile.getFileName() + " took=" + (elapsedNanos / 1_000_000L) + "ms");
		}
		return new RegionFileResult(packRegion(regionX, regionZ), chunkColors);
	}

	private static int extractChunkTopColor(CompoundTag chunkTag) {
		int[] blockColors = extractChunkTopBlockColors(chunkTag);
		if (blockColors == null) {
			return DEFAULT_CHUNK_COLOR;
		}

		int red = 0;
		int green = 0;
		int blue = 0;
		int samples = 0;

		for (int localZ = 0; localZ < 16; localZ += CHUNK_SAMPLE_STEP) {
			for (int localX = 0; localX < 16; localX += CHUNK_SAMPLE_STEP) {
				int rgb = blockColors[localX + localZ * BLOCKS_PER_CHUNK_EDGE];
				if ((rgb >>> 24) == 0) {
					continue;
				}

				red += (rgb >> 16) & 255;
				green += (rgb >> 8) & 255;
				blue += rgb & 255;
				samples++;
			}
		}

		if (samples == 0) {
			return DEFAULT_CHUNK_COLOR;
		}

		return 0xFF000000
			| ((red / samples) << 16)
			| ((green / samples) << 8)
			| (blue / samples);
	}

	private static int[] extractChunkTopBlockColors(CompoundTag chunkTag) {
		CompoundTag root = chunkTag.contains("Level") ? chunkTag.getCompoundOrEmpty("Level") : chunkTag;
		long[] heightmap = readPreferredHeightmap(root);
		if (heightmap == null) {
			return null;
		}

		Map<Integer, PalettedContainer<BlockState>> sectionsByY = decodeSections(root);
		if (sectionsByY.isEmpty()) {
			return null;
		}

		int minY = resolveChunkMinY(root, sectionsByY);
		int bits = inferHeightmapBits(heightmap.length);
		if (bits <= 0) {
			return null;
		}

		SimpleBitStorage storage;
		try {
			storage = new SimpleBitStorage(bits, 256, heightmap);
		} catch (Exception exception) {
			return null;
		}

		int[] blockColors = new int[BLOCKS_PER_CHUNK_EDGE * BLOCKS_PER_CHUNK_EDGE];
		for (int localZ = 0; localZ < BLOCKS_PER_CHUNK_EDGE; localZ++) {
			for (int localX = 0; localX < BLOCKS_PER_CHUNK_EDGE; localX++) {
				int index = localX + localZ * BLOCKS_PER_CHUNK_EDGE;
				int topY = storage.get(index) + minY - 1;
				BlockState state = getStateAt(sectionsByY, localX, topY, localZ);
				blockColors[index] = colorForBlockState(state);
			}
		}
		return blockColors;
	}

	private static Map<Integer, PalettedContainer<BlockState>> decodeSections(CompoundTag chunkRoot) {
		Map<Integer, PalettedContainer<BlockState>> sectionsByY = new HashMap<>();
		for (CompoundTag sectionTag : chunkRoot.getListOrEmpty("sections").compoundStream().toList()) {
			int sectionY = sectionTag.getByteOr("Y", (byte)0);
			CompoundTag blockStatesTag = sectionTag.getCompound("block_states").orElse(null);
			if (blockStatesTag == null || blockStatesTag.isEmpty()) {
				continue;
			}

			PalettedContainer<BlockState> container = BLOCK_STATES_CODEC.parse(NbtOps.INSTANCE, blockStatesTag).result().orElse(null);
			if (container != null) {
				sectionsByY.put(sectionY, container);
			}
		}
		return sectionsByY;
	}

	private static long[] readPreferredHeightmap(CompoundTag chunkRoot) {
		CompoundTag heightmaps = chunkRoot.getCompoundOrEmpty("Heightmaps");
		long[] worldSurface = heightmaps.getLongArray("WORLD_SURFACE").orElse(null);
		if (worldSurface != null && worldSurface.length > 0) {
			return worldSurface;
		}

		long[] motionBlocking = heightmaps.getLongArray("MOTION_BLOCKING").orElse(null);
		if (motionBlocking != null && motionBlocking.length > 0) {
			return motionBlocking;
		}

		return null;
	}

	private static int resolveChunkMinY(CompoundTag chunkRoot, Map<Integer, PalettedContainer<BlockState>> sectionsByY) {
		int sectionMin = sectionsByY.keySet().stream().min(Integer::compareTo).orElse(FALLBACK_MIN_Y / 16);
		return chunkRoot.getIntOr("yPos", sectionMin) * 16;
	}

	private static int inferHeightmapBits(int longArrayLength) {
		for (int bits = 1; bits <= 16; bits++) {
			int valuesPerLong = 64 / bits;
			if (valuesPerLong <= 0) {
				continue;
			}
			int expectedLength = Mth.positiveCeilDiv(256, valuesPerLong);
			if (expectedLength == longArrayLength) {
				return bits;
			}
		}
		return -1;
	}

	private static BlockState getStateAt(Map<Integer, PalettedContainer<BlockState>> sectionsByY, int localX, int worldY, int localZ) {
		int sectionY = Math.floorDiv(worldY, 16);
		PalettedContainer<BlockState> section = sectionsByY.get(sectionY);
		if (section == null) {
			return AIR_BLOCK_STATE;
		}

		int yInSection = worldY & 15;
		return section.get(localX & 15, yInSection, localZ & 15);
	}

	private static int colorForBlockState(BlockState state) {
		if (state == null || state.isAir()) {
			return 0;
		}

		MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		int rgb = mapColor.col;
		if (rgb == 0) {
			rgb = state.getBlock().defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
		}

		return rgb == 0 ? 0 : (0xFF000000 | rgb);
	}

	@Override
	public void tick() {
		super.tick();
		long tickStartNanos = System.nanoTime();
		if (this.pendingWorldListLoad != null && this.pendingWorldListLoad.isDone()) {
			CompletableFuture<List<LevelSummary>> completedWorldListLoad = this.pendingWorldListLoad;
			this.pendingWorldListLoad = null;
			try {
				this.availableWorlds = completedWorldListLoad.join();
				this.loadingWorldList = false;
				this.loadStatus = this.availableWorlds.isEmpty() ? "No worlds found." : "Select a world to load.";
				this.trace("tick(): world list ready entries=" + this.availableWorlds.size());
			} catch (CompletionException exception) {
				this.loadingWorldList = false;
				this.availableWorlds = List.of();
				this.loadStatus = "Failed to load world list.";
				Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
				LOGGER.error("Region Editor: failed to load world list", cause);
				this.trace("tick(): world list load failed cause=" + cause);
			}

			this.rebuildWorldButtons();
			this.refreshMenuVisibility();
		}

		int appliedBatches = 0;
		int queueStartSize = this.pendingRegionUpdates.size();
		while (appliedBatches < MAX_BATCHES_APPLIED_PER_TICK) {
			RegionBatchUpdate update = this.pendingRegionUpdates.poll();
			if (update == null) {
				break;
			}

			this.applyRegionUpdate(update);
			appliedBatches++;
		}

		if (appliedBatches > 0) {
			this.trace(
				"tick(): appliedBatches=" + appliedBatches
					+ " queueStart=" + queueStartSize
					+ " queueEnd=" + this.pendingRegionUpdates.size()
					+ " filesScanned=" + this.loadFilesScanned
					+ " regionsApplied=" + this.loadRegionsApplied
			);
		}

		int appliedChunkDetails = 0;
		while (appliedChunkDetails < MAX_CHUNK_DETAILS_APPLIED_PER_TICK) {
			ChunkDetailUpdate detailUpdate = this.pendingChunkDetailUpdates.poll();
			if (detailUpdate == null) {
				break;
			}

			if (detailUpdate.requestId() == this.activeLoadRequest) {
				this.blockColorsByChunk.put(detailUpdate.chunkKey(), detailUpdate.blockColors());
			}
			appliedChunkDetails++;
		}

		if (appliedChunkDetails > 0) {
			this.trace(
				"tick(): appliedChunkDetails=" + appliedChunkDetails
					+ " cachedChunks=" + this.blockColorsByChunk.size()
					+ " inFlight=" + this.chunkDetailsInFlight.size()
			);
		}

		long tickElapsedNanos = System.nanoTime() - tickStartNanos;
		if (tickElapsedNanos >= 20_000_000L) {
			this.trace("tick(): slow tick took=" + (tickElapsedNanos / 1_000_000L) + "ms queue=" + this.pendingRegionUpdates.size());
		}
	}

	private static long packRegion(int x, int z) {
		return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static long packChunk(int x, int z) {
		return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!this.isInMapArea(mouseX, mouseY) || scrollY == 0.0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		if (this.chunkColorsByRegion.isEmpty()) {
			return true;
		}

		MapTransform before = this.computeMapTransform(this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
		double worldU = (mouseX - before.startX()) / Math.max(1.0, before.regionPixel());
		double worldV = (mouseY - before.startZ()) / Math.max(1.0, before.regionPixel());

		float nextZoom = scrollY > 0.0 ? this.mapZoom * MAP_ZOOM_STEP : this.mapZoom / MAP_ZOOM_STEP;
		this.mapZoom = Mth.clamp(nextZoom, MIN_MAP_ZOOM, MAX_MAP_ZOOM);

		MapTransform afterNoPan = this.computeMapTransformWithoutPan(this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom, this.mapZoom);
		this.mapPanX = mouseX - (afterNoPan.startX() + worldU * Math.max(1.0, afterNoPan.regionPixel()));
		this.mapPanZ = mouseY - (afterNoPan.startZ() + worldV * Math.max(1.0, afterNoPan.regionPixel()));
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
		if (mouseButtonEvent.button() == 0 && this.isInMapArea(mouseButtonEvent.x(), mouseButtonEvent.y()) && !this.fileMenuOpen) {
			this.mapPanX += deltaX;
			this.mapPanZ += deltaY;
			return true;
		}

		return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
	}

	private boolean isInMapArea(double x, double y) {
		return x >= this.mapInnerLeft && x <= this.mapInnerRight && y >= this.mapInnerTop && y <= this.mapInnerBottom;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		long renderStartNanos = System.nanoTime();
		this.renderFrameCounter++;
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

		int mapLeft = 8;
		int mapTop = 36;
		int mapRight = this.width - 8;
		int mapBottom = this.height - 8;
		this.mapInnerLeft = mapLeft + 4;
		this.mapInnerTop = mapTop + 4;
		this.mapInnerRight = mapRight - 4;
		this.mapInnerBottom = mapBottom - 4;
		guiGraphics.fill(mapLeft, mapTop, mapRight, mapBottom, 0xCC151515);
		guiGraphics.fill(mapLeft + 1, mapTop + 1, mapRight - 1, mapBottom - 1, 0xFF1B1B1B);

		RenderStats renderStats = null;
		if (!this.chunkColorsByRegion.isEmpty()) {
			renderStats = this.renderRegionMap(guiGraphics, this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
		} else {
			guiGraphics.drawCenteredString(this.font, Component.literal(this.loadStatus), this.width / 2, this.height / 2 - 4, 0xFFB0B0B0);
		}

		if (this.loadedWorldName != null) {
			guiGraphics.drawString(this.font, Component.literal("World: " + this.loadedWorldName), mapLeft + 8, mapTop + 8, 0xFFFFFFFF);
		}

		if (this.loadingWorld) {
			guiGraphics.drawString(this.font, Component.literal("Loading..."), mapLeft + 8, mapTop + 20, 0xFFE0C070);
		}

		if (this.fileMenuOpen) {
			this.renderFileMenu(guiGraphics);
		}

		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		long renderElapsedNanos = System.nanoTime() - renderStartNanos;
		if (renderElapsedNanos >= SLOW_FRAME_THRESHOLD_NANOS) {
			this.slowRenderFrameCounter++;
			String severity = renderElapsedNanos >= EXTREME_FRAME_THRESHOLD_NANOS ? "EXTREME" : "SLOW";
			this.trace(
				"render(): " + severity + " frame=" + this.renderFrameCounter
					+ " took=" + (renderElapsedNanos / 1_000_000L) + "ms"
					+ " regions=" + this.chunkColorsByRegion.size()
					+ " queue=" + this.pendingRegionUpdates.size()
					+ " loadingWorld=" + this.loadingWorld
					+ renderStatsSuffix(renderStats)
			);
		}

		long nowNanos = System.nanoTime();
		if (nowNanos - this.lastRenderLogNanos >= RENDER_LOG_INTERVAL_NANOS) {
			this.lastRenderLogNanos = nowNanos;
			this.trace(
				"render(): heartbeat frame=" + this.renderFrameCounter
					+ " slowFrames=" + this.slowRenderFrameCounter
					+ " regions=" + this.chunkColorsByRegion.size()
					+ " queue=" + this.pendingRegionUpdates.size()
					+ " status=\"" + this.loadStatus + "\""
					+ renderStatsSuffix(renderStats)
			);
		}
	}

	private void renderFileMenu(GuiGraphics guiGraphics) {
		int menuLeft = 8;
		int menuTop = 30;
		int menuWidth = 188;
		int menuHeight = this.worldPickerOpen ? 210 : 30;
		guiGraphics.fill(menuLeft, menuTop, menuLeft + menuWidth, menuTop + menuHeight, 0xF0202020);
		guiGraphics.fill(menuLeft + 1, menuTop + 1, menuLeft + menuWidth - 1, menuTop + menuHeight - 1, 0xF0323232);

		if (this.worldPickerOpen && this.availableWorlds.isEmpty()) {
			String text = this.loadingWorldList ? "Loading worlds..." : "No worlds found";
			int color = this.loadingWorldList ? 0xFFE0C070 : 0xFFDD8888;
			guiGraphics.drawString(this.font, Component.literal(text), menuLeft + 8, menuTop + 56, color);
		}

		if (this.worldPickerOpen && this.availableWorlds.size() > WORLD_BUTTON_LIMIT) {
			guiGraphics.drawString(this.font, Component.literal("Showing first 8 worlds"), menuLeft + 8, menuTop + 196, 0xFFB0B0B0);
		}
	}

	private RenderStats renderRegionMap(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
		int regionCountX = this.maxRegionX - this.minRegionX + 1;
		int regionCountZ = this.maxRegionZ - this.minRegionZ + 1;
		if (regionCountX <= 0 || regionCountZ <= 0) {
			return new RenderStats(0, 0, 0, 1, 1);
		}

		MapTransform transform = this.computeMapTransform(left, top, right, bottom);
		int regionPixel = transform.regionPixel();
		int startX = transform.startX();
		int startZ = transform.startZ();
		if (regionPixel <= 0) {
			return new RenderStats(regionCountX * regionCountZ, this.chunkColorsByRegion.size(), 0, 1, 0);
		}

		int drawStride = 1;
		if (regionPixel <= 2 && this.chunkColorsByRegion.size() > MAX_REGION_CELLS_DRAWN_PER_FRAME) {
			drawStride = Math.max(1, this.chunkColorsByRegion.size() / MAX_REGION_CELLS_DRAWN_PER_FRAME);
		}
		int sampled = 0;
		int drawn = 0;
		int detailRequestsRemaining = MAX_CHUNK_DETAIL_REQUESTS_PER_FRAME;

		for (Map.Entry<Long, int[]> entry : this.chunkColorsByRegion.entrySet()) {
			if (drawStride > 1 && (sampled++ % drawStride) != 0) {
				continue;
			}

			int regionX = unpackRegionX(entry.getKey());
			int regionZ = unpackRegionZ(entry.getKey());
			int rx = regionX - this.minRegionX;
			int rz = regionZ - this.minRegionZ;
			int px = startX + rx * regionPixel;
			int pz = startZ + rz * regionPixel;
			int px2 = px + regionPixel;
			int pz2 = pz + regionPixel;

			if (px2 < left || px > right || pz2 < top || pz > bottom) {
				continue;
			}

			guiGraphics.fill(px, pz, px2, pz2, 0xFF2B2B2B);
			drawn++;
			if (drawStride == 1) {
				detailRequestsRemaining = this.renderChunkCells(guiGraphics, px, pz, regionX, regionZ, regionPixel, entry.getValue(), detailRequestsRemaining);
				renderGridLines(guiGraphics, px, pz, regionPixel);
			}
			if (regionPixel >= 2) {
				guiGraphics.fill(px, pz, px2, pz + 1, 0xFFFFFFFF);
				guiGraphics.fill(px, pz2 - 1, px2, pz2, 0xFFFFFFFF);
				guiGraphics.fill(px, pz, px + 1, pz2, 0xFFFFFFFF);
				guiGraphics.fill(px2 - 1, pz, px2, pz2, 0xFFFFFFFF);
			}
		}

		return new RenderStats(regionCountX * regionCountZ, this.chunkColorsByRegion.size(), drawn, drawStride, regionPixel);
	}

	private int renderChunkCells(
		GuiGraphics guiGraphics,
		int regionLeft,
		int regionTop,
		int regionX,
		int regionZ,
		int regionPixel,
		int[] chunkColors,
		int detailRequestsRemaining
	) {
		int chunkPixel = Math.max(1, regionPixel / CHUNKS_PER_REGION);
		if (regionPixel < 4) {
			return detailRequestsRemaining;
		}

		if (chunkPixel <= 1) {
			int present = 0;
			int totalRed = 0;
			int totalGreen = 0;
			int totalBlue = 0;
			for (int color : chunkColors) {
				if ((color >>> 24) == 0) {
					continue;
				}
				present++;
				totalRed += (color >> 16) & 255;
				totalGreen += (color >> 8) & 255;
				totalBlue += color & 255;
			}
			if (present <= 0) {
				return detailRequestsRemaining;
			}

			int red = totalRed / present;
			int green = totalGreen / present;
			int blue = totalBlue / present;
			int color = 0xFF000000 | (red << 16) | (green << 8) | blue;
			guiGraphics.fill(regionLeft, regionTop, regionLeft + regionPixel, regionTop + regionPixel, color);
			return detailRequestsRemaining;
		}

		boolean allowBlockDetail = chunkPixel >= BLOCKS_PER_CHUNK_EDGE;
		int blockPixel = allowBlockDetail ? Math.max(1, chunkPixel / BLOCKS_PER_CHUNK_EDGE) : 0;

		for (int idx = 0; idx < chunkColors.length; idx++) {
			int color = chunkColors[idx];
			if ((color >>> 24) == 0) {
				continue;
			}
			int chunkX = idx & 31;
			int chunkZ = idx >> 5;
			int globalChunkX = regionX * CHUNKS_PER_REGION + chunkX;
			int globalChunkZ = regionZ * CHUNKS_PER_REGION + chunkZ;
			int x1 = regionLeft + chunkX * chunkPixel;
			int z1 = regionTop + chunkZ * chunkPixel;
			int x2 = Math.min(regionLeft + regionPixel, x1 + chunkPixel);
			int z2 = Math.min(regionTop + regionPixel, z1 + chunkPixel);

			if (allowBlockDetail) {
				long chunkKey = packChunk(globalChunkX, globalChunkZ);
				int[] blockColors = this.blockColorsByChunk.get(chunkKey);
				if (blockColors != null) {
					for (int blockZ = 0; blockZ < BLOCKS_PER_CHUNK_EDGE; blockZ++) {
						for (int blockX = 0; blockX < BLOCKS_PER_CHUNK_EDGE; blockX++) {
							int blockColor = blockColors[blockX + blockZ * BLOCKS_PER_CHUNK_EDGE];
							if ((blockColor >>> 24) == 0) {
								continue;
							}

							int bx1 = x1 + blockX * blockPixel;
							int bz1 = z1 + blockZ * blockPixel;
							int bx2 = Math.min(x2, bx1 + blockPixel);
							int bz2 = Math.min(z2, bz1 + blockPixel);
							guiGraphics.fill(bx1, bz1, bx2, bz2, blockColor);
						}
					}
				} else {
					guiGraphics.fill(x1, z1, x2, z2, color);
					if (detailRequestsRemaining > 0 && this.requestChunkDetailAsync(globalChunkX, globalChunkZ)) {
						detailRequestsRemaining--;
					}
				}
			} else {
				guiGraphics.fill(x1, z1, x2, z2, color);
			}
		}

		return detailRequestsRemaining;
	}

	private void renderGridLines(GuiGraphics guiGraphics, int regionLeft, int regionTop, int regionPixel) {
		int chunkPixel = Math.max(1, regionPixel / CHUNKS_PER_REGION);
		if (chunkPixel < 2) {
			return;
		}

		for (int i = 1; i < CHUNKS_PER_REGION; i++) {
			int x = regionLeft + i * chunkPixel;
			int z = regionTop + i * chunkPixel;
			int color = (i % 8 == 0) ? 0x9096D8FF : 0x504070A0;
			guiGraphics.fill(x, regionTop, x + 1, regionTop + regionPixel, color);
			guiGraphics.fill(regionLeft, z, regionLeft + regionPixel, z + 1, color);
		}
	}

	private boolean requestChunkDetailAsync(int chunkX, int chunkZ) {
		if (this.currentRegionDir == null) {
			return false;
		}

		long chunkKey = packChunk(chunkX, chunkZ);
		if (this.blockColorsByChunk.containsKey(chunkKey)) {
			return false;
		}

		if (this.chunkDetailsInFlight.putIfAbsent(chunkKey, Boolean.TRUE) != null) {
			return false;
		}

		Path regionDir = this.currentRegionDir;
		int requestId = this.activeLoadRequest;
		CompletableFuture.supplyAsync(() -> readChunkDetailColors(regionDir, chunkX, chunkZ), Util.ioPool()).whenComplete((blockColors, throwable) -> {
			this.chunkDetailsInFlight.remove(chunkKey);
			if (requestId != this.activeLoadRequest) {
				return;
			}

			if (throwable != null || blockColors == null) {
				return;
			}

			this.pendingChunkDetailUpdates.add(new ChunkDetailUpdate(chunkKey, blockColors, requestId));
		});
		return true;
	}

	private static int[] readChunkDetailColors(Path regionDir, int chunkX, int chunkZ) {
		int regionX = Math.floorDiv(chunkX, CHUNKS_PER_REGION);
		int regionZ = Math.floorDiv(chunkZ, CHUNKS_PER_REGION);
		Path regionFilePath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
		if (!Files.isRegularFile(regionFilePath)) {
			return null;
		}

		try (RegionFile regionFile = new RegionFile(REGION_EDITOR_STORAGE_INFO, regionFilePath, regionDir, false);
			DataInputStream input = regionFile.getChunkDataInputStream(new ChunkPos(chunkX, chunkZ))) {
			if (input == null) {
				return null;
			}

			CompoundTag chunkTag = NbtIo.read(input);
			return extractChunkTopBlockColors(chunkTag);
		} catch (Exception exception) {
			return null;
		}
	}

	private MapTransform computeMapTransform(int left, int top, int right, int bottom) {
		MapTransform base = this.computeMapTransformWithoutPan(left, top, right, bottom, this.mapZoom);
		return new MapTransform(base.regionPixel(), base.startX() + (int)Math.round(this.mapPanX), base.startZ() + (int)Math.round(this.mapPanZ));
	}

	private MapTransform computeMapTransformWithoutPan(int left, int top, int right, int bottom, float zoom) {
		int regionCountX = this.maxRegionX - this.minRegionX + 1;
		int regionCountZ = this.maxRegionZ - this.minRegionZ + 1;
		int availableWidth = Math.max(1, right - left);
		int availableHeight = Math.max(1, bottom - top);
		int baseRegionPixel = Math.max(1, Math.min(availableWidth / Math.max(1, regionCountX), availableHeight / Math.max(1, regionCountZ)));
		int regionPixel = Math.max(1, Math.round(baseRegionPixel * zoom));
		int worldPixelWidth = regionCountX * regionPixel;
		int worldPixelHeight = regionCountZ * regionPixel;
		int startX = left + (availableWidth - worldPixelWidth) / 2;
		int startZ = top + (availableHeight - worldPixelHeight) / 2;
		return new MapTransform(regionPixel, startX, startZ);
	}

	private void publishBatch(int requestId, Map<Long, int[]> batch, int filesScanned, int regionsLoaded, boolean finalBatch) {
		if (requestId != this.activeLoadRequest) {
			return;
		}

		this.trace(
			"publishBatch(): requestId=" + requestId
				+ " batchSize=" + batch.size()
				+ " filesScanned=" + filesScanned
				+ " regionsLoaded=" + regionsLoaded
				+ " finalBatch=" + finalBatch
				+ " queueBefore=" + this.pendingRegionUpdates.size()
		);
		this.pendingRegionUpdates.add(RegionBatchUpdate.batch(batch, filesScanned, regionsLoaded, finalBatch));
	}

	private void publishProgress(int requestId, int filesScanned, int regionsLoaded, boolean finalProgress) {
		if (requestId != this.activeLoadRequest) {
			return;
		}

		long nowNanos = System.nanoTime();
		if (finalProgress || nowNanos - this.lastProgressLogNanos >= PROGRESS_LOG_INTERVAL_NANOS) {
			this.lastProgressLogNanos = nowNanos;
			this.trace(
				"publishProgress(): requestId=" + requestId
					+ " filesScanned=" + filesScanned
					+ " regionsLoaded=" + regionsLoaded
					+ " finalProgress=" + finalProgress
					+ " queueSize=" + this.pendingRegionUpdates.size()
			);
		}
		this.pendingRegionUpdates.add(RegionBatchUpdate.progress(filesScanned, regionsLoaded, finalProgress));
	}

	private void applyRegionUpdate(RegionBatchUpdate update) {
		this.trace(
			"applyRegionUpdate(): batchSize=" + (update.batch() == null ? 0 : update.batch().size())
				+ " filesScanned=" + update.filesScanned()
				+ " regionsLoaded=" + update.regionsLoaded()
				+ " completed=" + update.completed()
				+ " failed=" + update.failed()
				+ " queueRemaining=" + this.pendingRegionUpdates.size()
		);

		if (update.batch() != null) {
			if (!this.loggedFirstBatchApply) {
				this.loggedFirstBatchApply = true;
				this.trace("applyRegionUpdate(): first region batch applied batchSize=" + update.batch().size());
			}
			for (Map.Entry<Long, int[]> entry : update.batch().entrySet()) {
				long packedRegion = entry.getKey();
				int regionX = unpackRegionX(packedRegion);
				int regionZ = unpackRegionZ(packedRegion);
				this.chunkColorsByRegion.put(packedRegion, entry.getValue());
				this.updateBounds(regionX, regionZ);
			}
		}

		this.loadFilesScanned = Math.max(this.loadFilesScanned, update.filesScanned());
		this.loadRegionsApplied = Math.max(this.loadRegionsApplied, update.regionsLoaded());

		if (update.failed()) {
			this.pendingLoadTask = null;
			this.loadingWorld = false;
			this.loadStatus = "Failed to load world regions.";
			this.traceNanos(
				"applyRegionUpdate(): marked failed filesScanned=" + this.loadFilesScanned + " regionsApplied=" + this.loadRegionsApplied,
				this.worldLoadStartNanos
			);
			return;
		}

		if (update.completed()) {
			this.pendingLoadTask = null;
			this.loadingWorld = false;
			this.loadStatus = this.loadRegionsApplied == 0
				? "Loaded world has no region files."
				: "Loaded " + this.loadRegionsApplied + " regions.";
			LOGGER.info("Region Editor: world load complete for {} ({} regions, {} files scanned)", this.loadedWorldName, this.loadRegionsApplied, this.loadFilesScanned);
			this.traceNanos(
				"applyRegionUpdate(): completed world=" + this.loadedWorldName + " regions=" + this.loadRegionsApplied + " files=" + this.loadFilesScanned,
				this.worldLoadStartNanos
			);
			return;
		}

		this.loadStatus = "Loading world: " + this.loadedWorldName + " (" + this.loadRegionsApplied + " regions, " + this.loadFilesScanned + " files scanned)";
		if (update.logProgress()) {
			LOGGER.info("Region Editor: loading {} progress - {} regions, {} files scanned", this.loadedWorldName, this.loadRegionsApplied, this.loadFilesScanned);
		}
	}

	private void trace(String message) {
		LOGGER.info("Region Editor: {}", message);
		System.out.println("[RegionEditor] " + message);
	}

	private void traceNanos(String message, long startNanos) {
		long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
		this.trace(message + " took=" + (elapsedNanos / 1_000_000L) + "ms");
	}

	private static String renderStatsSuffix(RenderStats renderStats) {
		if (renderStats == null) {
			return "";
		}

		return " totalRegionGrid=" + renderStats.totalRegionGrid()
			+ " loadedRegions=" + renderStats.loadedRegions()
			+ " drawnRegions=" + renderStats.drawnRegions()
			+ " drawStride=" + renderStats.drawStride()
			+ " regionPixel=" + renderStats.regionPixel();
	}

	private void updateBounds(int regionX, int regionZ) {
		if (this.chunkColorsByRegion.size() == 1) {
			this.minRegionX = regionX;
			this.maxRegionX = regionX;
			this.minRegionZ = regionZ;
			this.maxRegionZ = regionZ;
			return;
		}

		this.minRegionX = Math.min(this.minRegionX, regionX);
		this.maxRegionX = Math.max(this.maxRegionX, regionX);
		this.minRegionZ = Math.min(this.minRegionZ, regionZ);
		this.maxRegionZ = Math.max(this.maxRegionZ, regionZ);
	}

	private static int unpackRegionX(long packedRegion) {
		return (int)(packedRegion >> 32);
	}

	private static int unpackRegionZ(long packedRegion) {
		return (int)packedRegion;
	}

	private record RegionBatchUpdate(Map<Long, int[]> batch, int filesScanned, int regionsLoaded, boolean completed, boolean failed, boolean logProgress) {
		private static RegionBatchUpdate batch(Map<Long, int[]> batch, int filesScanned, int regionsLoaded, boolean logProgress) {
			return new RegionBatchUpdate(batch, filesScanned, regionsLoaded, false, false, logProgress);
		}

		private static RegionBatchUpdate progress(int filesScanned, int regionsLoaded, boolean logProgress) {
			return new RegionBatchUpdate(null, filesScanned, regionsLoaded, false, false, logProgress);
		}

		private static RegionBatchUpdate complete(int filesScanned, int regionsLoaded) {
			return new RegionBatchUpdate(null, filesScanned, regionsLoaded, true, false, true);
		}

		private static RegionBatchUpdate failure(int filesScanned, int regionsLoaded) {
			return new RegionBatchUpdate(null, filesScanned, regionsLoaded, false, true, true);
		}
	}

	private record RegionFileResult(long packedRegion, int[] chunkColors) {
	}

	private record ChunkDetailUpdate(long chunkKey, int[] blockColors, int requestId) {
	}

	private record MapTransform(int regionPixel, int startX, int startZ) {
	}

	private record RenderStats(int totalRegionGrid, int loadedRegions, int drawnRegions, int drawStride, int regionPixel) {
	}
}
