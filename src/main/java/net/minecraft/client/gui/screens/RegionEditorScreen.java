package net.minecraft.client.gui.screens;

import com.mojang.serialization.Codec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.blaze3d.platform.NativeImage;
import net.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.system.MemoryUtil;
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
	private static final int MIN_BLOCK_PIXEL_FOR_DETAIL = 2;
	private static final float MIN_MAP_ZOOM = 0.25F;
	private static final float MAX_MAP_ZOOM = 128.0F;
	private static final float MAP_ZOOM_STEP = 1.2F;
	private static final long PROGRESS_LOG_INTERVAL_NANOS = 500_000_000L;
	private static final long RENDER_LOG_INTERVAL_NANOS = 1_000_000_000L;
	private static final long SLOW_FRAME_THRESHOLD_NANOS = 33_000_000L;
	private static final long EXTREME_FRAME_THRESHOLD_NANOS = 250_000_000L;
	private static final int DEFAULT_CHUNK_COLOR = 0xFF4A8F4A;
	private static final int REGION_SELECTION_TINT = 0x66FF8C00;
	private static final int CHUNK_SELECTION_TINT = 0x66FF8C00;
	private static final int PASTE_PREVIEW_FILL = 0x5533D6FF;
	private static final int PASTE_PREVIEW_BORDER = 0xFF7CE8FF;
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
	private Button actionButton;
	private Button loadWorldButton;
	private Button copyButton;
	private Button pasteButton;
	private Button deleteButton;
	private Button undoButton;
	private Button backButton;

	private boolean fileMenuOpen;
	private boolean actionMenuOpen;
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
	private final Map<Long, RegionTexture> regionTextures = new HashMap<>();
	private final Map<Long, int[]> blockColorsByChunk = new HashMap<>();
	private final Set<Long> selectedRegions = new HashSet<>();
	private final Map<Long, BitSet> selectedChunksByRegion = new HashMap<>();
	private ClipboardData clipboard;
	private boolean pastePreviewActive;
	private boolean ctrlChunkDragActive;
	private boolean ctrlChunkDragSelectMode;
	private boolean ctrlChunkDragChunkMode;
	private int ctrlChunkDragAnchorChunkX;
	private int ctrlChunkDragAnchorChunkZ;
	private int ctrlChunkDragCurrentChunkX;
	private int ctrlChunkDragCurrentChunkZ;
	private final Deque<UndoAction> undoStack = new ArrayDeque<>();
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
	private double lastMouseX;
	private double lastMouseY;

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

		this.actionButton = this.addRenderableWidget(
			Button.builder(Component.literal("Action"), button -> this.toggleActionMenu())
				.bounds(62, 8, 60, 20)
				.build()
		);

		this.loadWorldButton = this.addRenderableWidget(
			Button.builder(Component.literal("Load World"), button -> this.openWorldPicker())
				.bounds(12, 32, 120, 20)
				.build()
		);
		this.loadWorldButton.visible = false;
		this.loadWorldButton.active = false;

		this.copyButton = this.addRenderableWidget(
			Button.builder(Component.literal("Copy"), button -> this.copySelectionToClipboard())
				.bounds(66, 32, 120, 20)
				.build()
		);
		this.copyButton.visible = false;
		this.copyButton.active = false;

		this.pasteButton = this.addRenderableWidget(
			Button.builder(Component.literal("Paste"), button -> this.pasteClipboardAtMouse())
				.bounds(66, 54, 120, 20)
				.build()
		);
		this.pasteButton.visible = false;
		this.pasteButton.active = false;

		this.deleteButton = this.addRenderableWidget(
			Button.builder(Component.literal("Delete"), button -> this.deleteSelectedFromDisk())
				.bounds(66, 76, 120, 20)
				.build()
		);
		this.deleteButton.visible = false;
		this.deleteButton.active = false;

		this.undoButton = this.addRenderableWidget(
			Button.builder(Component.literal("Undo"), button -> this.undoLastAction())
				.bounds(66, 98, 120, 20)
				.build()
		);
		this.undoButton.visible = false;
		this.undoButton.active = false;

		this.backButton = this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen))
				.bounds(this.width - 68, 8, 60, 20)
				.build()
		);

		this.rebuildWorldButtons();
	}

	private void toggleFileMenu() {
		this.fileMenuOpen = !this.fileMenuOpen;
		if (this.fileMenuOpen) {
			this.actionMenuOpen = false;
		}
		this.trace("toggleFileMenu(): fileMenuOpen=" + this.fileMenuOpen + " worldPickerOpen=" + this.worldPickerOpen);
		if (!this.fileMenuOpen) {
			this.worldPickerOpen = false;
		}
		this.refreshMenuVisibility();
	}

	private void toggleActionMenu() {
		this.actionMenuOpen = !this.actionMenuOpen;
		if (this.actionMenuOpen) {
			this.fileMenuOpen = false;
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
		this.copyButton.visible = this.actionMenuOpen;
		this.copyButton.active = this.actionMenuOpen && !this.loadingWorld && this.currentRegionDir != null && this.hasSelection();
		this.pasteButton.visible = this.actionMenuOpen;
		this.pasteButton.active = this.actionMenuOpen && !this.loadingWorld && this.currentRegionDir != null && this.clipboard != null;
		this.deleteButton.visible = this.actionMenuOpen;
		this.deleteButton.active = this.actionMenuOpen && !this.loadingWorld && this.hasSelection();
		this.undoButton.visible = this.actionMenuOpen;
		this.undoButton.active = this.actionMenuOpen && !this.loadingWorld && this.currentRegionDir != null && !this.undoStack.isEmpty();

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
		this.clearRegionTextures();
		this.chunkColorsByRegion.clear();
		this.blockColorsByChunk.clear();
		this.selectedRegions.clear();
		this.selectedChunksByRegion.clear();
		this.pastePreviewActive = false;
		this.undoStack.clear();
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
		this.clearPendingRegionUpdates();
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
		this.actionMenuOpen = false;
		this.worldPickerOpen = false;
		this.refreshMenuVisibility();
		this.traceNanos("loadWorld(): queued background work for requestId=" + requestId, startNanos);
	}

	private boolean hasSelection() {
		if (!this.selectedRegions.isEmpty()) {
			return true;
		}

		for (BitSet bitSet : this.selectedChunksByRegion.values()) {
			if (bitSet != null && !bitSet.isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private void deleteSelectedFromDisk() {
		if (this.currentRegionDir == null || !this.hasSelection()) {
			return;
		}

		Path regionDir = this.currentRegionDir;
		Set<Long> regionsToDelete = new HashSet<>(this.selectedRegions);
		Map<Long, BitSet> chunksToDeleteByRegion = new HashMap<>();
		for (Map.Entry<Long, BitSet> entry : this.selectedChunksByRegion.entrySet()) {
			if (entry.getValue() != null && !entry.getValue().isEmpty()) {
				chunksToDeleteByRegion.put(entry.getKey(), (BitSet)entry.getValue().clone());
			}
		}

		this.selectedRegions.clear();
		this.selectedChunksByRegion.clear();
		this.refreshMenuVisibility();

		int deletedRegions = 0;
		int deletedChunks = 0;
		Set<Long> regionsToReload = new HashSet<>();
		Map<Long, UndoChunkSnapshot> undoSnapshotByChunk = new LinkedHashMap<>();

		for (long regionKey : regionsToDelete) {
			int regionX = unpackRegionX(regionKey);
			int regionZ = unpackRegionZ(regionKey);
			captureRegionUndoSnapshot(regionDir, regionX, regionZ, undoSnapshotByChunk);
			Path regionFilePath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
			try {
				if (Files.deleteIfExists(regionFilePath)) {
					deletedRegions++;
				}
			} catch (IOException exception) {
				LOGGER.warn("Region Editor: failed deleting region file {}", regionFilePath, exception);
			}

			this.removeRegionCache(regionKey);
			for (int chunkZ = 0; chunkZ < CHUNKS_PER_REGION; chunkZ++) {
				for (int chunkX = 0; chunkX < CHUNKS_PER_REGION; chunkX++) {
					int globalChunkX = regionX * CHUNKS_PER_REGION + chunkX;
					int globalChunkZ = regionZ * CHUNKS_PER_REGION + chunkZ;
					this.blockColorsByChunk.remove(packChunk(globalChunkX, globalChunkZ));
				}
			}
		}

		for (Map.Entry<Long, BitSet> entry : chunksToDeleteByRegion.entrySet()) {
			long regionKey = entry.getKey();
			if (regionsToDelete.contains(regionKey)) {
				continue;
			}

			int regionX = unpackRegionX(regionKey);
			int regionZ = unpackRegionZ(regionKey);
			Path regionFilePath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
			if (!Files.isRegularFile(regionFilePath)) {
				continue;
			}

			BitSet selected = entry.getValue();
			try (RegionFile regionFile = new RegionFile(REGION_EDITOR_STORAGE_INFO, regionFilePath, regionDir, false)) {
				for (int idx = selected.nextSetBit(0); idx >= 0; idx = selected.nextSetBit(idx + 1)) {
					int chunkX = idx & 31;
					int chunkZ = idx >> 5;
					int globalChunkX = regionX * CHUNKS_PER_REGION + chunkX;
					int globalChunkZ = regionZ * CHUNKS_PER_REGION + chunkZ;
					captureUndoSnapshotForChunk(regionDir, globalChunkX, globalChunkZ, undoSnapshotByChunk);
					ChunkPos chunkPos = new ChunkPos(globalChunkX, globalChunkZ);
					if (regionFile.hasChunk(chunkPos)) {
						regionFile.clear(chunkPos);
						deletedChunks++;
					}
					this.blockColorsByChunk.remove(packChunk(globalChunkX, globalChunkZ));
				}
			} catch (IOException exception) {
				LOGGER.warn("Region Editor: failed deleting selected chunks in {}", regionFilePath, exception);
			}

			regionsToReload.add(regionKey);
		}

		for (long regionKey : regionsToReload) {
			if (regionsToDelete.contains(regionKey)) {
				continue;
			}

			int regionX = unpackRegionX(regionKey);
			int regionZ = unpackRegionZ(regionKey);
			Path regionFilePath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
			if (!Files.isRegularFile(regionFilePath)) {
				this.removeRegionCache(regionKey);
				continue;
			}

			RegionFileResult refreshed = readRegionFile(regionFilePath, regionX, regionZ);
			if (refreshed == null) {
				this.removeRegionCache(regionKey);
			} else {
				this.chunkColorsByRegion.put(regionKey, refreshed.chunkColors());
				this.uploadRegionTexture(regionKey, refreshed.regionImage());
			}
		}

		if (!undoSnapshotByChunk.isEmpty()) {
			this.undoStack.push(new UndoAction("delete", new ArrayList<>(undoSnapshotByChunk.values())));
		}

		this.recomputeBoundsFromLoadedRegions();
		this.loadStatus = "Deleted " + deletedRegions + " regions and " + deletedChunks + " chunks.";
		this.actionMenuOpen = false;
		this.refreshMenuVisibility();
	}

	private void copySelectionToClipboard() {
		if (this.currentRegionDir == null) {
			return;
		}

		Set<Long> selectedChunks = this.collectSelectedChunks();
		if (selectedChunks.isEmpty()) {
			this.loadStatus = "Nothing selected to copy.";
			return;
		}

		boolean regionSnap = this.selectedChunksByRegion.values().stream().allMatch(bitSet -> bitSet == null || bitSet.isEmpty()) && !this.selectedRegions.isEmpty();
		int minChunkX = Integer.MAX_VALUE;
		int minChunkZ = Integer.MAX_VALUE;
		int maxChunkX = Integer.MIN_VALUE;
		int maxChunkZ = Integer.MIN_VALUE;
		for (long chunkKey : selectedChunks) {
			int chunkX = unpackRegionX(chunkKey);
			int chunkZ = unpackRegionZ(chunkKey);
			if (chunkX < minChunkX) minChunkX = chunkX;
			if (chunkX > maxChunkX) maxChunkX = chunkX;
			if (chunkZ < minChunkZ) minChunkZ = chunkZ;
			if (chunkZ > maxChunkZ) maxChunkZ = chunkZ;
		}

		Map<Long, CompoundTag> chunksByRelativeOffset = new HashMap<>();
		for (long chunkKey : selectedChunks) {
			int chunkX = unpackRegionX(chunkKey);
			int chunkZ = unpackRegionZ(chunkKey);
			CompoundTag chunkTag = readChunkTag(this.currentRegionDir, chunkX, chunkZ);
			if (chunkTag == null) {
				continue;
			}

			int relX = chunkX - minChunkX;
			int relZ = chunkZ - minChunkZ;
			chunksByRelativeOffset.put(packChunk(relX, relZ), chunkTag.copy());
		}

		if (chunksByRelativeOffset.isEmpty()) {
			this.loadStatus = "Selection had no readable chunk data to copy.";
			return;
		}

		int widthChunks = (maxChunkX - minChunkX) + 1;
		int heightChunks = (maxChunkZ - minChunkZ) + 1;
		this.clipboard = new ClipboardData(chunksByRelativeOffset, regionSnap, widthChunks, heightChunks, selectedChunks.size());
		this.pastePreviewActive = false;
		String snapMode = regionSnap ? "region" : "chunk";
		this.loadStatus = "Copied " + chunksByRelativeOffset.size() + " chunks. Paste snaps to " + snapMode + " grid.";
		this.actionMenuOpen = false;
		this.refreshMenuVisibility();
	}

	private void pasteClipboardAtMouse() {
		if (this.currentRegionDir == null || this.clipboard == null) {
			return;
		}

		if (!this.pastePreviewActive) {
			this.pastePreviewActive = true;
			this.loadStatus = "Paste preview active. Press Ctrl+V to paste at preview origin.";
			this.refreshMenuVisibility();
			return;
		}

		PasteOrigin pasteOrigin = this.computePasteOrigin(this.lastMouseX, this.lastMouseY, this.clipboard.snapToRegionGrid());
		if (pasteOrigin == null) {
			this.loadStatus = "Move mouse over map to choose a paste origin.";
			return;
		}

		Map<Long, UndoChunkSnapshot> undoSnapshotByChunk = new LinkedHashMap<>();
		Set<Long> touchedRegions = new HashSet<>();
		int pastedChunks = 0;
		for (Map.Entry<Long, CompoundTag> entry : this.clipboard.chunksByRelativeOffset().entrySet()) {
			int relChunkX = unpackRegionX(entry.getKey());
			int relChunkZ = unpackRegionZ(entry.getKey());
			int targetChunkX = pasteOrigin.originChunkX() + relChunkX;
			int targetChunkZ = pasteOrigin.originChunkZ() + relChunkZ;
			captureUndoSnapshotForChunk(this.currentRegionDir, targetChunkX, targetChunkZ, undoSnapshotByChunk);
			CompoundTag targetTag = entry.getValue().copy();
			setChunkCoordinates(targetTag, targetChunkX, targetChunkZ);
			if (writeChunkTag(this.currentRegionDir, targetChunkX, targetChunkZ, targetTag)) {
				pastedChunks++;
				touchedRegions.add(packRegion(Math.floorDiv(targetChunkX, CHUNKS_PER_REGION), Math.floorDiv(targetChunkZ, CHUNKS_PER_REGION)));
				this.blockColorsByChunk.remove(packChunk(targetChunkX, targetChunkZ));
			}
		}

		if (pastedChunks <= 0) {
			this.loadStatus = "Paste failed. No chunks were written.";
			return;
		}

		if (!undoSnapshotByChunk.isEmpty()) {
			this.undoStack.push(new UndoAction("paste", new ArrayList<>(undoSnapshotByChunk.values())));
		}

		this.refreshRegionsFromDisk(touchedRegions);
		this.recomputeBoundsFromLoadedRegions();
		this.pastePreviewActive = false;
		this.loadStatus = "Pasted " + pastedChunks + " chunks at " + pasteOrigin.summary() + ".";
		this.actionMenuOpen = false;
		this.refreshMenuVisibility();
	}

	private void undoLastAction() {
		if (this.currentRegionDir == null || this.undoStack.isEmpty()) {
			return;
		}
		this.pastePreviewActive = false;

		UndoAction undoAction = this.undoStack.pop();
		Set<Long> touchedRegions = new HashSet<>();
		int restored = 0;
		for (UndoChunkSnapshot snapshot : undoAction.snapshots()) {
			boolean ok;
			if (snapshot.hadChunk()) {
				ok = writeChunkTag(this.currentRegionDir, snapshot.chunkX(), snapshot.chunkZ(), snapshot.previousTag().copy());
			} else {
				ok = clearChunkFromDisk(this.currentRegionDir, snapshot.chunkX(), snapshot.chunkZ());
			}

			if (ok) {
				restored++;
				touchedRegions.add(packRegion(Math.floorDiv(snapshot.chunkX(), CHUNKS_PER_REGION), Math.floorDiv(snapshot.chunkZ(), CHUNKS_PER_REGION)));
				this.blockColorsByChunk.remove(packChunk(snapshot.chunkX(), snapshot.chunkZ()));
			}
		}

		this.refreshRegionsFromDisk(touchedRegions);
		this.recomputeBoundsFromLoadedRegions();
		this.loadStatus = "Undo " + undoAction.description() + ": restored " + restored + " chunks.";
		this.refreshMenuVisibility();
	}

	private void refreshRegionsFromDisk(Set<Long> regionKeys) {
		if (this.currentRegionDir == null || regionKeys.isEmpty()) {
			return;
		}

		for (long regionKey : regionKeys) {
			int regionX = unpackRegionX(regionKey);
			int regionZ = unpackRegionZ(regionKey);
			Path regionFilePath = this.currentRegionDir.resolve("r." + regionX + "." + regionZ + ".mca");
			if (!Files.isRegularFile(regionFilePath)) {
				this.removeRegionCache(regionKey);
				continue;
			}

			RegionFileResult refreshed = readRegionFile(regionFilePath, regionX, regionZ);
			if (refreshed == null) {
				this.removeRegionCache(regionKey);
			} else {
				this.chunkColorsByRegion.put(regionKey, refreshed.chunkColors());
				this.uploadRegionTexture(regionKey, refreshed.regionImage());
			}
		}
	}

	private Set<Long> collectSelectedChunks() {
		Set<Long> selectedChunks = new HashSet<>();

		for (long regionKey : this.selectedRegions) {
			int[] regionChunkColors = this.chunkColorsByRegion.get(regionKey);
			if (regionChunkColors == null) {
				continue;
			}

			int regionX = unpackRegionX(regionKey);
			int regionZ = unpackRegionZ(regionKey);
			for (int idx = 0; idx < regionChunkColors.length; idx++) {
				if ((regionChunkColors[idx] >>> 24) == 0) {
					continue;
				}

				int localChunkX = idx & 31;
				int localChunkZ = idx >> 5;
				int chunkX = regionX * CHUNKS_PER_REGION + localChunkX;
				int chunkZ = regionZ * CHUNKS_PER_REGION + localChunkZ;
				selectedChunks.add(packChunk(chunkX, chunkZ));
			}
		}

		for (Map.Entry<Long, BitSet> entry : this.selectedChunksByRegion.entrySet()) {
			long regionKey = entry.getKey();
			BitSet selected = entry.getValue();
			if (selected == null || selected.isEmpty()) {
				continue;
			}

			int[] regionChunkColors = this.chunkColorsByRegion.get(regionKey);
			if (regionChunkColors == null) {
				continue;
			}

			int regionX = unpackRegionX(regionKey);
			int regionZ = unpackRegionZ(regionKey);
			for (int idx = selected.nextSetBit(0); idx >= 0; idx = selected.nextSetBit(idx + 1)) {
				if (idx < 0 || idx >= regionChunkColors.length || (regionChunkColors[idx] >>> 24) == 0) {
					continue;
				}

				int localChunkX = idx & 31;
				int localChunkZ = idx >> 5;
				int chunkX = regionX * CHUNKS_PER_REGION + localChunkX;
				int chunkZ = regionZ * CHUNKS_PER_REGION + localChunkZ;
				selectedChunks.add(packChunk(chunkX, chunkZ));
			}
		}

		return selectedChunks;
	}

	private PasteOrigin computePasteOrigin(double mouseX, double mouseY, boolean snapToRegionGrid) {
		if (!this.isInMapArea(mouseX, mouseY) || this.chunkColorsByRegion.isEmpty()) {
			return null;
		}

		MapTransform transform = this.computeMapTransform(this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
		int regionPixel = transform.regionPixel();
		if (regionPixel <= 0) {
			return null;
		}

		double worldX = mouseX - transform.startX();
		double worldZ = mouseY - transform.startZ();
		if (snapToRegionGrid) {
			int regionGridX = (int)Math.floor(worldX / regionPixel);
			int regionGridZ = (int)Math.floor(worldZ / regionPixel);
			int originRegionX = this.minRegionX + regionGridX;
			int originRegionZ = this.minRegionZ + regionGridZ;
			return new PasteOrigin(
				originRegionX * CHUNKS_PER_REGION,
				originRegionZ * CHUNKS_PER_REGION,
				"region " + originRegionX + "," + originRegionZ
			);
		}

		int chunkGridX = (int)Math.floor(worldX * CHUNKS_PER_REGION / regionPixel);
		int chunkGridZ = (int)Math.floor(worldZ * CHUNKS_PER_REGION / regionPixel);
		int originChunkX = this.minRegionX * CHUNKS_PER_REGION + chunkGridX;
		int originChunkZ = this.minRegionZ * CHUNKS_PER_REGION + chunkGridZ;
		return new PasteOrigin(originChunkX, originChunkZ, "chunk " + originChunkX + "," + originChunkZ);
	}

	private static CompoundTag readChunkTag(Path regionDir, int chunkX, int chunkZ) {
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
			return NbtIo.read(input);
		} catch (Exception exception) {
			return null;
		}
	}

	private static boolean writeChunkTag(Path regionDir, int chunkX, int chunkZ, CompoundTag chunkTag) {
		try {
			Files.createDirectories(regionDir);
			int regionX = Math.floorDiv(chunkX, CHUNKS_PER_REGION);
			int regionZ = Math.floorDiv(chunkZ, CHUNKS_PER_REGION);
			Path regionFilePath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
			try (RegionFile regionFile = new RegionFile(REGION_EDITOR_STORAGE_INFO, regionFilePath, regionDir, false);
				DataOutputStream output = regionFile.getChunkDataOutputStream(new ChunkPos(chunkX, chunkZ))) {
				NbtIo.write(chunkTag, output);
				regionFile.flush();
			}
			return true;
		} catch (Exception exception) {
			LOGGER.warn("Region Editor: failed writing chunk {},{}", chunkX, chunkZ, exception);
			return false;
		}
	}

	private static boolean clearChunkFromDisk(Path regionDir, int chunkX, int chunkZ) {
		try {
			int regionX = Math.floorDiv(chunkX, CHUNKS_PER_REGION);
			int regionZ = Math.floorDiv(chunkZ, CHUNKS_PER_REGION);
			Path regionFilePath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
			if (!Files.isRegularFile(regionFilePath)) {
				return true;
			}

			try (RegionFile regionFile = new RegionFile(REGION_EDITOR_STORAGE_INFO, regionFilePath, regionDir, false)) {
				regionFile.clear(new ChunkPos(chunkX, chunkZ));
				regionFile.flush();
			}
			return true;
		} catch (Exception exception) {
			LOGGER.warn("Region Editor: failed clearing chunk {},{}", chunkX, chunkZ, exception);
			return false;
		}
	}

	private static void setChunkCoordinates(CompoundTag chunkTag, int chunkX, int chunkZ) {
		chunkTag.putInt("xPos", chunkX);
		chunkTag.putInt("zPos", chunkZ);
		if (chunkTag.contains("Level")) {
			CompoundTag level = chunkTag.getCompoundOrEmpty("Level");
			level.putInt("xPos", chunkX);
			level.putInt("zPos", chunkZ);
		}
	}

	private static void captureUndoSnapshotForChunk(Path regionDir, int chunkX, int chunkZ, Map<Long, UndoChunkSnapshot> snapshots) {
		long chunkKey = packChunk(chunkX, chunkZ);
		if (snapshots.containsKey(chunkKey)) {
			return;
		}

		CompoundTag existing = readChunkTag(regionDir, chunkX, chunkZ);
		snapshots.put(chunkKey, new UndoChunkSnapshot(chunkX, chunkZ, existing != null, existing == null ? new CompoundTag() : existing.copy()));
	}

	private static void captureRegionUndoSnapshot(Path regionDir, int regionX, int regionZ, Map<Long, UndoChunkSnapshot> snapshots) {
		Path regionFilePath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
		if (!Files.isRegularFile(regionFilePath)) {
			return;
		}

		try (RegionFile regionFile = new RegionFile(REGION_EDITOR_STORAGE_INFO, regionFilePath, regionDir, false)) {
			for (int chunkZ = 0; chunkZ < CHUNKS_PER_REGION; chunkZ++) {
				for (int chunkX = 0; chunkX < CHUNKS_PER_REGION; chunkX++) {
					int globalChunkX = regionX * CHUNKS_PER_REGION + chunkX;
					int globalChunkZ = regionZ * CHUNKS_PER_REGION + chunkZ;
					ChunkPos chunkPos = new ChunkPos(globalChunkX, globalChunkZ);
					if (!regionFile.hasChunk(chunkPos)) {
						continue;
					}

					long chunkKey = packChunk(globalChunkX, globalChunkZ);
					if (snapshots.containsKey(chunkKey)) {
						continue;
					}

					try (DataInputStream input = regionFile.getChunkDataInputStream(chunkPos)) {
						if (input == null) {
							continue;
						}
						CompoundTag existing = NbtIo.read(input);
						snapshots.put(chunkKey, new UndoChunkSnapshot(globalChunkX, globalChunkZ, true, existing == null ? new CompoundTag() : existing));
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception exception) {
			LOGGER.warn("Region Editor: failed capturing undo snapshot for region {},{}", regionX, regionZ, exception);
		}
	}

	private void removeRegionCache(long regionKey) {
		this.chunkColorsByRegion.remove(regionKey);
		RegionTexture existing = this.regionTextures.remove(regionKey);
		if (existing != null && this.minecraft != null) {
			this.minecraft.getTextureManager().release(existing.location());
		}
	}

	private void recomputeBoundsFromLoadedRegions() {
		if (this.chunkColorsByRegion.isEmpty()) {
			this.minRegionX = 0;
			this.maxRegionX = 0;
			this.minRegionZ = 0;
			this.maxRegionZ = 0;
			return;
		}

		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (long regionKey : this.chunkColorsByRegion.keySet()) {
			int regionX = unpackRegionX(regionKey);
			int regionZ = unpackRegionZ(regionKey);
			if (regionX < minX) minX = regionX;
			if (regionX > maxX) maxX = regionX;
			if (regionZ < minZ) minZ = regionZ;
			if (regionZ > maxZ) maxZ = regionZ;
		}

		this.minRegionX = minX;
		this.maxRegionX = maxX;
		this.minRegionZ = minZ;
		this.maxRegionZ = maxZ;
	}

	private void streamRegionData(Path regionDir, int requestId) throws IOException {
		long startNanos = System.nanoTime();
		this.trace("streamRegionData(): requestId=" + requestId + " dir=" + regionDir);
		if (!Files.isDirectory(regionDir)) {
			this.trace("streamRegionData(): no region directory for requestId=" + requestId);
			return;
		}

		Map<Long, RegionFileResult> batch = new LinkedHashMap<>();
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
							batch.put(result.packedRegion(), result);
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
					batch.put(result.packedRegion(), result);
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
		NativeImage regionImage = new NativeImage(BLOCKS_PER_REGION_EDGE, BLOCKS_PER_REGION_EDGE, true);
		IntBuffer regionPixels = MemoryUtil.memIntBuffer(regionImage.pixels, BLOCKS_PER_REGION_EDGE * BLOCKS_PER_REGION_EDGE);
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
						fillChunkPixels(regionPixels, chunkLocalX, chunkLocalZ, null, DEFAULT_CHUNK_COLOR);
						continue;
					}

					CompoundTag chunkTag = maybeDataFixChunkTag(NbtIo.read(chunkInput));
					int[] blockColors = extractChunkTopBlockColors(chunkTag);
					int chunkColor = averageChunkTopColor(blockColors);
					chunkColors[idx] = chunkColor;
					fillChunkPixels(regionPixels, chunkLocalX, chunkLocalZ, blockColors, chunkColor);
				} catch (Exception exception) {
					chunkColors[idx] = DEFAULT_CHUNK_COLOR;
					fillChunkPixels(regionPixels, chunkLocalX, chunkLocalZ, null, DEFAULT_CHUNK_COLOR);
					LOGGER.debug("Region Editor: failed to decode chunk color for {}", chunkPos, exception);
				}
			}
		} catch (Exception exception) {
			LOGGER.warn("Region Editor: failed to decode region {}, using fallback occupancy colors", regionFile.getFileName(), exception);
			for (int idx = chunkPresence.nextSetBit(0); idx >= 0; idx = chunkPresence.nextSetBit(idx + 1)) {
				hasAnyChunk = true;
				int chunkLocalX = idx & 31;
				int chunkLocalZ = idx >> 5;
				chunkColors[idx] = DEFAULT_CHUNK_COLOR;
				fillChunkPixels(regionPixels, chunkLocalX, chunkLocalZ, null, DEFAULT_CHUNK_COLOR);
			}
		}

		if (!hasAnyChunk) {
			regionImage.close();
			return null;
		}

		long elapsedNanos = System.nanoTime() - startNanos;
		if (elapsedNanos >= 5_000_000L) {
			LOGGER.info("Region Editor: readRegionFile slow path file={} took={}ms", regionFile.getFileName(), elapsedNanos / 1_000_000L);
			System.out.println("[RegionEditor] readRegionFile slow path file=" + regionFile.getFileName() + " took=" + (elapsedNanos / 1_000_000L) + "ms");
		}
		return new RegionFileResult(packRegion(regionX, regionZ), chunkColors, regionImage);
	}

	private static int extractChunkTopColor(CompoundTag chunkTag) {
		int[] blockColors = extractChunkTopBlockColors(chunkTag);
		return averageChunkTopColor(blockColors);
	}

	private static int averageChunkTopColor(int[] blockColors) {
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

	private static void fillChunkPixels(IntBuffer regionPixels, int chunkLocalX, int chunkLocalZ, int[] blockColors, int fallbackColor) {
		int baseX = chunkLocalX * BLOCKS_PER_CHUNK_EDGE;
		int baseZ = chunkLocalZ * BLOCKS_PER_CHUNK_EDGE;
		for (int localZ = 0; localZ < BLOCKS_PER_CHUNK_EDGE; localZ++) {
			int rowStart = (baseZ + localZ) * BLOCKS_PER_REGION_EDGE + baseX;
			for (int localX = 0; localX < BLOCKS_PER_CHUNK_EDGE; localX++) {
				int argb = blockColors != null ? blockColors[localX + localZ * BLOCKS_PER_CHUNK_EDGE] : fallbackColor;
				if (argb == 0) {
					argb = fallbackColor;
				}
				regionPixels.put(rowStart + localX, ARGB.toABGR(argb));
			}
		}
	}

	private static int[] extractChunkTopBlockColors(CompoundTag chunkTag) {
		CompoundTag root = chunkTag.contains("Level") ? chunkTag.getCompoundOrEmpty("Level") : chunkTag;
		Map<Integer, PalettedContainer<BlockState>> sectionsByY = decodeSections(root);
		if (sectionsByY.isEmpty()) {
			return extractChunkTopBlockColorsLegacy(root);
		}

		int[] blockColors = new int[BLOCKS_PER_CHUNK_EDGE * BLOCKS_PER_CHUNK_EDGE];
		boolean[] unresolved = new boolean[BLOCKS_PER_CHUNK_EDGE * BLOCKS_PER_CHUNK_EDGE];
		int unresolvedCount = unresolved.length;
		for (int i = 0; i < unresolved.length; i++) {
			unresolved[i] = true;
		}

		int sectionMax = sectionsByY.keySet().stream().max(Integer::compareTo).orElse(FALLBACK_MIN_Y / 16);
		int sectionMin = sectionsByY.keySet().stream().min(Integer::compareTo).orElse(sectionMax);
		for (int sectionY = sectionMax; sectionY >= sectionMin && unresolvedCount > 0; sectionY--) {
			PalettedContainer<BlockState> section = sectionsByY.get(sectionY);
			if (section == null) {
				continue;
			}

			for (int yInSection = 15; yInSection >= 0 && unresolvedCount > 0; yInSection--) {
				for (int localZ = 0; localZ < BLOCKS_PER_CHUNK_EDGE; localZ++) {
					for (int localX = 0; localX < BLOCKS_PER_CHUNK_EDGE; localX++) {
						int index = localX + localZ * BLOCKS_PER_CHUNK_EDGE;
						if (!unresolved[index]) {
							continue;
						}

						BlockState state = section.get(localX & 15, yInSection, localZ & 15);
						if (!state.isAir()) {
							blockColors[index] = colorForBlockState(state);
							unresolved[index] = false;
							unresolvedCount--;
						}
					}
				}
			}
		}

		if (unresolvedCount > 0) {
			int[] legacyFallback = extractChunkTopBlockColorsLegacy(root);
			if (legacyFallback != null) {
				for (int i = 0; i < unresolved.length; i++) {
					if (unresolved[i]) {
						blockColors[i] = legacyFallback[i];
					}
				}
			}
		}

		return blockColors;
	}

	private static int[] extractChunkTopBlockColorsLegacy(CompoundTag chunkRoot) {
		Map<Integer, LegacySectionData> sectionsByY = new HashMap<>();
		for (CompoundTag sectionTag : streamSectionTags(chunkRoot)) {
			int sectionY = sectionTag.getByteOr("Y", (byte)0);
			List<CompoundTag> paletteTags = sectionTag.getListOrEmpty("Palette").compoundStream().toList();
			if (paletteTags.isEmpty()) {
				continue;
			}

			BlockState[] palette = new BlockState[paletteTags.size()];
			for (int i = 0; i < paletteTags.size(); i++) {
				palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletteTags.get(i));
			}

			long[] packed = sectionTag.getLongArray("BlockStates").orElse(null);
			SimpleBitStorage storage = null;
			if (packed != null && packed.length > 0) {
				int bits = Math.max(4, Mth.ceillog2(Math.max(1, palette.length)));
				try {
					storage = new SimpleBitStorage(bits, 4096, packed);
				} catch (Exception ignored) {
					storage = null;
				}
			}

			sectionsByY.put(sectionY, new LegacySectionData(palette, storage));
		}

		if (sectionsByY.isEmpty()) {
			return null;
		}

		int[] blockColors = new int[BLOCKS_PER_CHUNK_EDGE * BLOCKS_PER_CHUNK_EDGE];
		boolean[] unresolved = new boolean[BLOCKS_PER_CHUNK_EDGE * BLOCKS_PER_CHUNK_EDGE];
		int unresolvedCount = unresolved.length;
		for (int i = 0; i < unresolved.length; i++) {
			unresolved[i] = true;
		}

		int sectionMax = sectionsByY.keySet().stream().max(Integer::compareTo).orElse(FALLBACK_MIN_Y / 16);
		int sectionMin = sectionsByY.keySet().stream().min(Integer::compareTo).orElse(sectionMax);
		for (int sectionY = sectionMax; sectionY >= sectionMin && unresolvedCount > 0; sectionY--) {
			LegacySectionData section = sectionsByY.get(sectionY);
			if (section == null) {
				continue;
			}

			for (int yInSection = 15; yInSection >= 0 && unresolvedCount > 0; yInSection--) {
				for (int localZ = 0; localZ < BLOCKS_PER_CHUNK_EDGE; localZ++) {
					for (int localX = 0; localX < BLOCKS_PER_CHUNK_EDGE; localX++) {
						int index = localX + localZ * BLOCKS_PER_CHUNK_EDGE;
						if (!unresolved[index]) {
							continue;
						}

						BlockState state = readLegacyState(section, localX, yInSection, localZ);
						if (!state.isAir()) {
							blockColors[index] = colorForBlockState(state);
							unresolved[index] = false;
							unresolvedCount--;
						}
					}
				}
			}
		}

		return blockColors;
	}

	private static List<CompoundTag> streamSectionTags(CompoundTag chunkRoot) {
		List<CompoundTag> lower = chunkRoot.getListOrEmpty("sections").compoundStream().toList();
		if (!lower.isEmpty()) {
			return lower;
		}

		return chunkRoot.getListOrEmpty("Sections").compoundStream().toList();
	}

	private static BlockState readLegacyState(LegacySectionData section, int localX, int yInSection, int localZ) {
		if (section.palette.length == 0) {
			return AIR_BLOCK_STATE;
		}

		if (section.storage == null) {
			return section.palette[0];
		}

		int packedIndex = (yInSection << 8) | (localZ << 4) | localX;
		int paletteIndex = section.storage.get(packedIndex);
		if (paletteIndex < 0 || paletteIndex >= section.palette.length) {
			return AIR_BLOCK_STATE;
		}

		return section.palette[paletteIndex];
	}

	private static Map<Integer, PalettedContainer<BlockState>> decodeSections(CompoundTag chunkRoot) {
		Map<Integer, PalettedContainer<BlockState>> sectionsByY = new HashMap<>();
		for (CompoundTag sectionTag : streamSectionTags(chunkRoot)) {
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

	private static CompoundTag maybeDataFixChunkTag(CompoundTag chunkTag) {
		int dataVersion = NbtUtils.getDataVersion(chunkTag, -1);
		int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
		if (dataVersion == currentVersion) {
			return chunkTag;
		}

		try {
			CompoundTag working = chunkTag.copy();
			if (dataVersion < 1493) {
				working = DataFixTypes.CHUNK.update(DataFixers.getDataFixer(), working, dataVersion, 1493);
			}

			ChunkStorage.injectDatafixingContext(working, Level.OVERWORLD, Optional.empty());
			working = DataFixTypes.CHUNK.updateToCurrentVersion(DataFixers.getDataFixer(), working, Math.max(1493, dataVersion));
			working.remove("__context");
			NbtUtils.addCurrentDataVersion(working);
			return working;
		} catch (Exception exception) {
			LOGGER.debug("Region Editor: chunk datafix failed, using raw chunk data", exception);
			return chunkTag;
		}
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
		if (rgb == 0) {
			rgb = deriveFallbackColor(state);
		}

		return 0xFF000000 | rgb;
	}

	private static int deriveFallbackColor(BlockState state) {
		// Some modded blocks report MapColor.NONE (0). Use a stable per-block fallback
		// so we still render top-of-column detail instead of collapsing to flat chunk color.
		ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		int hash = key == null ? state.getBlock().hashCode() : key.hashCode();
		int r = 64 + ((hash >> 16) & 0x7F);
		int g = 64 + ((hash >> 8) & 0x7F);
		int b = 64 + (hash & 0x7F);
		return (r << 16) | (g << 8) | b;
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
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		double mouseX = mouseButtonEvent.x();
		double mouseY = mouseButtonEvent.y();
		int button = mouseButtonEvent.button();
		if (this.fileMenuOpen || this.chunkColorsByRegion.isEmpty() || !this.isInMapArea(mouseX, mouseY)) {
			return super.mouseClicked(mouseButtonEvent, bl);
		}

		if (this.minecraft != null && this.minecraft.hasControlDown() && (button == 0 || button == 1)) {
			this.ctrlChunkDragChunkMode = this.isChunkSelectionMode();
			GridCoord startCoord = this.ctrlChunkDragChunkMode ? this.screenToChunk(mouseX, mouseY) : this.screenToRegion(mouseX, mouseY);
			if (startCoord != null) {
				this.ctrlChunkDragActive = true;
				this.ctrlChunkDragSelectMode = button == 0;
				this.ctrlChunkDragAnchorChunkX = startCoord.x();
				this.ctrlChunkDragAnchorChunkZ = startCoord.z();
				this.ctrlChunkDragCurrentChunkX = startCoord.x();
				this.ctrlChunkDragCurrentChunkZ = startCoord.z();
				if (this.ctrlChunkDragChunkMode) {
					this.applyChunkBoxSelection(
						this.ctrlChunkDragAnchorChunkX,
						this.ctrlChunkDragAnchorChunkZ,
						this.ctrlChunkDragCurrentChunkX,
						this.ctrlChunkDragCurrentChunkZ,
						this.ctrlChunkDragSelectMode
					);
				} else {
					this.applyRegionBoxSelection(
						this.ctrlChunkDragAnchorChunkX,
						this.ctrlChunkDragAnchorChunkZ,
						this.ctrlChunkDragCurrentChunkX,
						this.ctrlChunkDragCurrentChunkZ,
						this.ctrlChunkDragSelectMode
					);
				}
				this.refreshMenuVisibility();
				return true;
			}
		}

		if (button == 0) {
			if (this.updateSelectionAt(mouseX, mouseY, true)) {
				this.refreshMenuVisibility();
				return true;
			}
		} else if (button == 1) {
			if (this.updateSelectionAt(mouseX, mouseY, false)) {
				this.refreshMenuVisibility();
				return true;
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	private boolean updateSelectionAt(double mouseX, double mouseY, boolean select) {
		MapTransform transform = this.computeMapTransform(this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
		int regionPixel = transform.regionPixel();
		if (regionPixel <= 0) {
			return false;
		}

		int regionCountX = this.maxRegionX - this.minRegionX + 1;
		int regionCountZ = this.maxRegionZ - this.minRegionZ + 1;
		if (regionCountX <= 0 || regionCountZ <= 0) {
			return false;
		}

		double worldX = mouseX - transform.startX();
		double worldZ = mouseY - transform.startZ();
		int regionGridX = (int)Math.floor(worldX / regionPixel);
		int regionGridZ = (int)Math.floor(worldZ / regionPixel);
		if (regionGridX < 0 || regionGridZ < 0 || regionGridX >= regionCountX || regionGridZ >= regionCountZ) {
			return false;
		}

		int regionX = this.minRegionX + regionGridX;
		int regionZ = this.minRegionZ + regionGridZ;
		long regionKey = packRegion(regionX, regionZ);
		if (!this.chunkColorsByRegion.containsKey(regionKey)) {
			return false;
		}

		double chunkPixel = regionPixel / (double)CHUNKS_PER_REGION;
		if (chunkPixel >= 2.0) {
			double localRegionX = worldX - regionGridX * regionPixel;
			double localRegionZ = worldZ - regionGridZ * regionPixel;
			int chunkLocalX = Mth.clamp((int)Math.floor(localRegionX * CHUNKS_PER_REGION / regionPixel), 0, CHUNKS_PER_REGION - 1);
			int chunkLocalZ = Mth.clamp((int)Math.floor(localRegionZ * CHUNKS_PER_REGION / regionPixel), 0, CHUNKS_PER_REGION - 1);
			int idx = chunkLocalX + chunkLocalZ * CHUNKS_PER_REGION;
			int[] regionChunkColors = this.chunkColorsByRegion.get(regionKey);
			if (regionChunkColors == null || (regionChunkColors[idx] >>> 24) == 0) {
				return false;
			}

			if (select) {
				BitSet selected = this.selectedChunksByRegion.computeIfAbsent(regionKey, key -> new BitSet(CHUNK_COUNT_PER_REGION));
				selected.set(idx);
			} else {
				BitSet selected = this.selectedChunksByRegion.get(regionKey);
				if (selected != null) {
					selected.clear(idx);
					if (selected.isEmpty()) {
						this.selectedChunksByRegion.remove(regionKey);
					}
				}
			}
			return true;
		}

		if (select) {
			this.selectedRegions.add(regionKey);
		} else {
			this.selectedRegions.remove(regionKey);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (keyEvent.hasControlDown()) {
			if (keyEvent.key() == 67) {
				this.copySelectionToClipboard();
				return true;
			}
			if (keyEvent.key() == 86) {
				this.pasteClipboardAtMouse();
				return true;
			}
			if (keyEvent.key() == 90) {
				this.undoLastAction();
				return true;
			}
		}

		return super.keyPressed(keyEvent);
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
		if (this.ctrlChunkDragActive && (mouseButtonEvent.button() == 0 || mouseButtonEvent.button() == 1)) {
			GridCoord dragCoord = this.ctrlChunkDragChunkMode
				? this.screenToChunk(mouseButtonEvent.x(), mouseButtonEvent.y())
				: this.screenToRegion(mouseButtonEvent.x(), mouseButtonEvent.y());
			if (dragCoord != null && (dragCoord.x() != this.ctrlChunkDragCurrentChunkX || dragCoord.z() != this.ctrlChunkDragCurrentChunkZ)) {
				this.ctrlChunkDragCurrentChunkX = dragCoord.x();
				this.ctrlChunkDragCurrentChunkZ = dragCoord.z();
				if (this.ctrlChunkDragChunkMode) {
					this.applyChunkBoxSelection(
						this.ctrlChunkDragAnchorChunkX,
						this.ctrlChunkDragAnchorChunkZ,
						this.ctrlChunkDragCurrentChunkX,
						this.ctrlChunkDragCurrentChunkZ,
						this.ctrlChunkDragSelectMode
					);
				} else {
					this.applyRegionBoxSelection(
						this.ctrlChunkDragAnchorChunkX,
						this.ctrlChunkDragAnchorChunkZ,
						this.ctrlChunkDragCurrentChunkX,
						this.ctrlChunkDragCurrentChunkZ,
						this.ctrlChunkDragSelectMode
					);
				}
				this.refreshMenuVisibility();
			}
			return true;
		}

		if (mouseButtonEvent.button() == 0 && this.isInMapArea(mouseButtonEvent.x(), mouseButtonEvent.y()) && !this.fileMenuOpen) {
			this.mapPanX += deltaX;
			this.mapPanZ += deltaY;
			return true;
		}

		return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		if (this.ctrlChunkDragActive && (mouseButtonEvent.button() == 0 || mouseButtonEvent.button() == 1)) {
			this.ctrlChunkDragActive = false;
			this.refreshMenuVisibility();
			return true;
		}

		return super.mouseReleased(mouseButtonEvent);
	}

	private ChunkGridCoord screenToChunk(double mouseX, double mouseY) {
		if (!this.isInMapArea(mouseX, mouseY)) {
			return null;
		}

		MapTransform transform = this.computeMapTransform(this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
		int regionPixel = transform.regionPixel();
		if (regionPixel <= 0) {
			return null;
		}

		int regionCountX = this.maxRegionX - this.minRegionX + 1;
		int regionCountZ = this.maxRegionZ - this.minRegionZ + 1;
		if (regionCountX <= 0 || regionCountZ <= 0) {
			return null;
		}

		double worldX = mouseX - transform.startX();
		double worldZ = mouseY - transform.startZ();
		int chunkGridX = (int)Math.floor(worldX * CHUNKS_PER_REGION / regionPixel);
		int chunkGridZ = (int)Math.floor(worldZ * CHUNKS_PER_REGION / regionPixel);
		int maxChunkGridX = regionCountX * CHUNKS_PER_REGION - 1;
		int maxChunkGridZ = regionCountZ * CHUNKS_PER_REGION - 1;
		if (chunkGridX < 0 || chunkGridZ < 0 || chunkGridX > maxChunkGridX || chunkGridZ > maxChunkGridZ) {
			return null;
		}

		int chunkX = this.minRegionX * CHUNKS_PER_REGION + chunkGridX;
		int chunkZ = this.minRegionZ * CHUNKS_PER_REGION + chunkGridZ;
		return new ChunkGridCoord(chunkX, chunkZ);
	}

	private RegionGridCoord screenToRegion(double mouseX, double mouseY) {
		if (!this.isInMapArea(mouseX, mouseY)) {
			return null;
		}

		MapTransform transform = this.computeMapTransform(this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
		int regionPixel = transform.regionPixel();
		if (regionPixel <= 0) {
			return null;
		}

		int regionCountX = this.maxRegionX - this.minRegionX + 1;
		int regionCountZ = this.maxRegionZ - this.minRegionZ + 1;
		if (regionCountX <= 0 || regionCountZ <= 0) {
			return null;
		}

		double worldX = mouseX - transform.startX();
		double worldZ = mouseY - transform.startZ();
		int regionGridX = (int)Math.floor(worldX / regionPixel);
		int regionGridZ = (int)Math.floor(worldZ / regionPixel);
		if (regionGridX < 0 || regionGridZ < 0 || regionGridX >= regionCountX || regionGridZ >= regionCountZ) {
			return null;
		}

		int regionX = this.minRegionX + regionGridX;
		int regionZ = this.minRegionZ + regionGridZ;
		if (!this.chunkColorsByRegion.containsKey(packRegion(regionX, regionZ))) {
			return null;
		}

		return new RegionGridCoord(regionX, regionZ);
	}

	private boolean isChunkSelectionMode() {
		MapTransform transform = this.computeMapTransform(this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
		int regionPixel = transform.regionPixel();
		if (regionPixel <= 0) {
			return false;
		}

		double chunkPixel = regionPixel / (double)CHUNKS_PER_REGION;
		return chunkPixel >= 2.0;
	}

	private void applyChunkBoxSelection(int chunkX1, int chunkZ1, int chunkX2, int chunkZ2, boolean select) {
		int minChunkX = Math.min(chunkX1, chunkX2);
		int maxChunkX = Math.max(chunkX1, chunkX2);
		int minChunkZ = Math.min(chunkZ1, chunkZ2);
		int maxChunkZ = Math.max(chunkZ1, chunkZ2);

		for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				int regionX = Math.floorDiv(chunkX, CHUNKS_PER_REGION);
				int regionZ = Math.floorDiv(chunkZ, CHUNKS_PER_REGION);
				long regionKey = packRegion(regionX, regionZ);
				int[] regionChunkColors = this.chunkColorsByRegion.get(regionKey);
				if (regionChunkColors == null) {
					continue;
				}

				int localChunkX = Math.floorMod(chunkX, CHUNKS_PER_REGION);
				int localChunkZ = Math.floorMod(chunkZ, CHUNKS_PER_REGION);
				int idx = localChunkX + localChunkZ * CHUNKS_PER_REGION;
				if ((regionChunkColors[idx] >>> 24) == 0) {
					continue;
				}

				if (select) {
					this.selectedRegions.remove(regionKey);
					BitSet selected = this.selectedChunksByRegion.computeIfAbsent(regionKey, key -> new BitSet(CHUNK_COUNT_PER_REGION));
					selected.set(idx);
				} else {
					this.selectedRegions.remove(regionKey);
					BitSet selected = this.selectedChunksByRegion.get(regionKey);
					if (selected != null) {
						selected.clear(idx);
						if (selected.isEmpty()) {
							this.selectedChunksByRegion.remove(regionKey);
						}
					}
				}
			}
		}
	}

	private void applyRegionBoxSelection(int regionX1, int regionZ1, int regionX2, int regionZ2, boolean select) {
		int minRegionX = Math.min(regionX1, regionX2);
		int maxRegionX = Math.max(regionX1, regionX2);
		int minRegionZ = Math.min(regionZ1, regionZ2);
		int maxRegionZ = Math.max(regionZ1, regionZ2);

		for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
			for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
				long regionKey = packRegion(regionX, regionZ);
				if (!this.chunkColorsByRegion.containsKey(regionKey)) {
					continue;
				}

				if (select) {
					this.selectedRegions.add(regionKey);
				} else {
					this.selectedRegions.remove(regionKey);
				}
			}
		}
	}

	private boolean isInMapArea(double x, double y) {
		return x >= this.mapInnerLeft && x <= this.mapInnerRight && y >= this.mapInnerTop && y <= this.mapInnerBottom;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;
		long renderStartNanos = System.nanoTime();
		this.renderFrameCounter++;

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

		if (this.actionMenuOpen) {
			this.renderActionMenu(guiGraphics);
		}

		if (this.pastePreviewActive && this.clipboard != null) {
			this.renderPastePreview(guiGraphics, this.mapInnerLeft, this.mapInnerTop, this.mapInnerRight, this.mapInnerBottom);
			guiGraphics.drawString(this.font, Component.literal("Paste Preview: press Ctrl+V to paste"), mapLeft + 8, mapTop + 32, 0xFFBEEFFF);
		}

		String dragTip = "Tip: Ctrl+drag selects box (L add, R remove)";
		int tipX = this.width - 8 - this.font.width(dragTip);
		guiGraphics.drawString(this.font, Component.literal(dragTip), tipX, 32, 0xFF9FD2E8);

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

	private void renderActionMenu(GuiGraphics guiGraphics) {
		int menuLeft = 62;
		int menuTop = 30;
		int menuWidth = 134;
		int menuHeight = 92;
		guiGraphics.fill(menuLeft, menuTop, menuLeft + menuWidth, menuTop + menuHeight, 0xF0202020);
		guiGraphics.fill(menuLeft + 1, menuTop + 1, menuLeft + menuWidth - 1, menuTop + menuHeight - 1, 0xF0323232);
	}

	private void renderPastePreview(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
		if (this.clipboard == null || this.chunkColorsByRegion.isEmpty()) {
			return;
		}

		PasteOrigin origin = this.computePasteOrigin(this.lastMouseX, this.lastMouseY, this.clipboard.snapToRegionGrid());
		if (origin == null) {
			return;
		}

		MapTransform transform = this.computeMapTransform(left, top, right, bottom);
		int regionPixel = transform.regionPixel();
		if (regionPixel <= 0) {
			return;
		}

		int minChunkX = this.minRegionX * CHUNKS_PER_REGION;
		int minChunkZ = this.minRegionZ * CHUNKS_PER_REGION;
		double chunkPixel = regionPixel / (double)CHUNKS_PER_REGION;

		double relChunkX1 = origin.originChunkX() - minChunkX;
		double relChunkZ1 = origin.originChunkZ() - minChunkZ;
		double relChunkX2 = relChunkX1 + this.clipboard.widthChunks();
		double relChunkZ2 = relChunkZ1 + this.clipboard.heightChunks();

		int x1 = transform.startX() + (int)Math.floor(relChunkX1 * chunkPixel);
		int z1 = transform.startZ() + (int)Math.floor(relChunkZ1 * chunkPixel);
		int x2 = transform.startX() + (int)Math.ceil(relChunkX2 * chunkPixel);
		int z2 = transform.startZ() + (int)Math.ceil(relChunkZ2 * chunkPixel);

		guiGraphics.enableScissor(left, top, right, bottom);
		try {
			guiGraphics.fill(x1, z1, x2, z2, PASTE_PREVIEW_FILL);
			guiGraphics.fill(x1, z1, x2, z1 + 1, PASTE_PREVIEW_BORDER);
			guiGraphics.fill(x1, z2 - 1, x2, z2, PASTE_PREVIEW_BORDER);
			guiGraphics.fill(x1, z1, x1 + 1, z2, PASTE_PREVIEW_BORDER);
			guiGraphics.fill(x2 - 1, z1, x2, z2, PASTE_PREVIEW_BORDER);
		} finally {
			guiGraphics.disableScissor();
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
		int drawn = 0;

		guiGraphics.enableScissor(left, top, right, bottom);
		try {
			for (Map.Entry<Long, int[]> entry : this.chunkColorsByRegion.entrySet()) {
				long regionKey = entry.getKey();

				int regionX = unpackRegionX(regionKey);
				int regionZ = unpackRegionZ(regionKey);
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
				RegionTexture regionTexture = this.regionTextures.get(regionKey);
				if (regionTexture != null) {
					guiGraphics.blit(
						RenderPipelines.GUI_TEXTURED,
						regionTexture.location(),
						px,
						pz,
						0.0F,
						0.0F,
						regionPixel,
						regionPixel,
						BLOCKS_PER_REGION_EDGE,
						BLOCKS_PER_REGION_EDGE,
						BLOCKS_PER_REGION_EDGE,
						BLOCKS_PER_REGION_EDGE
					);
				}

				if (this.selectedRegions.contains(regionKey)) {
					guiGraphics.fill(px, pz, px2, pz2, REGION_SELECTION_TINT);
				}

				BitSet selectedChunks = this.selectedChunksByRegion.get(regionKey);
				if (selectedChunks != null && !selectedChunks.isEmpty() && regionPixel >= 2) {
					for (int idx = selectedChunks.nextSetBit(0); idx >= 0; idx = selectedChunks.nextSetBit(idx + 1)) {
						int chunkX = idx & 31;
						int chunkZ = idx >> 5;
						int cx1 = px + (chunkX * regionPixel) / CHUNKS_PER_REGION;
						int cz1 = pz + (chunkZ * regionPixel) / CHUNKS_PER_REGION;
						int cx2 = px + ((chunkX + 1) * regionPixel) / CHUNKS_PER_REGION;
						int cz2 = pz + ((chunkZ + 1) * regionPixel) / CHUNKS_PER_REGION;
						guiGraphics.fill(cx1, cz1, cx2, cz2, CHUNK_SELECTION_TINT);
					}
				}

				drawn++;
				renderGridLines(guiGraphics, px, pz, regionPixel);
				if (regionPixel >= 2) {
					guiGraphics.fill(px, pz, px2, pz + 1, 0xFFFFFFFF);
					guiGraphics.fill(px, pz2 - 1, px2, pz2, 0xFFFFFFFF);
					guiGraphics.fill(px, pz, px + 1, pz2, 0xFFFFFFFF);
					guiGraphics.fill(px2 - 1, pz, px2, pz2, 0xFFFFFFFF);
				}
			}
		} finally {
			guiGraphics.disableScissor();
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

		for (int idx = 0; idx < chunkColors.length; idx++) {
			int color = chunkColors[idx];
			if ((color >>> 24) == 0) {
				continue;
			}
			int chunkX = idx & 31;
			int chunkZ = idx >> 5;
			int globalChunkX = regionX * CHUNKS_PER_REGION + chunkX;
			int globalChunkZ = regionZ * CHUNKS_PER_REGION + chunkZ;
			int x1 = regionLeft + (chunkX * regionPixel) / CHUNKS_PER_REGION;
			int z1 = regionTop + (chunkZ * regionPixel) / CHUNKS_PER_REGION;
			int x2 = regionLeft + ((chunkX + 1) * regionPixel) / CHUNKS_PER_REGION;
			int z2 = regionTop + ((chunkZ + 1) * regionPixel) / CHUNKS_PER_REGION;

			int chunkWidth = Math.max(1, x2 - x1);
			int chunkHeight = Math.max(1, z2 - z1);
			int blockPixelX = Math.max(1, chunkWidth / BLOCKS_PER_CHUNK_EDGE);
			int blockPixelZ = Math.max(1, chunkHeight / BLOCKS_PER_CHUNK_EDGE);
			boolean allowBlockDetail = Math.min(blockPixelX, blockPixelZ) >= MIN_BLOCK_PIXEL_FOR_DETAIL;

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

							int bx1 = x1 + (blockX * chunkWidth) / BLOCKS_PER_CHUNK_EDGE;
							int bz1 = z1 + (blockZ * chunkHeight) / BLOCKS_PER_CHUNK_EDGE;
							int bx2 = x1 + ((blockX + 1) * chunkWidth) / BLOCKS_PER_CHUNK_EDGE;
							int bz2 = z1 + ((blockZ + 1) * chunkHeight) / BLOCKS_PER_CHUNK_EDGE;
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
			int x = regionLeft + (i * regionPixel) / CHUNKS_PER_REGION;
			int z = regionTop + (i * regionPixel) / CHUNKS_PER_REGION;
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

			CompoundTag chunkTag = maybeDataFixChunkTag(NbtIo.read(input));
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

	private void publishBatch(int requestId, Map<Long, RegionFileResult> batch, int filesScanned, int regionsLoaded, boolean finalBatch) {
		if (requestId != this.activeLoadRequest) {
			closeRegionBatch(batch);
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
			for (Map.Entry<Long, RegionFileResult> entry : update.batch().entrySet()) {
				long packedRegion = entry.getKey();
				int regionX = unpackRegionX(packedRegion);
				int regionZ = unpackRegionZ(packedRegion);
				RegionFileResult region = entry.getValue();
				this.chunkColorsByRegion.put(packedRegion, region.chunkColors());
				this.uploadRegionTexture(packedRegion, region.regionImage());
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

	private void uploadRegionTexture(long packedRegion, NativeImage regionImage) {
		RegionTexture existing = this.regionTextures.remove(packedRegion);
		if (existing != null) {
			this.minecraft.getTextureManager().release(existing.location());
		}

		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
			"mattmc",
			"region_editor/" + this.activeLoadRequest + "/" + unpackRegionX(packedRegion) + "_" + unpackRegionZ(packedRegion)
		);
		DynamicTexture texture = new DynamicTexture(() -> "Region Editor " + unpackRegionX(packedRegion) + "," + unpackRegionZ(packedRegion), regionImage);
		texture.setClamp(true);
		texture.setFilter(false, false);
		this.minecraft.getTextureManager().register(location, texture);
		this.regionTextures.put(packedRegion, new RegionTexture(location, texture));
	}

	private void clearRegionTextures() {
		if (this.minecraft == null) {
			this.regionTextures.clear();
			return;
		}

		for (RegionTexture regionTexture : this.regionTextures.values()) {
			this.minecraft.getTextureManager().release(regionTexture.location());
		}
		this.regionTextures.clear();
	}

	private void clearPendingRegionUpdates() {
		RegionBatchUpdate update;
		while ((update = this.pendingRegionUpdates.poll()) != null) {
			closeRegionBatch(update.batch());
		}
	}

	private static void closeRegionBatch(Map<Long, RegionFileResult> batch) {
		if (batch == null) {
			return;
		}

		for (RegionFileResult result : batch.values()) {
			result.regionImage().close();
		}
	}

	@Override
	public void onClose() {
		this.clearPendingRegionUpdates();
		this.clearRegionTextures();
		super.onClose();
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

	private record RegionBatchUpdate(Map<Long, RegionFileResult> batch, int filesScanned, int regionsLoaded, boolean completed, boolean failed, boolean logProgress) {
		private static RegionBatchUpdate batch(Map<Long, RegionFileResult> batch, int filesScanned, int regionsLoaded, boolean logProgress) {
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

	private record RegionFileResult(long packedRegion, int[] chunkColors, NativeImage regionImage) {
	}

	private record LegacySectionData(BlockState[] palette, SimpleBitStorage storage) {
	}

	private record RegionTexture(ResourceLocation location, DynamicTexture texture) {
	}

	private record ChunkDetailUpdate(long chunkKey, int[] blockColors, int requestId) {
	}

	private record ClipboardData(Map<Long, CompoundTag> chunksByRelativeOffset, boolean snapToRegionGrid, int widthChunks, int heightChunks, int sourceChunkCount) {
	}

	private record PasteOrigin(int originChunkX, int originChunkZ, String summary) {
	}

	private record UndoChunkSnapshot(int chunkX, int chunkZ, boolean hadChunk, CompoundTag previousTag) {
	}

	private record UndoAction(String description, List<UndoChunkSnapshot> snapshots) {
	}

	private record MapTransform(int regionPixel, int startX, int startZ) {
	}

	private record RenderStats(int totalRegionGrid, int loadedRegions, int drawnRegions, int drawStride, int regionPixel) {
	}

	private interface GridCoord {
		int x();

		int z();
	}

	private record ChunkGridCoord(int chunkX, int chunkZ) implements GridCoord {
		@Override
		public int x() {
			return this.chunkX;
		}

		@Override
		public int z() {
			return this.chunkZ;
		}
	}

	private record RegionGridCoord(int regionX, int regionZ) implements GridCoord {
		@Override
		public int x() {
			return this.regionX;
		}

		@Override
		public int z() {
			return this.regionZ;
		}
	}
}
