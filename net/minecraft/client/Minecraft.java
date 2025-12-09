package net.minecraft.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.minecraft.server.profile.PlayerProfile;

import net.minecraft.client.auth.BanDetails;
import net.minecraft.client.auth.UserApiService;
import net.minecraft.client.auth.UserApiService.UserFlag;
import net.minecraft.client.auth.UserApiService.UserProperties;
import net.minecraft.client.auth.ProfileResult.ProfileActionType;
import net.minecraft.client.auth.ProfileResult;

import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.ClientShutdownWatchdog;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.datafixers.DataFixer;
import com.mojang.jtracy.DiscontinuousFrame;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.net.Proxy;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.Optionull;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debugchart.ProfilerPieChart;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;

import net.minecraft.client.gui.screens.BanNoticeScreens;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.OutOfMemoryScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.main.SilentInitException;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.LocalPlayerResolver;
import net.minecraft.client.profiling.ClientMetricsSamplersProvider;
import net.minecraft.client.quickplay.QuickPlay;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GpuWarnlistManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.VirtualScreen;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.client.resources.DryFoliageColorReloadListener;
import net.minecraft.client.resources.FoliageColorReloadListener;
import net.minecraft.client.resources.GrassColorReloadListener;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.client.resources.WaypointStyleManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.OpenFile;
import net.minecraft.network.chat.contents.KeybindResolver;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Dialogs;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DialogTags;
import net.minecraft.util.CommonLinks;
import net.minecraft.util.FileZipper;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.ModCheck;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.ContinuousProfiler;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.util.profiling.Profiler.Scope;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.InteractionResult.Fail;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Minecraft extends ReentrantBlockableEventLoop<Runnable> implements WindowEventHandler {
	static Minecraft instance;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MAX_TICKS_PER_UPDATE = 10;
	public static final ResourceLocation DEFAULT_FONT = ResourceLocation.withDefaultNamespace("default");
	public static final ResourceLocation UNIFORM_FONT = ResourceLocation.withDefaultNamespace("uniform");
	public static final ResourceLocation ALT_FONT = ResourceLocation.withDefaultNamespace("alt");
	private static final ResourceLocation REGIONAL_COMPLIANCIES = ResourceLocation.withDefaultNamespace("regional_compliancies.json");
	private static final CompletableFuture<Unit> RESOURCE_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
	private static final Component SOCIAL_INTERACTIONS_NOT_AVAILABLE = Component.translatable("multiplayer.socialInteractions.not_available");
	private static final Component SAVING_LEVEL = Component.translatable("menu.savingLevel");
	public static final String UPDATE_DRIVERS_ADVICE = "Please make sure you have up-to-date drivers (see aka.ms/mcdriver for instructions).";
	private final long canary = Double.doubleToLongBits(Math.PI);
	private final Path resourcePackDirectory;
	private final CompletableFuture<ProfileResult> profileFuture;
	private final TextureManager textureManager;
	private final ShaderManager shaderManager;
	private final DataFixer fixerUpper;
	private final VirtualScreen virtualScreen;
	private final Window window;
	private final DeltaTracker.Timer deltaTracker = new DeltaTracker.Timer(20.0F, 0L, this::getTickTargetMillis);
	private final RenderBuffers renderBuffers;
	public final LevelRenderer levelRenderer;
	private final EntityRenderDispatcher entityRenderDispatcher;
	private final ItemModelResolver itemModelResolver;
	private final ItemRenderer itemRenderer;
	private final MapRenderer mapRenderer;
	public final ParticleEngine particleEngine;
	private final ParticleResources particleResources;
	private final User user;
	public final Font font;
	public final Font fontFilterFishy;
	public final GameRenderer gameRenderer;
	public final Gui gui;
	public final Options options;
	public final DebugScreenEntryList debugEntries;
	private final HotbarManager hotbarManager;
	public final MouseHandler mouseHandler;
	public final KeyboardHandler keyboardHandler;
	private InputType lastInputType = InputType.NONE;
	public final File gameDirectory;
	private final String launchedVersion;
	private final String versionType;
	private final Proxy proxy;
	private final boolean offlineDeveloperMode;
	private final LevelStorageSource levelSource;
	private final boolean allowsMultiplayer;
	private final boolean allowsChat;
	private final ReloadableResourceManager resourceManager;
	private final VanillaPackResources vanillaPackResources;
	private final DownloadedPackSource downloadedPackSource;
	private final PackRepository resourcePackRepository;
	private final LanguageManager languageManager;
	private final BlockColors blockColors;
	private final RenderTarget mainRenderTarget;
	@Nullable
	private final TracyFrameCapture tracyFrameCapture;
	private final SoundManager soundManager;
	private final MusicManager musicManager;
	private final FontManager fontManager;
	private final SplashManager splashManager;
	private final GpuWarnlistManager gpuWarnlistManager;
	private final PeriodicNotificationManager regionalCompliancies = new PeriodicNotificationManager(REGIONAL_COMPLIANCIES, Minecraft::countryEqualsISO3);
	private final UserApiService userApiService;
	private final CompletableFuture<UserProperties> userPropertiesFuture;
	private final SkinManager skinManager;
	private final net.minecraft.client.resources.SkinLoader skinLoader;
	private final AtlasManager atlasManager;
	private final ModelManager modelManager;
	private final BlockRenderDispatcher blockRenderer;
	private final MapTextureManager mapTextureManager;
	private final WaypointStyleManager waypointStyles;
	private final ToastManager toastManager;
	private final Tutorial tutorial;
	private final PlayerSocialManager playerSocialManager;
	private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
	private final ProfileKeyPairManager profileKeyPairManager;
	private final QuickPlayLog quickPlayLog;
	private final Services services;
	private final PlayerSkinRenderCache playerSkinRenderCache;
	@Nullable
	public MultiPlayerGameMode gameMode;
	@Nullable
	public ClientLevel level;
	@Nullable
	public LocalPlayer player;
	@Nullable
	private IntegratedServer singleplayerServer;
	@Nullable
	private Connection pendingConnection;
	private boolean isLocalServer;
	@Nullable
	private Entity cameraEntity;
	@Nullable
	public Entity crosshairPickEntity;
	@Nullable
	public HitResult hitResult;
	private int rightClickDelay;
	public int missTime;
	private volatile boolean pause;
	private long lastNanoTime = Util.getNanos();
	private long lastTime;
	private int frames;
	public boolean noRender;
	@Nullable
	public Screen screen;
	@Nullable
	private Overlay overlay;
	private boolean clientLevelTeardownInProgress;
	Thread gameThread;
	private volatile boolean running;
	@Nullable
	private Supplier<CrashReport> delayedCrash;
	private static int fps;
	private long frameTimeNs;
	private final FramerateLimitTracker framerateLimitTracker;
	public boolean wireframe;
	public boolean smartCull = true;
	private boolean windowActive;
	@Nullable
	private CompletableFuture<Void> pendingReload;
	@Nullable
	private TutorialToast socialInteractionsToast;
	private int fpsPieRenderTicks;
	private final ContinuousProfiler fpsPieProfiler;
	private MetricsRecorder metricsRecorder = InactiveMetricsRecorder.INSTANCE;
	private final ResourceLoadStateTracker reloadStateTracker = new ResourceLoadStateTracker();
	private long savedCpuDuration;
	private double gpuUtilization;
	@Nullable
	private TimerQuery.FrameProfile currentFrameProfile;
	private final ChatListener chatListener;
	private final CommandHistory commandHistory;
	private final DirectoryValidator directoryValidator;
	private boolean gameLoadFinished;
	private final long clientStartTimeMs;
	private long clientTickCount;
	private final PacketProcessor packetProcessor;

	public Minecraft(GameConfig gameConfig) {
		super("Client");
		instance = this;
		this.clientStartTimeMs = System.currentTimeMillis();
		this.gameDirectory = gameConfig.location.gameDirectory;
		File file = gameConfig.location.assetDirectory;
		this.resourcePackDirectory = gameConfig.location.resourcePackDirectory.toPath();
		this.launchedVersion = gameConfig.game.launchVersion;
		this.versionType = gameConfig.game.versionType;
		Path path = this.gameDirectory.toPath();
		this.directoryValidator = LevelStorageSource.parseValidator(path.resolve("allowed_symlinks.txt"));
		ClientPackSource clientPackSource = new ClientPackSource(gameConfig.location.getExternalAssetSource(), this.directoryValidator);
		this.downloadedPackSource = new DownloadedPackSource(this, path.resolve("downloads"), gameConfig.user);
		RepositorySource repositorySource = new FolderRepositorySource(
			this.resourcePackDirectory, PackType.CLIENT_RESOURCES, PackSource.DEFAULT, this.directoryValidator
		);
		this.resourcePackRepository = new PackRepository(
			new RepositorySource[]{clientPackSource, this.downloadedPackSource.createRepositorySource(), repositorySource}
		);
		this.vanillaPackResources = clientPackSource.getVanillaPack();
		this.proxy = gameConfig.user.proxy;
		this.offlineDeveloperMode = gameConfig.game.offlineDeveloperMode;
		// Offline mode - no Yggdrasil authentication
		this.services = Services.createOffline(this.gameDirectory);
		this.user = gameConfig.user.user;
		// Always offline - no profile fetching from Mojang
		this.profileFuture = CompletableFuture.completedFuture(null);
		this.userApiService = UserApiService.OFFLINE;
		this.userPropertiesFuture = CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES);
		LOGGER.info("Setting user: {}", this.user.getName());
		LOGGER.debug("(Session ID is {})", this.user.getSessionId());
		this.allowsMultiplayer = !gameConfig.game.disableMultiplayer;
		this.allowsChat = !gameConfig.game.disableChat;
		this.singleplayerServer = null;
		KeybindResolver.setKeyResolver(KeyMapping::createNameSupplier);
		this.fixerUpper = DataFixers.getDataFixer();
		this.gameThread = Thread.currentThread();
		
		// Initialize shader system early (before Options)
		// This matches Iris's onEarlyInitialize pattern
		net.minecraft.client.renderer.shaders.core.ShaderSystem.getInstance()
			.earlyInitialize(this.gameDirectory.toPath());
		
		this.options = new Options(this, this.gameDirectory);
		this.debugEntries = new DebugScreenEntryList(this.gameDirectory);
		this.toastManager = new ToastManager(this, this.options);
		boolean bl = this.options.startedCleanly;
		this.options.startedCleanly = false;
		this.options.save();
		this.running = true;
		this.tutorial = new Tutorial(this, this.options);
		this.hotbarManager = new HotbarManager(path, this.fixerUpper);
		LOGGER.info("Backend library: {}", RenderSystem.getBackendDescription());
		DisplayData displayData = gameConfig.display;
		if (this.options.overrideHeight > 0 && this.options.overrideWidth > 0) {
			displayData = gameConfig.display.withSize(this.options.overrideWidth, this.options.overrideHeight);
		}

		if (!bl) {
			displayData = displayData.withFullscreen(false);
			this.options.fullscreenVideoModeString = null;
			LOGGER.warn("Detected unexpected shutdown during last game startup: resetting fullscreen mode");
		}

		Util.timeSource = RenderSystem.initBackendSystem();
		this.virtualScreen = new VirtualScreen(this);
		this.window = this.virtualScreen.newWindow(displayData, this.options.fullscreenVideoModeString, this.createTitle());
		this.setWindowActive(true);
		this.window.setWindowCloseCallback(new Runnable() {
			private boolean threadStarted;

			public void run() {
				if (!this.threadStarted) {
					this.threadStarted = true;
					ClientShutdownWatchdog.startShutdownWatchdog(gameConfig.location.gameDirectory, Minecraft.this.gameThread.threadId());
				}
			}
		});

		try {
			this.window.setIcon(this.vanillaPackResources, SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
		} catch (IOException var18) {
			LOGGER.error("Couldn't set icon", (Throwable)var18);
		}

		this.mouseHandler = new MouseHandler(this);
		this.mouseHandler.setup(this.window);
		this.keyboardHandler = new KeyboardHandler(this);
		this.keyboardHandler.setup(this.window);
		RenderSystem.initRenderer(
			this.window.handle(),
			this.options.glDebugVerbosity,
			SharedConstants.DEBUG_SYNCHRONOUS_GL_LOGS,
			(resourceLocation, shaderType) -> this.getShaderManager().getShader(resourceLocation, shaderType),
			gameConfig.game.renderDebugLabels
		);
		LOGGER.info("Using optional rendering extensions: {}", String.join(", ", RenderSystem.getDevice().getEnabledExtensions()));
		this.mainRenderTarget = new MainTarget(this.window.getWidth(), this.window.getHeight());
		this.resourceManager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
		this.resourcePackRepository.reload();
		this.options.loadSelectedResourcePacks(this.resourcePackRepository);
		this.languageManager = new LanguageManager(this.options.languageCode, clientLanguage -> {
			if (this.player != null) {
				this.player.connection.updateSearchTrees();
			}
		});
		this.resourceManager.registerReloadListener(this.languageManager);
		this.textureManager = new TextureManager(this.resourceManager);
		this.resourceManager.registerReloadListener(this.textureManager);
		this.shaderManager = new ShaderManager(this.textureManager, this::triggerResourcePackRecovery);
		this.resourceManager.registerReloadListener(this.shaderManager);
		SkinTextureDownloader skinTextureDownloader = new SkinTextureDownloader(this.proxy, this.textureManager, this);
		this.skinManager = new SkinManager(file.toPath().resolve("skins"), this.services, skinTextureDownloader, this);
		this.skinLoader = new net.minecraft.client.resources.SkinLoader(path);
		this.levelSource = new LevelStorageSource(path.resolve("saves"), path.resolve("backups"), this.directoryValidator, this.fixerUpper);
		this.commandHistory = new CommandHistory(path);
		this.musicManager = new MusicManager(this);
		this.soundManager = new SoundManager(this.options, this.musicManager);
		this.resourceManager.registerReloadListener(this.soundManager);
		this.splashManager = new SplashManager(this.user);
		this.resourceManager.registerReloadListener(this.splashManager);
		this.atlasManager = new AtlasManager(this.textureManager, this.options.mipmapLevels().get());
		this.resourceManager.registerReloadListener(this.atlasManager);
		ProfileResolver profileResolver = new LocalPlayerResolver(this, this.services.profileResolver());
		this.playerSkinRenderCache = new PlayerSkinRenderCache(this.textureManager, this.skinManager, profileResolver);
		ClientMannequin.registerOverrides(this.playerSkinRenderCache);
		this.fontManager = new FontManager(this.textureManager, this.atlasManager, this.playerSkinRenderCache);
		this.font = this.fontManager.createFont();
		this.fontFilterFishy = this.fontManager.createFontFilterFishy();
		this.resourceManager.registerReloadListener(this.fontManager);
		this.updateFontOptions();
		this.resourceManager.registerReloadListener(new GrassColorReloadListener());
		this.resourceManager.registerReloadListener(new FoliageColorReloadListener());
		this.resourceManager.registerReloadListener(new DryFoliageColorReloadListener());
		this.window.setErrorSection("Startup");
		RenderSystem.setupDefaultState();
		this.window.setErrorSection("Post startup");
		this.blockColors = BlockColors.createDefault();
		this.modelManager = new ModelManager(this.blockColors, this.atlasManager, this.playerSkinRenderCache);
		this.resourceManager.registerReloadListener(this.modelManager);
		EquipmentAssetManager equipmentAssetManager = new EquipmentAssetManager();
		this.resourceManager.registerReloadListener(equipmentAssetManager);
		this.itemModelResolver = new ItemModelResolver(this.modelManager);
		this.itemRenderer = new ItemRenderer();
		this.mapTextureManager = new MapTextureManager(this.textureManager);
		this.mapRenderer = new MapRenderer(this.atlasManager, this.mapTextureManager);

		try {
			int i = Runtime.getRuntime().availableProcessors();
			Tesselator.init();
			this.renderBuffers = new RenderBuffers(i);
		} catch (OutOfMemoryError var17) {
			TinyFileDialogs.tinyfd_messageBox(
				"Minecraft",
				"Oh no! The game was unable to allocate memory off-heap while trying to start. You may try to free some memory by closing other applications on your computer, check that your system meets the minimum requirements, and try again. If the problem persists, please visit: "
					+ CommonLinks.GENERAL_HELP,
				"ok",
				"error",
				true
			);
			throw new SilentInitException("Unable to allocate render buffers", var17);
		}

		this.playerSocialManager = new PlayerSocialManager(this, this.userApiService);
		this.blockRenderer = new BlockRenderDispatcher(
			this.modelManager.getBlockModelShaper(), this.atlasManager, this.modelManager.specialBlockModelRenderer(), this.blockColors
		);
		this.resourceManager.registerReloadListener(this.blockRenderer);
		this.entityRenderDispatcher = new EntityRenderDispatcher(
			this,
			this.textureManager,
			this.itemModelResolver,
			this.itemRenderer,
			this.mapRenderer,
			this.blockRenderer,
			this.atlasManager,
			this.font,
			this.options,
			this.modelManager.entityModels(),
			equipmentAssetManager,
			this.playerSkinRenderCache
		);
		this.resourceManager.registerReloadListener(this.entityRenderDispatcher);
		this.blockEntityRenderDispatcher = new BlockEntityRenderDispatcher(
			this.font,
			this.modelManager.entityModels(),
			this.blockRenderer,
			this.itemModelResolver,
			this.itemRenderer,
			this.entityRenderDispatcher,
			this.atlasManager,
			this.playerSkinRenderCache
		);
		this.resourceManager.registerReloadListener(this.blockEntityRenderDispatcher);
		this.particleResources = new ParticleResources();
		this.resourceManager.registerReloadListener(this.particleResources);
		this.particleEngine = new ParticleEngine(this.level, this.particleResources);
		this.particleResources.onReload(this.particleEngine::clearParticles);
		this.waypointStyles = new WaypointStyleManager();
		this.resourceManager.registerReloadListener(this.waypointStyles);
		this.gameRenderer = new GameRenderer(this, this.entityRenderDispatcher.getItemInHandRenderer(), this.renderBuffers, this.blockRenderer);
		this.levelRenderer = new LevelRenderer(
			this,
			this.entityRenderDispatcher,
			this.blockEntityRenderDispatcher,
			this.renderBuffers,
			this.gameRenderer.getLevelRenderState(),
			this.gameRenderer.getFeatureRenderDispatcher()
		);
		this.resourceManager.registerReloadListener(this.levelRenderer);
		this.resourceManager.registerReloadListener(this.levelRenderer.getCloudRenderer());
		this.gpuWarnlistManager = new GpuWarnlistManager();
		this.resourceManager.registerReloadListener(this.gpuWarnlistManager);
		this.resourceManager.registerReloadListener(this.regionalCompliancies);
		this.gui = new Gui(this);
		RenderSystem.setErrorCallback(this::onFullscreenError);
		if (this.mainRenderTarget.width != this.window.getWidth() || this.mainRenderTarget.height != this.window.getHeight()) {
			StringBuilder stringBuilder = new StringBuilder(
				"Recovering from unsupported resolution ("
					+ this.window.getWidth()
					+ "x"
					+ this.window.getHeight()
					+ ").\nPlease make sure you have up-to-date drivers (see aka.ms/mcdriver for instructions)."
			);

			try {
				GpuDevice gpuDevice = RenderSystem.getDevice();
				List<String> list = gpuDevice.getLastDebugMessages();
				if (!list.isEmpty()) {
					stringBuilder.append("\n\nReported GL debug messages:\n").append(String.join("\n", list));
				}
			} catch (Throwable var16) {
			}

			this.window.setWindowed(this.mainRenderTarget.width, this.mainRenderTarget.height);
			TinyFileDialogs.tinyfd_messageBox("Minecraft", stringBuilder.toString(), "ok", "error", false);
		} else if (this.options.fullscreen().get() && !this.window.isFullscreen()) {
			if (bl) {
				this.window.toggleFullScreen();
				this.options.fullscreen().set(this.window.isFullscreen());
			} else {
				this.options.fullscreen().set(false);
			}
		}

		this.window.updateVsync(this.options.enableVsync().get());
		this.window.updateRawMouseInput(this.options.rawMouseInput().get());
		this.window.setAllowCursorChanges(this.options.allowCursorChanges().get());
		this.window.setDefaultErrorCallback();
		this.resizeDisplay();
		this.gameRenderer.preloadUiShader(this.vanillaPackResources.asProvider());
		this.profileKeyPairManager = this.offlineDeveloperMode
			? ProfileKeyPairManager.EMPTY_KEY_MANAGER
			: ProfileKeyPairManager.create(this.userApiService, this.user, path);
		this.chatListener = new ChatListener(this);
		this.chatListener.setMessageDelay(this.options.chatDelay().get());
		TitleScreen.registerTextures(this.textureManager);
		LoadingOverlay.registerTextures(this.textureManager);
		this.gameRenderer.getPanorama().registerTextures(this.textureManager);
		this.setScreen(new GenericMessageScreen(Component.translatable("gui.loadingMinecraft")));
		List<PackResources> list2 = this.resourcePackRepository.openAllSelected();
		this.reloadStateTracker.startReload(ResourceLoadStateTracker.ReloadReason.INITIAL, list2);
		ReloadInstance reloadInstance = this.resourceManager
			.createReload(Util.backgroundExecutor().forName("resourceLoad"), this, RESOURCE_RELOAD_INITIAL_TASK, list2);
		Minecraft.GameLoadCookie gameLoadCookie = new Minecraft.GameLoadCookie(gameConfig.quickPlay);
		this.setOverlay(
			new LoadingOverlay(this, reloadInstance, optional -> Util.ifElse(optional, throwable -> this.rollbackResourcePacks(throwable, gameLoadCookie), () -> {
				if (SharedConstants.IS_RUNNING_IN_IDE) {
					this.selfTest();
				}

				this.reloadStateTracker.finishReload();
				this.onResourceLoadFinished(gameLoadCookie);
			}), false)
		);
		this.quickPlayLog = QuickPlayLog.of(gameConfig.quickPlay.logPath());
		this.framerateLimitTracker = new FramerateLimitTracker(this.options, this);
		this.fpsPieProfiler = new ContinuousProfiler(Util.timeSource, () -> this.fpsPieRenderTicks, this.framerateLimitTracker::isHeavilyThrottled);
		if (TracyClient.isAvailable() && gameConfig.game.captureTracyImages) {
			this.tracyFrameCapture = new TracyFrameCapture();
		} else {
			this.tracyFrameCapture = null;
		}

		this.packetProcessor = new PacketProcessor(this.gameThread);
	}

	public boolean hasShiftDown() {
		Window window = this.getWindow();
		return InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344);
	}

	public boolean hasControlDown() {
		Window window = this.getWindow();
		return InputConstants.isKeyDown(window, InputQuirks.EDIT_SHORTCUT_KEY_LEFT) || InputConstants.isKeyDown(window, InputQuirks.EDIT_SHORTCUT_KEY_RIGHT);
	}

	public boolean hasAltDown() {
		Window window = this.getWindow();
		return InputConstants.isKeyDown(window, 342) || InputConstants.isKeyDown(window, 346);
	}

	private void onResourceLoadFinished(@Nullable Minecraft.GameLoadCookie gameLoadCookie) {
		if (!this.gameLoadFinished) {
			this.gameLoadFinished = true;
			
			// Initialize shader pack repository after resources are loaded
			// This matches IRIS pattern: resources must be available before scanning for packs
			if (net.minecraft.client.renderer.shaders.core.ShaderSystem.getInstance().isInitialized()) {
				net.minecraft.client.renderer.shaders.core.ShaderSystem.getInstance()
					.onResourceManagerReady(this.resourceManager);
			}
			
			this.onGameLoadFinished(gameLoadCookie);
		}
	}

	private void onGameLoadFinished(@Nullable Minecraft.GameLoadCookie gameLoadCookie) {
		Runnable runnable = this.buildInitialScreens(gameLoadCookie);
		runnable.run();
		this.options.startedCleanly = true;
		this.options.save();
	}

	public boolean isGameLoadFinished() {
		return this.gameLoadFinished;
	}

	private Runnable buildInitialScreens(@Nullable Minecraft.GameLoadCookie gameLoadCookie) {
		List<Function<Runnable, Screen>> list = new ArrayList();
		boolean bl = this.addInitialScreens(list);
		Runnable runnable = () -> {
			if (gameLoadCookie != null && gameLoadCookie.quickPlayData.isEnabled()) {
				QuickPlay.connect(this, gameLoadCookie.quickPlayData.variant());
			} else {
				this.setScreen(new TitleScreen(true, new LogoRenderer(bl)));
			}
		};

		for (Function<Runnable, Screen> function : Lists.reverse(list)) {
			Screen screen = (Screen)function.apply(runnable);
			runnable = () -> this.setScreen(screen);
		}

		return runnable;
	}

	private boolean addInitialScreens(List<Function<Runnable, Screen>> list) {
		boolean bl = false;

		BanDetails banDetails = this.multiplayerBan();
		if (banDetails != null) {
			list.add((Function<Runnable, Screen>)(Runnable runnable) -> BanNoticeScreens.create(blx -> {
				if (blx) {
					Util.getPlatform().openUri(CommonLinks.SUSPENSION_HELP);
				}

				runnable.run();
			}, banDetails));
		}

		// Offline mode - no profile action checks needed
		ProfileResult profileResult = (ProfileResult)this.profileFuture.join();
		// Profile result is always null in offline mode

		return bl;
	}

	private static boolean countryEqualsISO3(Object object) {
		try {
			return Locale.getDefault().getISO3Country().equals(object);
		} catch (MissingResourceException var2) {
			return false;
		}
	}

	public void updateTitle() {
		this.window.setTitle(this.createTitle());
	}

	private String createTitle() {
		StringBuilder stringBuilder = new StringBuilder("Minecraft");
		if (checkModStatus().shouldReportAsModified()) {
			stringBuilder.append("*");
		}

		stringBuilder.append(" ");
		stringBuilder.append(SharedConstants.getCurrentVersion().name());
		ClientPacketListener clientPacketListener = this.getConnection();
		if (clientPacketListener != null && clientPacketListener.getConnection().isConnected()) {
			stringBuilder.append(" - ");
			ServerData serverData = this.getCurrentServer();
			if (this.singleplayerServer != null && !this.singleplayerServer.isPublished()) {
				stringBuilder.append(I18n.get("title.singleplayer"));
			} else if (this.singleplayerServer == null && (serverData == null || !serverData.isLan())) {
				stringBuilder.append(I18n.get("title.multiplayer.other"));
			} else {
				stringBuilder.append(I18n.get("title.multiplayer.lan"));
			}
		}

		return stringBuilder.toString();
	}

	private UserApiService createUserApiService(GameConfig gameConfig) {
		// Always offline mode
		return UserApiService.OFFLINE;
	}

	public boolean isOfflineDeveloperMode() {
		return this.offlineDeveloperMode;
	}

	public static ModCheck checkModStatus() {
		return ModCheck.identify("vanilla", ClientBrandRetriever::getClientModName, "Client", Minecraft.class);
	}

	private void rollbackResourcePacks(Throwable throwable, @Nullable Minecraft.GameLoadCookie gameLoadCookie) {
		if (this.resourcePackRepository.getSelectedIds().size() > 1) {
			this.clearResourcePacksOnError(throwable, null, gameLoadCookie);
		} else {
			Util.throwAsRuntime(throwable);
		}
	}

	public void clearResourcePacksOnError(Throwable throwable, @Nullable Component component, @Nullable Minecraft.GameLoadCookie gameLoadCookie) {
		LOGGER.info("Caught error loading resourcepacks, removing all selected resourcepacks", throwable);
		this.reloadStateTracker.startRecovery(throwable);
		this.downloadedPackSource.onRecovery();
		this.resourcePackRepository.setSelected(Collections.emptyList());
		this.options.resourcePacks.clear();
		this.options.incompatibleResourcePacks.clear();
		this.options.save();
		this.reloadResourcePacks(true, gameLoadCookie).thenRunAsync(() -> this.addResourcePackLoadFailToast(component), this);
	}

	private void abortResourcePackRecovery() {
		this.setOverlay(null);
		if (this.level != null) {
			this.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
			this.disconnectWithProgressScreen();
		}

		this.setScreen(new TitleScreen());
		this.addResourcePackLoadFailToast(null);
	}

	private void addResourcePackLoadFailToast(@Nullable Component component) {
		ToastManager toastManager = this.getToastManager();
		SystemToast.addOrUpdate(toastManager, SystemToast.SystemToastId.PACK_LOAD_FAILURE, Component.translatable("resourcePack.load_fail"), component);
	}

	public void triggerResourcePackRecovery(Exception exception) {
		if (!this.resourcePackRepository.isAbleToClearAnyPack()) {
			if (this.resourcePackRepository.getSelectedIds().size() <= 1) {
				LOGGER.error(LogUtils.FATAL_MARKER, exception.getMessage(), (Throwable)exception);
				this.emergencySaveAndCrash(new CrashReport(exception.getMessage(), exception));
			} else {
				this.schedule(this::abortResourcePackRecovery);
			}
		} else {
			this.clearResourcePacksOnError(exception, Component.translatable("resourcePack.runtime_failure"), null);
		}
	}

	public void run() {
		this.gameThread = Thread.currentThread();
		if (Runtime.getRuntime().availableProcessors() > 4) {
			this.gameThread.setPriority(10);
		}

		DiscontinuousFrame discontinuousFrame = TracyClient.createDiscontinuousFrame("Client Tick");

		try {
			boolean bl = false;

			while (this.running) {
				this.handleDelayedCrash();

				try {
					SingleTickProfiler singleTickProfiler = SingleTickProfiler.createTickProfiler("Renderer");
					boolean bl2 = this.getDebugOverlay().showProfilerChart();
					Scope scope = Profiler.use(this.constructProfiler(bl2, singleTickProfiler));

					try {
						this.metricsRecorder.startTick();
						discontinuousFrame.start();
						this.runTick(!bl);
						discontinuousFrame.end();
						this.metricsRecorder.endTick();
					} catch (Throwable var9) {
						if (scope != null) {
							try {
								scope.close();
							} catch (Throwable var8) {
								var9.addSuppressed(var8);
							}
						}

						throw var9;
					}

					if (scope != null) {
						scope.close();
					}

					this.finishProfilers(bl2, singleTickProfiler);
				} catch (OutOfMemoryError var10) {
					if (bl) {
						throw var10;
					}

					this.emergencySave();
					this.setScreen(new OutOfMemoryScreen());
					System.gc();
					LOGGER.error(LogUtils.FATAL_MARKER, "Out of memory", (Throwable)var10);
					bl = true;
				}
			}
		} catch (ReportedException var11) {
			LOGGER.error(LogUtils.FATAL_MARKER, "Reported exception thrown!", (Throwable)var11);
			this.emergencySaveAndCrash(var11.getReport());
		} catch (Throwable var12) {
			LOGGER.error(LogUtils.FATAL_MARKER, "Unreported exception thrown!", var12);
			this.emergencySaveAndCrash(new CrashReport("Unexpected error", var12));
		}
	}

	void updateFontOptions() {
		this.fontManager.updateOptions(this.options);
	}

	private void onFullscreenError(int i, long l) {
		this.options.enableVsync().set(false);
		this.options.save();
	}

	public RenderTarget getMainRenderTarget() {
		return this.mainRenderTarget;
	}

	public String getLaunchedVersion() {
		return this.launchedVersion;
	}

	public String getVersionType() {
		return this.versionType;
	}

	public void delayCrash(CrashReport crashReport) {
		this.delayedCrash = () -> this.fillReport(crashReport);
	}

	public void delayCrashRaw(CrashReport crashReport) {
		this.delayedCrash = () -> crashReport;
	}

	private void handleDelayedCrash() {
		if (this.delayedCrash != null) {
			crash(this, this.gameDirectory, (CrashReport)this.delayedCrash.get());
		}
	}

	public void emergencySaveAndCrash(CrashReport crashReport) {
		MemoryReserve.release();
		CrashReport crashReport2 = this.fillReport(crashReport);
		this.emergencySave();
		crash(this, this.gameDirectory, crashReport2);
	}

	public static int saveReport(File file, CrashReport crashReport) {
		Path path = file.toPath().resolve("crash-reports");
		Path path2 = path.resolve("crash-" + Util.getFilenameFormattedDateTime() + "-client.txt");
		Bootstrap.realStdoutPrintln(crashReport.getFriendlyReport(ReportType.CRASH));
		if (crashReport.getSaveFile() != null) {
			Bootstrap.realStdoutPrintln("#@!@# Game crashed! Crash report saved to: #@!@# " + crashReport.getSaveFile().toAbsolutePath());
			return -1;
		} else if (crashReport.saveToFile(path2, ReportType.CRASH)) {
			Bootstrap.realStdoutPrintln("#@!@# Game crashed! Crash report saved to: #@!@# " + path2.toAbsolutePath());
			return -1;
		} else {
			Bootstrap.realStdoutPrintln("#@?@# Game crashed! Crash report could not be saved. #@?@#");
			return -2;
		}
	}

	public static void crash(@Nullable Minecraft minecraft, File file, CrashReport crashReport) {
		int i = saveReport(file, crashReport);
		if (minecraft != null) {
			minecraft.soundManager.emergencyShutdown();
		}

		System.exit(i);
	}

	public boolean isEnforceUnicode() {
		return this.options.forceUnicodeFont().get();
	}

	public CompletableFuture<Void> reloadResourcePacks() {
		return this.reloadResourcePacks(false, null);
	}

	private CompletableFuture<Void> reloadResourcePacks(boolean bl, @Nullable Minecraft.GameLoadCookie gameLoadCookie) {
		if (this.pendingReload != null) {
			return this.pendingReload;
		} else {
			CompletableFuture<Void> completableFuture = new CompletableFuture();
			if (!bl && this.overlay instanceof LoadingOverlay) {
				this.pendingReload = completableFuture;
				return completableFuture;
			} else {
				this.resourcePackRepository.reload();
				List<PackResources> list = this.resourcePackRepository.openAllSelected();
				if (!bl) {
					this.reloadStateTracker.startReload(ResourceLoadStateTracker.ReloadReason.MANUAL, list);
				}

				this.setOverlay(
					new LoadingOverlay(
						this,
						this.resourceManager.createReload(Util.backgroundExecutor().forName("resourceLoad"), this, RESOURCE_RELOAD_INITIAL_TASK, list),
						optional -> Util.ifElse(optional, throwable -> {
							if (bl) {
								this.downloadedPackSource.onRecoveryFailure();
								this.abortResourcePackRecovery();
							} else {
								this.rollbackResourcePacks(throwable, gameLoadCookie);
							}
						}, () -> {
							this.levelRenderer.allChanged();
							this.reloadStateTracker.finishReload();
							this.downloadedPackSource.onReloadSuccess();
							completableFuture.complete(null);
							this.onResourceLoadFinished(gameLoadCookie);
						}),
						!bl
					)
				);
				return completableFuture;
			}
		}
	}

	private void selfTest() {
		boolean bl = false;
		BlockModelShaper blockModelShaper = this.getBlockRenderer().getBlockModelShaper();
		BlockStateModel blockStateModel = blockModelShaper.getModelManager().getMissingBlockStateModel();

		for (Block block : BuiltInRegistries.BLOCK) {
			for (BlockState blockState : block.getStateDefinition().getPossibleStates()) {
				if (blockState.getRenderShape() == RenderShape.MODEL) {
					BlockStateModel blockStateModel2 = blockModelShaper.getBlockModel(blockState);
					if (blockStateModel2 == blockStateModel) {
						LOGGER.debug("Missing model for: {}", blockState);
						bl = true;
					}
				}
			}
		}

		TextureAtlasSprite textureAtlasSprite = blockStateModel.particleIcon();

		for (Block block2 : BuiltInRegistries.BLOCK) {
			for (BlockState blockState2 : block2.getStateDefinition().getPossibleStates()) {
				TextureAtlasSprite textureAtlasSprite2 = blockModelShaper.getParticleIcon(blockState2);
				if (!blockState2.isAir() && textureAtlasSprite2 == textureAtlasSprite) {
					LOGGER.debug("Missing particle icon for: {}", blockState2);
				}
			}
		}

		BuiltInRegistries.ITEM.listElements().forEach(reference -> {
			Item item = (Item)reference.value();
			String string = item.getDescriptionId();
			String string2 = Component.translatable(string).getString();
			if (string2.toLowerCase(Locale.ROOT).equals(item.getDescriptionId())) {
				LOGGER.debug("Missing translation for: {} {} {}", reference.key().location(), string, item);
			}
		});
		bl |= MenuScreens.selfTest();
		bl |= EntityRenderers.validateRegistrations();
		if (bl) {
			throw new IllegalStateException("Your game data is foobar, fix the errors above!");
		}
	}

	public LevelStorageSource getLevelSource() {
		return this.levelSource;
	}

	public void openChatScreen(ChatComponent.ChatMethod chatMethod) {
		Minecraft.ChatStatus chatStatus = this.getChatStatus();
		if (!chatStatus.isChatAllowed(this.isLocalServer())) {
			if (this.gui.isShowingChatDisabledByPlayer()) {
				this.gui.setChatDisabledByPlayerShown(false);
				this.setScreen(new ConfirmLinkScreen(bl -> {
					if (bl) {
						Util.getPlatform().openUri(CommonLinks.ACCOUNT_SETTINGS);
					}

					this.setScreen(null);
				}, Minecraft.ChatStatus.INFO_DISABLED_BY_PROFILE, CommonLinks.ACCOUNT_SETTINGS, true));
			} else {
				Component component = chatStatus.getMessage();
				this.gui.setOverlayMessage(component, false);
				this.gui.setChatDisabledByPlayerShown(chatStatus == Minecraft.ChatStatus.DISABLED_BY_PROFILE);
			}
		} else {
			this.gui.getChat().openScreen(chatMethod, ChatScreen::new);
		}
	}

	public void setScreen(@Nullable Screen screen) {
		if (SharedConstants.IS_RUNNING_IN_IDE && Thread.currentThread() != this.gameThread) {
			LOGGER.error("setScreen called from non-game thread");
		}

		if (this.screen != null) {
			this.screen.removed();
		} else {
			this.setLastInputType(InputType.NONE);
		}

		if (screen == null) {
			if (this.clientLevelTeardownInProgress) {
				throw new IllegalStateException("Trying to return to in-game GUI during disconnection");
			}

			if (this.level == null) {
				screen = new TitleScreen();
			} else if (this.player.isDeadOrDying()) {
				if (this.player.shouldShowDeathScreen()) {
					screen = new DeathScreen(null, this.level.getLevelData().isHardcore());
				} else {
					this.player.respawn();
				}
			} else {
				screen = this.gui.getChat().restoreChatScreen();
			}
		}

		this.screen = screen;
		if (this.screen != null) {
			this.screen.added();
		}

		if (screen != null) {
			this.mouseHandler.releaseMouse();
			KeyMapping.releaseAll();
			screen.init(this, this.window.getGuiScaledWidth(), this.window.getGuiScaledHeight());
			this.noRender = false;
		} else {
			if (this.level != null) {
				KeyMapping.restoreToggleStatesOnScreenClosed();
			}

			this.soundManager.resume();
			this.mouseHandler.grabMouse();
		}

		this.updateTitle();
	}

	public void setOverlay(@Nullable Overlay overlay) {
		this.overlay = overlay;
	}

	public void destroy() {
		try {
			LOGGER.info("Stopping!");

			try {
				if (this.level != null) {
					this.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
				}

				this.disconnectWithProgressScreen();
			} catch (Throwable var6) {
			}

			if (this.screen != null) {
				this.screen.removed();
			}

			this.close();
		} finally {
			Util.timeSource = System::nanoTime;
			if (this.delayedCrash == null) {
				System.exit(0);
			}
		}
	}

	public void close() {
		if (this.currentFrameProfile != null) {
			this.currentFrameProfile.cancel();
		}

		try {
			this.regionalCompliancies.close();
			this.atlasManager.close();
			this.fontManager.close();
			this.gameRenderer.close();
			this.shaderManager.close();
			this.levelRenderer.close();
			this.soundManager.destroy();
			this.mapTextureManager.close();
			this.textureManager.close();
			this.resourceManager.close();
			if (this.tracyFrameCapture != null) {
				this.tracyFrameCapture.close();
			}

			FreeTypeUtil.destroy();
			Util.shutdownExecutors();
			RenderSystem.getDevice().close();
		} catch (Throwable var5) {
			LOGGER.error("Shutdown failure!", var5);
			throw var5;
		} finally {
			this.virtualScreen.close();
			this.window.close();
		}
	}

	private void runTick(boolean bl) {
		this.window.setErrorSection("Pre render");
		if (this.window.shouldClose()) {
			this.stop();
		}

		if (this.pendingReload != null && !(this.overlay instanceof LoadingOverlay)) {
			CompletableFuture<Void> completableFuture = this.pendingReload;
			this.pendingReload = null;
			this.reloadResourcePacks().thenRun(() -> completableFuture.complete(null));
		}

		int i = this.deltaTracker.advanceTime(Util.getMillis(), bl);
		ProfilerFiller profilerFiller = Profiler.get();
		if (bl) {
			profilerFiller.push("scheduledPacketProcessing");
			this.packetProcessor.processQueuedPackets();
			profilerFiller.popPush("scheduledExecutables");
			this.runAllTasks();
			profilerFiller.popPush("tick");

			for (int j = 0; j < Math.min(10, i); j++) {
				profilerFiller.incrementCounter("clientTick");
				this.tick();
			}

			profilerFiller.pop();
		}

		this.window.setErrorSection("Render");
		profilerFiller.push("gpuAsync");
		RenderSystem.executePendingTasks();
		profilerFiller.popPush("sound");
		this.soundManager.updateSource(this.gameRenderer.getMainCamera());
		profilerFiller.popPush("toasts");
		this.toastManager.update();
		profilerFiller.popPush("mouse");
		this.mouseHandler.handleAccumulatedMovement();
		profilerFiller.popPush("render");
		long l = Util.getNanos();
		boolean bl2;
		if (!this.debugEntries.isCurrentlyEnabled(DebugScreenEntries.GPU_UTILIZATION) && !this.metricsRecorder.isRecording()) {
			bl2 = false;
			this.gpuUtilization = 0.0;
		} else {
			bl2 = (this.currentFrameProfile == null || this.currentFrameProfile.isDone()) && !TimerQuery.getInstance().isRecording();
			if (bl2) {
				TimerQuery.getInstance().beginProfile();
			}
		}

		RenderTarget renderTarget = this.getMainRenderTarget();
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(renderTarget.getColorTexture(), 0, renderTarget.getDepthTexture(), 1.0);
		profilerFiller.push("gameRenderer");
		if (!this.noRender) {
			this.gameRenderer.render(this.deltaTracker, bl);
		}

		profilerFiller.popPush("blit");
		if (!this.window.isMinimized()) {
			renderTarget.blitToScreen();
		}

		this.frameTimeNs = Util.getNanos() - l;
		if (bl2) {
			this.currentFrameProfile = TimerQuery.getInstance().endProfile();
		}

		profilerFiller.popPush("updateDisplay");
		if (this.tracyFrameCapture != null) {
			this.tracyFrameCapture.upload();
			this.tracyFrameCapture.capture(renderTarget);
		}

		this.window.updateDisplay(this.tracyFrameCapture);
		int k = this.framerateLimitTracker.getFramerateLimit();
		if (k < 260) {
			RenderSystem.limitDisplayFPS(k);
		}

		profilerFiller.pop();
		profilerFiller.popPush("yield");
		Thread.yield();
		profilerFiller.pop();
		this.window.setErrorSection("Post render");
		this.frames++;
		boolean bl3 = this.pause;
		this.pause = this.hasSingleplayerServer()
			&& (this.screen != null && this.screen.isPauseScreen() || this.overlay != null && this.overlay.isPauseScreen())
			&& !this.singleplayerServer.isPublished();
		if (!bl3 && this.pause) {
			this.soundManager.pauseAllExcept(SoundSource.MUSIC, SoundSource.UI);
		}

		this.deltaTracker.updatePauseState(this.pause);
		this.deltaTracker.updateFrozenState(!this.isLevelRunningNormally());
		long m = Util.getNanos();
		long n = m - this.lastNanoTime;
		if (bl2) {
			this.savedCpuDuration = n;
		}

		this.getDebugOverlay().logFrameDuration(n);
		this.lastNanoTime = m;
		profilerFiller.push("fpsUpdate");
		if (this.currentFrameProfile != null && this.currentFrameProfile.isDone()) {
			this.gpuUtilization = this.currentFrameProfile.get() * 100.0 / this.savedCpuDuration;
		}

		while (Util.getMillis() >= this.lastTime + 1000L) {
			fps = this.frames;
			this.lastTime += 1000L;
			this.frames = 0;
		}

		profilerFiller.pop();
	}

	private ProfilerFiller constructProfiler(boolean bl, @Nullable SingleTickProfiler singleTickProfiler) {
		if (!bl) {
			this.fpsPieProfiler.disable();
			if (!this.metricsRecorder.isRecording() && singleTickProfiler == null) {
				return InactiveProfiler.INSTANCE;
			}
		}

		ProfilerFiller profilerFiller;
		if (bl) {
			if (!this.fpsPieProfiler.isEnabled()) {
				this.fpsPieRenderTicks = 0;
				this.fpsPieProfiler.enable();
			}

			this.fpsPieRenderTicks++;
			profilerFiller = this.fpsPieProfiler.getFiller();
		} else {
			profilerFiller = InactiveProfiler.INSTANCE;
		}

		if (this.metricsRecorder.isRecording()) {
			profilerFiller = ProfilerFiller.combine(profilerFiller, this.metricsRecorder.getProfiler());
		}

		return SingleTickProfiler.decorateFiller(profilerFiller, singleTickProfiler);
	}

	private void finishProfilers(boolean bl, @Nullable SingleTickProfiler singleTickProfiler) {
		if (singleTickProfiler != null) {
			singleTickProfiler.endTick();
		}

		ProfilerPieChart profilerPieChart = this.getDebugOverlay().getProfilerPieChart();
		if (bl) {
			profilerPieChart.setPieChartResults(this.fpsPieProfiler.getResults());
		} else {
			profilerPieChart.setPieChartResults(null);
		}
	}

	@Override
	public void resizeDisplay() {
		int i = this.window.calculateScale(this.options.guiScale().get(), this.isEnforceUnicode());
		this.window.setGuiScale(i);
		if (this.screen != null) {
			this.screen.resize(this, this.window.getGuiScaledWidth(), this.window.getGuiScaledHeight());
		}

		RenderTarget renderTarget = this.getMainRenderTarget();
		renderTarget.resize(this.window.getWidth(), this.window.getHeight());
		this.gameRenderer.resize(this.window.getWidth(), this.window.getHeight());
		this.mouseHandler.setIgnoreFirstMove();
	}

	@Override
	public void cursorEntered() {
		this.mouseHandler.cursorEntered();
	}

	public int getFps() {
		return fps;
	}

	public long getFrameTimeNs() {
		return this.frameTimeNs;
	}

	private void emergencySave() {
		MemoryReserve.release();

		try {
			if (this.isLocalServer && this.singleplayerServer != null) {
				this.singleplayerServer.halt(true);
			}

			this.disconnectWithSavingScreen();
		} catch (Throwable var2) {
		}

		System.gc();
	}

	public boolean debugClientMetricsStart(Consumer<Component> consumer) {
		if (this.metricsRecorder.isRecording()) {
			this.debugClientMetricsStop();
			return false;
		} else {
			Consumer<ProfileResults> consumer2 = profileResults -> {
				if (profileResults != EmptyProfileResults.EMPTY) {
					int i = profileResults.getTickDuration();
					double d = (double)profileResults.getNanoDuration() / TimeUtil.NANOSECONDS_PER_SECOND;
					this.execute(
						() -> consumer.accept(
							Component.translatable("commands.debug.stopped", new Object[]{String.format(Locale.ROOT, "%.2f", d), i, String.format(Locale.ROOT, "%.2f", i / d)})
						)
					);
				}
			};
			Consumer<Path> consumer3 = path -> {
				Component component = Component.literal(path.toString())
					.withStyle(ChatFormatting.UNDERLINE)
					.withStyle(style -> style.withClickEvent(new OpenFile(path.getParent())));
				this.execute(() -> consumer.accept(Component.translatable("debug.profiling.stop", new Object[]{component})));
			};
			SystemReport systemReport = fillSystemReport(new SystemReport(), this, this.languageManager, this.launchedVersion, this.options);
			Consumer<List<Path>> consumer4 = list -> {
				Path path = this.archiveProfilingReport(systemReport, list);
				consumer3.accept(path);
			};
			Consumer<Path> consumer5;
			if (this.singleplayerServer == null) {
				consumer5 = path -> consumer4.accept(ImmutableList.of(path));
			} else {
				this.singleplayerServer.fillSystemReport(systemReport);
				CompletableFuture<Path> completableFuture = new CompletableFuture();
				CompletableFuture<Path> completableFuture2 = new CompletableFuture();
				CompletableFuture.allOf(completableFuture, completableFuture2)
					.thenRunAsync(() -> consumer4.accept(ImmutableList.of((Path)completableFuture.join(), (Path)completableFuture2.join())), Util.ioPool());
				this.singleplayerServer.startRecordingMetrics(profileResults -> {}, completableFuture2::complete);
				consumer5 = completableFuture::complete;
			}

			this.metricsRecorder = ActiveMetricsRecorder.createStarted(
				new ClientMetricsSamplersProvider(Util.timeSource, this.levelRenderer), Util.timeSource, Util.ioPool(), new MetricsPersister("client"), profileResults -> {
					this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
					consumer2.accept(profileResults);
				}, consumer5
			);
			return true;
		}
	}

	private void debugClientMetricsStop() {
		this.metricsRecorder.end();
		if (this.singleplayerServer != null) {
			this.singleplayerServer.finishRecordingMetrics();
		}
	}

	private void debugClientMetricsCancel() {
		this.metricsRecorder.cancel();
		if (this.singleplayerServer != null) {
			this.singleplayerServer.cancelRecordingMetrics();
		}
	}

	private Path archiveProfilingReport(SystemReport systemReport, List<Path> list) {
		String string;
		if (this.isLocalServer()) {
			string = this.getSingleplayerServer().getWorldData().getLevelName();
		} else {
			ServerData serverData = this.getCurrentServer();
			string = serverData != null ? serverData.name : "unknown";
		}

		Path path;
		try {
			String string2 = String.format(Locale.ROOT, "%s-%s-%s", Util.getFilenameFormattedDateTime(), string, SharedConstants.getCurrentVersion().id());
			String string3 = FileUtil.findAvailableName(MetricsPersister.PROFILING_RESULTS_DIR, string2, ".zip");
			path = MetricsPersister.PROFILING_RESULTS_DIR.resolve(string3);
		} catch (IOException var21) {
			throw new UncheckedIOException(var21);
		}

		try {
			FileZipper fileZipper = new FileZipper(path);

			try {
				fileZipper.add(Paths.get("system.txt"), systemReport.toLineSeparatedString());
				fileZipper.add(Paths.get("client").resolve(this.options.getFile().getName()), this.options.dumpOptionsForReport());
				list.forEach(fileZipper::add);
			} catch (Throwable var20) {
				try {
					fileZipper.close();
				} catch (Throwable var19) {
					var20.addSuppressed(var19);
				}

				throw var20;
			}

			fileZipper.close();
		} finally {
			for (Path path3 : list) {
				try {
					FileUtils.forceDelete(path3.toFile());
				} catch (IOException var18) {
					LOGGER.warn("Failed to delete temporary profiling result {}", path3, var18);
				}
			}
		}

		return path;
	}

	public void stop() {
		this.running = false;
	}

	public boolean isRunning() {
		return this.running;
	}

	public void pauseGame(boolean bl) {
		if (this.screen == null) {
			boolean bl2 = this.hasSingleplayerServer() && !this.singleplayerServer.isPublished();
			if (bl2) {
				this.setScreen(new PauseScreen(!bl));
			} else {
				this.setScreen(new PauseScreen(true));
			}
		}
	}

	private void continueAttack(boolean bl) {
		if (!bl) {
			this.missTime = 0;
		}

		if (this.missTime <= 0 && !this.player.isUsingItem()) {
			if (bl && this.hitResult != null && this.hitResult.getType() == Type.BLOCK) {
				BlockHitResult blockHitResult = (BlockHitResult)this.hitResult;
				BlockPos blockPos = blockHitResult.getBlockPos();
				if (!this.level.getBlockState(blockPos).isAir()) {
					Direction direction = blockHitResult.getDirection();
					if (this.gameMode.continueDestroyBlock(blockPos, direction)) {
						this.level.addBreakingBlockEffect(blockPos, direction);
						this.player.swing(InteractionHand.MAIN_HAND);
					}
				}
			} else {
				this.gameMode.stopDestroyBlock();
			}
		}
	}

	private boolean startAttack() {
		if (this.missTime > 0) {
			return false;
		} else if (this.hitResult == null) {
			LOGGER.error("Null returned as 'hitResult', this shouldn't happen!");
			if (this.gameMode.hasMissTime()) {
				this.missTime = 10;
			}

			return false;
		} else if (this.player.isHandsBusy()) {
			return false;
		} else {
			ItemStack itemStack = this.player.getItemInHand(InteractionHand.MAIN_HAND);
			if (!itemStack.isItemEnabled(this.level.enabledFeatures())) {
				return false;
			} else {
				boolean bl = false;
				switch (this.hitResult.getType()) {
					case ENTITY:
						this.gameMode.attack(this.player, ((EntityHitResult)this.hitResult).getEntity());
						break;
					case BLOCK:
						BlockHitResult blockHitResult = (BlockHitResult)this.hitResult;
						BlockPos blockPos = blockHitResult.getBlockPos();
						if (!this.level.getBlockState(blockPos).isAir()) {
							this.gameMode.startDestroyBlock(blockPos, blockHitResult.getDirection());
							if (this.level.getBlockState(blockPos).isAir()) {
								bl = true;
							}
							break;
						}
					case MISS:
						if (this.gameMode.hasMissTime()) {
							this.missTime = 10;
						}

						this.player.resetAttackStrengthTicker();
				}

				this.player.swing(InteractionHand.MAIN_HAND);
				return bl;
			}
		}
	}

	private void startUseItem() {
		if (!this.gameMode.isDestroying()) {
			this.rightClickDelay = 4;
			if (!this.player.isHandsBusy()) {
				if (this.hitResult == null) {
					LOGGER.warn("Null returned as 'hitResult', this shouldn't happen!");
				}

				for (InteractionHand interactionHand : InteractionHand.values()) {
					ItemStack itemStack = this.player.getItemInHand(interactionHand);
					if (!itemStack.isItemEnabled(this.level.enabledFeatures())) {
						return;
					}

					if (this.hitResult != null) {
						switch (this.hitResult.getType()) {
							case ENTITY:
								EntityHitResult entityHitResult = (EntityHitResult)this.hitResult;
								Entity entity = entityHitResult.getEntity();
								if (!this.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
									return;
								}

								InteractionResult interactionResult = this.gameMode.interactAt(this.player, entity, entityHitResult, interactionHand);
								if (!interactionResult.consumesAction()) {
									interactionResult = this.gameMode.interact(this.player, entity, interactionHand);
								}

								if (interactionResult instanceof Success success) {
									if (success.swingSource() == SwingSource.CLIENT) {
										this.player.swing(interactionHand);
									}

									return;
								}
								break;
							case BLOCK:
								BlockHitResult blockHitResult = (BlockHitResult)this.hitResult;
								int i = itemStack.getCount();
								InteractionResult interactionResult2 = this.gameMode.useItemOn(this.player, interactionHand, blockHitResult);
								if (interactionResult2 instanceof Success success2) {
									if (success2.swingSource() == SwingSource.CLIENT) {
										this.player.swing(interactionHand);
										if (!itemStack.isEmpty() && (itemStack.getCount() != i || this.player.hasInfiniteMaterials())) {
											this.gameRenderer.itemInHandRenderer.itemUsed(interactionHand);
										}
									}

									return;
								}

								if (interactionResult2 instanceof Fail) {
									return;
								}
						}
					}

					if (!itemStack.isEmpty() && this.gameMode.useItem(this.player, interactionHand) instanceof Success success3) {
						if (success3.swingSource() == SwingSource.CLIENT) {
							this.player.swing(interactionHand);
						}

						this.gameRenderer.itemInHandRenderer.itemUsed(interactionHand);
						return;
					}
				}
			}
		}
	}

	public MusicManager getMusicManager() {
		return this.musicManager;
	}

	public void tick() {
		this.clientTickCount++;
		if (this.level != null && !this.pause) {
			this.level.tickRateManager().tick();
		}

		if (this.rightClickDelay > 0) {
			this.rightClickDelay--;
		}

		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("gui");
		this.chatListener.tick();
		this.gui.tick(this.pause);
		profilerFiller.pop();
		this.gameRenderer.pick(1.0F);
		this.tutorial.onLookAt(this.level, this.hitResult);
		profilerFiller.push("gameMode");
		if (!this.pause && this.level != null) {
			this.gameMode.tick();
		}

		profilerFiller.popPush("textures");
		if (this.isLevelRunningNormally()) {
			this.textureManager.tick();
		}

		if (this.screen != null || this.player == null) {
			if (this.screen instanceof InBedChatScreen inBedChatScreen && !this.player.isSleeping()) {
				inBedChatScreen.onPlayerWokeUp();
			}
		} else if (this.player.isDeadOrDying() && !(this.screen instanceof DeathScreen)) {
			this.setScreen(null);
		} else if (this.player.isSleeping() && this.level != null) {
			this.gui.getChat().openScreen(ChatComponent.ChatMethod.MESSAGE, InBedChatScreen::new);
		}

		if (this.screen != null) {
			this.missTime = 10000;
		}

		if (this.screen != null) {
			try {
				this.screen.tick();
			} catch (Throwable var5) {
				CrashReport crashReport = CrashReport.forThrowable(var5, "Ticking screen");
				this.screen.fillCrashDetails(crashReport);
				throw new ReportedException(crashReport);
			}
		}

		if (this.overlay != null) {
			this.overlay.tick();
		}

		if (!this.getDebugOverlay().showDebugScreen()) {
			this.gui.clearCache();
		}

		if (this.overlay == null && this.screen == null) {
			profilerFiller.popPush("Keybindings");
			this.handleKeybinds();
			if (this.missTime > 0) {
				this.missTime--;
			}
		}

		if (this.level != null) {
			if (!this.pause) {
				profilerFiller.popPush("gameRenderer");
				this.gameRenderer.tick();
				profilerFiller.popPush("entities");
				this.level.tickEntities();
				profilerFiller.popPush("blockEntities");
				this.level.tickBlockEntities();
			}
		} else if (this.gameRenderer.currentPostEffect() != null) {
			this.gameRenderer.clearPostEffect();
		}

		this.musicManager.tick();
		this.soundManager.tick(this.pause);
		if (this.level != null) {
			if (!this.pause) {
				profilerFiller.popPush("level");
				if (!this.options.joinedFirstServer && this.isMultiplayerServer()) {
					Component component = Component.translatable("tutorial.socialInteractions.title");
					Component component2 = Component.translatable("tutorial.socialInteractions.description", new Object[]{Tutorial.key("socialInteractions")});
					this.socialInteractionsToast = new TutorialToast(this.font, TutorialToast.Icons.SOCIAL_INTERACTIONS, component, component2, true, 8000);
					this.toastManager.addToast(this.socialInteractionsToast);
					this.options.joinedFirstServer = true;
					this.options.save();
				}

				this.tutorial.tick();

				try {
					this.level.tick(() -> true);
				} catch (Throwable var6) {
					CrashReport crashReport = CrashReport.forThrowable(var6, "Exception in world tick");
					if (this.level == null) {
						CrashReportCategory crashReportCategory = crashReport.addCategory("Affected level");
						crashReportCategory.setDetail("Problem", "Level is null!");
					} else {
						this.level.fillReportDetails(crashReport);
					}

					throw new ReportedException(crashReport);
				}
			}

			profilerFiller.popPush("animateTick");
			if (!this.pause && this.isLevelRunningNormally()) {
				this.level.animateTick(this.player.getBlockX(), this.player.getBlockY(), this.player.getBlockZ());
			}

			profilerFiller.popPush("particles");
			if (!this.pause && this.isLevelRunningNormally()) {
				this.particleEngine.tick();
			}

			ClientPacketListener clientPacketListener = this.getConnection();
			if (clientPacketListener != null && !this.pause) {
				clientPacketListener.send(ServerboundClientTickEndPacket.INSTANCE);
			}
		} else if (this.pendingConnection != null) {
			profilerFiller.popPush("pendingConnection");
			this.pendingConnection.tick();
		}

		profilerFiller.popPush("keyboard");
		this.keyboardHandler.tick();
		profilerFiller.pop();
	}

	private boolean isLevelRunningNormally() {
		return this.level == null || this.level.tickRateManager().runsNormally();
	}

	private boolean isMultiplayerServer() {
		return !this.isLocalServer || this.singleplayerServer != null && this.singleplayerServer.isPublished();
	}

	private void handleKeybinds() {
		while (this.options.keyTogglePerspective.consumeClick()) {
			CameraType cameraType = this.options.getCameraType();
			this.options.setCameraType(this.options.getCameraType().cycle());
			if (cameraType.isFirstPerson() != this.options.getCameraType().isFirstPerson()) {
				this.gameRenderer.checkEntityPostEffect(this.options.getCameraType().isFirstPerson() ? this.getCameraEntity() : null);
			}

			this.levelRenderer.needsUpdate();
		}

		while (this.options.keySmoothCamera.consumeClick()) {
			this.options.smoothCamera = !this.options.smoothCamera;
		}

		for (int i = 0; i < 9; i++) {
			boolean bl = this.options.keySaveHotbarActivator.isDown();
			boolean bl2 = this.options.keyLoadHotbarActivator.isDown();
			if (this.options.keyHotbarSlots[i].consumeClick()) {
				if (this.player.isSpectator()) {
					this.gui.getSpectatorGui().onHotbarSelected(i);
				} else if (!this.player.hasInfiniteMaterials() || this.screen != null || !bl2 && !bl) {
					this.player.getInventory().setSelectedSlot(i);
				} else {
					CreativeModeInventoryScreen.handleHotbarLoadOrSave(this, i, bl2, bl);
				}
			}
		}

		while (this.options.keySocialInteractions.consumeClick()) {
			if (!this.isMultiplayerServer() && !SharedConstants.DEBUG_SOCIAL_INTERACTIONS) {
				this.player.displayClientMessage(SOCIAL_INTERACTIONS_NOT_AVAILABLE, true);
			} else {
				if (this.socialInteractionsToast != null) {
					this.socialInteractionsToast.hide();
					this.socialInteractionsToast = null;
				}

				this.setScreen(new SocialInteractionsScreen());
			}
		}

		while (this.options.keyInventory.consumeClick()) {
			if (this.gameMode.isServerControlledInventory()) {
				this.player.sendOpenInventory();
			} else {
				this.tutorial.onOpenInventory();
				this.setScreen(new InventoryScreen(this.player));
			}
		}

		while (this.options.keyAdvancements.consumeClick()) {
			this.setScreen(new AdvancementsScreen(this.player.connection.getAdvancements()));
		}

		while (this.options.keyQuickActions.consumeClick()) {
			this.getQuickActionsDialog().ifPresent(holder -> this.player.connection.showDialog(holder, this.screen));
		}

		while (this.options.keySwapOffhand.consumeClick()) {
			if (!this.player.isSpectator()) {
				this.getConnection().send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
			}
		}

		while (this.options.keyDrop.consumeClick()) {
			if (!this.player.isSpectator() && this.player.drop(this.hasControlDown())) {
				this.player.swing(InteractionHand.MAIN_HAND);
			}
		}

		while (this.options.keyChat.consumeClick()) {
			this.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
		}

		if (this.screen == null && this.overlay == null && this.options.keyCommand.consumeClick()) {
			this.openChatScreen(ChatComponent.ChatMethod.COMMAND);
		}

		boolean bl3 = false;
		if (this.player.isUsingItem()) {
			if (!this.options.keyUse.isDown()) {
				this.gameMode.releaseUsingItem(this.player);
			}

			while (this.options.keyAttack.consumeClick()) {
			}

			while (this.options.keyUse.consumeClick()) {
			}

			while (this.options.keyPickItem.consumeClick()) {
			}
		} else {
			while (this.options.keyAttack.consumeClick()) {
				bl3 |= this.startAttack();
			}

			while (this.options.keyUse.consumeClick()) {
				this.startUseItem();
			}

			while (this.options.keyPickItem.consumeClick()) {
				this.pickBlock();
			}

			if (this.player.isSpectator()) {
				while (this.options.keySpectatorHotbar.consumeClick()) {
					this.gui.getSpectatorGui().onHotbarActionKeyPressed();
				}
			}
		}

		if (this.options.keyUse.isDown() && this.rightClickDelay == 0 && !this.player.isUsingItem()) {
			this.startUseItem();
		}

		this.continueAttack(this.screen == null && !bl3 && this.options.keyAttack.isDown() && this.mouseHandler.isMouseGrabbed());
	}

	private Optional<Holder<Dialog>> getQuickActionsDialog() {
		Registry<Dialog> registry = this.player.connection.registryAccess().lookupOrThrow(Registries.DIALOG);
		return registry.get(DialogTags.QUICK_ACTIONS).flatMap(named -> {
			if (named.size() == 0) {
				return Optional.empty();
			} else {
				return named.size() == 1 ? Optional.of(named.get(0)) : registry.get(Dialogs.QUICK_ACTIONS);
			}
		});
	}

	public double getGpuUtilization() {
		return this.gpuUtilization;
	}

	public ProfileKeyPairManager getProfileKeyPairManager() {
		return this.profileKeyPairManager;
	}

	public WorldOpenFlows createWorldOpenFlows() {
		return new WorldOpenFlows(this, this.levelSource);
	}

	public void doWorldLoad(LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, boolean bl) {
		this.disconnectWithProgressScreen();
		Instant instant = Instant.now();
		LevelLoadTracker levelLoadTracker = new LevelLoadTracker(bl ? 500L : 0L);
		LevelLoadingScreen levelLoadingScreen = new LevelLoadingScreen(levelLoadTracker, LevelLoadingScreen.Reason.OTHER);
		this.setScreen(levelLoadingScreen);
		int i = Math.max(5, 3) + ChunkLevel.RADIUS_AROUND_FULL_CHUNK + 1;

		try {
			levelStorageAccess.saveDataTag(worldStem.registries().compositeAccess(), worldStem.worldData());
			LevelLoadListener levelLoadListener = LevelLoadListener.compose(levelLoadTracker, LoggingLevelLoadListener.forSingleplayer());
			this.singleplayerServer = (IntegratedServer)MinecraftServer.spin(
				thread -> new IntegratedServer(thread, this, levelStorageAccess, packRepository, worldStem, this.services, levelLoadListener)
			);
			levelLoadTracker.setServerChunkStatusView(this.singleplayerServer.createChunkLoadStatusView(i));
			this.isLocalServer = true;
			this.quickPlayLog.setWorldData(QuickPlayLog.Type.SINGLEPLAYER, levelStorageAccess.getLevelId(), worldStem.worldData().getLevelName());
		} catch (Throwable var15) {
			CrashReport crashReport = CrashReport.forThrowable(var15, "Starting integrated server");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Starting integrated server");
			crashReportCategory.setDetail("Level ID", levelStorageAccess.getLevelId());
			crashReportCategory.setDetail("Level Name", () -> worldStem.worldData().getLevelName());
			throw new ReportedException(crashReport);
		}

		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("waitForServer");
		long l = TimeUnit.SECONDS.toNanos(1L) / 60L;

		while (!this.singleplayerServer.isReady() || this.overlay != null) {
			long m = Util.getNanos() + l;
			levelLoadingScreen.tick();
			if (this.overlay != null) {
				this.overlay.tick();
			}

			this.runTick(false);
			this.runAllTasks();
			this.managedBlock(() -> Util.getNanos() > m);
			this.handleDelayedCrash();
		}

		profilerFiller.pop();
		Duration duration = Duration.between(instant, Instant.now());
		SocketAddress socketAddress = this.singleplayerServer.getConnection().startMemoryChannel();
		Connection connection = Connection.connectToLocalServer(socketAddress);
		connection.initiateServerboundPlayConnection(
			socketAddress.toString(), 0, new ClientHandshakePacketListenerImpl(connection, this, null, null, bl, duration, component -> {}, levelLoadTracker, null)
		);
		connection.send(new ServerboundHelloPacket(this.getUser().getName(), this.getUser().getProfileId()));
		this.pendingConnection = connection;
	}

	public void setLevel(ClientLevel clientLevel) {
		this.level = clientLevel;
		this.updateLevelInEngines(clientLevel);
	}

	public void disconnectFromWorld(Component component) {
		boolean bl = this.isLocalServer();
		ServerData serverData = this.getCurrentServer();
		if (this.level != null) {
			this.level.disconnect(component);
		}

		if (bl) {
			this.disconnectWithSavingScreen();
		} else {
			this.disconnectWithProgressScreen();
		}

		TitleScreen titleScreen = new TitleScreen();
		if (bl) {
			this.setScreen(titleScreen);
		} else {
			this.setScreen(new JoinMultiplayerScreen(titleScreen));
		}
	}

	public void disconnectWithSavingScreen() {
		this.disconnect(new GenericMessageScreen(SAVING_LEVEL), false);
	}

	public void disconnectWithProgressScreen() {
		this.disconnect(new ProgressScreen(true), false);
	}

	public void disconnect(Screen screen, boolean bl) {
		ClientPacketListener clientPacketListener = this.getConnection();
		if (clientPacketListener != null) {
			this.dropAllTasks();
			clientPacketListener.close();
			if (!bl) {
				this.clearDownloadedResourcePacks();
			}
		}

		this.playerSocialManager.stopOnlineMode();
		if (this.metricsRecorder.isRecording()) {
			this.debugClientMetricsCancel();
		}

		IntegratedServer integratedServer = this.singleplayerServer;
		this.singleplayerServer = null;
		this.gameRenderer.resetData();
		this.gameMode = null;
		this.clientLevelTeardownInProgress = true;

		try {
			if (this.level != null) {
				this.gui.onDisconnected();
			}

			if (integratedServer != null) {
				this.setScreen(new GenericMessageScreen(SAVING_LEVEL));
				ProfilerFiller profilerFiller = Profiler.get();
				profilerFiller.push("waitForServer");

				while (!integratedServer.isShutdown()) {
					this.runTick(false);
				}

				profilerFiller.pop();
			}

			this.setScreenAndShow(screen);
			this.isLocalServer = false;
			this.level = null;
			this.updateLevelInEngines(null);
			this.player = null;
		} finally {
			this.clientLevelTeardownInProgress = false;
		}
	}

	public void clearDownloadedResourcePacks() {
		this.downloadedPackSource.cleanupAfterDisconnect();
		this.runAllTasks();
	}

	public void clearClientLevel(Screen screen) {
		ClientPacketListener clientPacketListener = this.getConnection();
		if (clientPacketListener != null) {
			clientPacketListener.clearLevel();
		}

		if (this.metricsRecorder.isRecording()) {
			this.debugClientMetricsCancel();
		}

		this.gameRenderer.resetData();
		this.gameMode = null;
		this.clientLevelTeardownInProgress = true;

		try {
			this.setScreenAndShow(screen);
			this.gui.onDisconnected();
			this.level = null;
			this.updateLevelInEngines(null);
			this.player = null;
		} finally {
			this.clientLevelTeardownInProgress = false;
		}
	}

	public void setScreenAndShow(Screen screen) {
		Zone zone = Profiler.get().zone("forcedTick");

		try {
			this.setScreen(screen);
			this.runTick(false);
		} catch (Throwable var6) {
			if (zone != null) {
				try {
					zone.close();
				} catch (Throwable var5) {
					var6.addSuppressed(var5);
				}
			}

			throw var6;
		}

		if (zone != null) {
			zone.close();
		}
	}

	private void updateLevelInEngines(@Nullable ClientLevel clientLevel) {
		this.soundManager.stop();
		this.setCameraEntity(null);
		this.pendingConnection = null;
		this.levelRenderer.setLevel(clientLevel);
		this.particleEngine.setLevel(clientLevel);
		this.gameRenderer.setLevel(clientLevel);
		this.updateTitle();
	}

	private UserProperties userProperties() {
		return (UserProperties)this.userPropertiesFuture.join();
	}

	public boolean allowsMultiplayer() {
		return this.allowsMultiplayer && this.userProperties().flag(UserFlag.SERVERS_ALLOWED) && this.multiplayerBan() == null && !this.isNameBanned();
	}

	@Nullable
	public BanDetails multiplayerBan() {
		return (BanDetails)this.userProperties().bannedScopes().get("MULTIPLAYER");
	}

	public boolean isNameBanned() {
		ProfileResult profileResult = (ProfileResult)this.profileFuture.getNow(null);
		return profileResult != null && profileResult.actions().contains(ProfileActionType.FORCED_NAME_CHANGE);
	}

	public boolean isBlocked(UUID uUID) {
		return this.getChatStatus().isChatAllowed(false)
			? this.playerSocialManager.shouldHideMessageFrom(uUID)
			: (this.player == null || !uUID.equals(this.player.getUUID())) && !uUID.equals(Util.NIL_UUID);
	}

	public Minecraft.ChatStatus getChatStatus() {
		if (this.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
			return Minecraft.ChatStatus.DISABLED_BY_OPTIONS;
		} else if (!this.allowsChat) {
			return Minecraft.ChatStatus.DISABLED_BY_LAUNCHER;
		} else {
			return !this.userProperties().flag(UserFlag.CHAT_ALLOWED) ? Minecraft.ChatStatus.DISABLED_BY_PROFILE : Minecraft.ChatStatus.ENABLED;
		}
	}

	public final boolean isDemo() {
		return false;
	}

	public final boolean canSwitchGameMode() {
		return this.player != null && this.gameMode != null;
	}

	@Nullable
	public ClientPacketListener getConnection() {
		return this.player == null ? null : this.player.connection;
	}

	public static boolean renderNames() {
		return !instance.options.hideGui;
	}

	public static boolean useFancyGraphics() {
		return instance.options.graphicsMode().get().getId() >= GraphicsStatus.FANCY.getId();
	}

	public static boolean useShaderTransparency() {
		return !instance.gameRenderer.isPanoramicMode() && instance.options.graphicsMode().get().getId() >= GraphicsStatus.FABULOUS.getId();
	}

	public static boolean useAmbientOcclusion() {
		return instance.options.ambientOcclusion().get();
	}

	private void pickBlock() {
		if (this.hitResult != null && this.hitResult.getType() != Type.MISS) {
			boolean bl = this.hasControlDown();
			switch (this.hitResult) {
				case BlockHitResult blockHitResult:
					this.gameMode.handlePickItemFromBlock(blockHitResult.getBlockPos(), bl);
					break;
				case EntityHitResult entityHitResult:
					this.gameMode.handlePickItemFromEntity(entityHitResult.getEntity(), bl);
					break;
				default:
			}
		}
	}

	public CrashReport fillReport(CrashReport crashReport) {
		SystemReport systemReport = crashReport.getSystemReport();

		try {
			fillSystemReport(systemReport, this, this.languageManager, this.launchedVersion, this.options);
			this.fillUptime(crashReport.addCategory("Uptime"));
			if (this.level != null) {
				this.level.fillReportDetails(crashReport);
			}

			if (this.singleplayerServer != null) {
				this.singleplayerServer.fillSystemReport(systemReport);
			}

			this.reloadStateTracker.fillCrashReport(crashReport);
		} catch (Throwable var4) {
			LOGGER.error("Failed to collect details", var4);
		}

		return crashReport;
	}

	public static void fillReport(
		@Nullable Minecraft minecraft, @Nullable LanguageManager languageManager, String string, @Nullable Options options, CrashReport crashReport
	) {
		SystemReport systemReport = crashReport.getSystemReport();
		fillSystemReport(systemReport, minecraft, languageManager, string, options);
	}

	private static String formatSeconds(double d) {
		return String.format(Locale.ROOT, "%.3fs", d);
	}

	private void fillUptime(CrashReportCategory crashReportCategory) {
		crashReportCategory.setDetail("JVM uptime", () -> formatSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
		crashReportCategory.setDetail("Wall uptime", () -> formatSeconds((System.currentTimeMillis() - this.clientStartTimeMs) / 1000.0));
		crashReportCategory.setDetail("High-res time", () -> formatSeconds(Util.getMillis() / 1000.0));
		crashReportCategory.setDetail("Client ticks", () -> String.format(Locale.ROOT, "%d ticks / %.3fs", this.clientTickCount, this.clientTickCount / 20.0));
	}

	private static SystemReport fillSystemReport(
		SystemReport systemReport, @Nullable Minecraft minecraft, @Nullable LanguageManager languageManager, String string, @Nullable Options options
	) {
		systemReport.setDetail("Launched Version", () -> string);
		String string2 = getLauncherBrand();
		if (string2 != null) {
			systemReport.setDetail("Launcher name", string2);
		}

		systemReport.setDetail("Backend library", RenderSystem::getBackendDescription);
		systemReport.setDetail("Backend API", RenderSystem::getApiDescription);
		systemReport.setDetail("Window size", () -> minecraft != null ? minecraft.window.getWidth() + "x" + minecraft.window.getHeight() : "<not initialized>");
		systemReport.setDetail("GFLW Platform", Window::getPlatform);
		systemReport.setDetail("Render Extensions", () -> String.join(", ", RenderSystem.getDevice().getEnabledExtensions()));
		systemReport.setDetail("GL debug messages", () -> {
			GpuDevice gpuDevice = RenderSystem.tryGetDevice();
			if (gpuDevice == null) {
				return "<no renderer available>";
			} else {
				return gpuDevice.isDebuggingEnabled() ? String.join("\n", gpuDevice.getLastDebugMessages()) : "<debugging unavailable>";
			}
		});
		systemReport.setDetail("Is Modded", () -> checkModStatus().fullDescription());
		systemReport.setDetail("Universe", () -> minecraft != null ? Long.toHexString(minecraft.canary) : "404");
		systemReport.setDetail("Type", "Client (map_client.txt)");
		if (options != null) {
			if (minecraft != null) {
				String string3 = minecraft.getGpuWarnlistManager().getAllWarnings();
				if (string3 != null) {
					systemReport.setDetail("GPU Warnings", string3);
				}
			}

			systemReport.setDetail("Graphics mode", options.graphicsMode().get().toString());
			systemReport.setDetail("Render Distance", options.getEffectiveRenderDistance() + "/" + options.renderDistance().get() + " chunks");
		}

		if (minecraft != null) {
			systemReport.setDetail("Resource Packs", () -> PackRepository.displayPackList(minecraft.getResourcePackRepository().getSelectedPacks()));
		}

		if (languageManager != null) {
			systemReport.setDetail("Current Language", () -> languageManager.getSelected());
		}

		systemReport.setDetail("Locale", String.valueOf(Locale.getDefault()));
		systemReport.setDetail("System encoding", () -> System.getProperty("sun.jnu.encoding", "<not set>"));
		systemReport.setDetail("File encoding", () -> System.getProperty("file.encoding", "<not set>"));
		systemReport.setDetail("CPU", GLX::_getCpuInfo);
		return systemReport;
	}

	public static Minecraft getInstance() {
		return instance;
	}

	public CompletableFuture<Void> delayTextureReload() {
		return this.submit(() -> this.reloadResourcePacks()).thenCompose(completableFuture -> completableFuture);
	}

	@Nullable
	public ServerData getCurrentServer() {
		return (ServerData)Optionull.map(this.getConnection(), ClientPacketListener::getServerData);
	}

	public boolean isLocalServer() {
		return this.isLocalServer;
	}

	public boolean hasSingleplayerServer() {
		return this.isLocalServer && this.singleplayerServer != null;
	}

	@Nullable
	public IntegratedServer getSingleplayerServer() {
		return this.singleplayerServer;
	}

	public boolean isSingleplayer() {
		IntegratedServer integratedServer = this.getSingleplayerServer();
		return integratedServer != null && !integratedServer.isPublished();
	}

	public boolean isLocalPlayer(UUID uUID) {
		return uUID.equals(this.getUser().getProfileId());
	}

	public User getUser() {
		return this.user;
	}

	public PlayerProfile getGameProfile() {
		// Offline mode - create profile from local user info
		return new PlayerProfile(this.user.getProfileId(), this.user.getName());
	}

	public Proxy getProxy() {
		return this.proxy;
	}

	public TextureManager getTextureManager() {
		return this.textureManager;
	}

	public ShaderManager getShaderManager() {
		return this.shaderManager;
	}

	public ResourceManager getResourceManager() {
		return this.resourceManager;
	}

	public PackRepository getResourcePackRepository() {
		return this.resourcePackRepository;
	}

	public VanillaPackResources getVanillaPackResources() {
		return this.vanillaPackResources;
	}

	public DownloadedPackSource getDownloadedPackSource() {
		return this.downloadedPackSource;
	}

	public Path getResourcePackDirectory() {
		return this.resourcePackDirectory;
	}

	public LanguageManager getLanguageManager() {
		return this.languageManager;
	}

	public boolean isPaused() {
		return this.pause;
	}

	public GpuWarnlistManager getGpuWarnlistManager() {
		return this.gpuWarnlistManager;
	}

	public SoundManager getSoundManager() {
		return this.soundManager;
	}

	public MusicInfo getSituationalMusic() {
		Music music = (Music)Optionull.map(this.screen, Screen::getBackgroundMusic);
		if (music != null) {
			return new MusicInfo(music);
		} else if (this.player == null) {
			return new MusicInfo(Musics.MENU);
		} else {
			Level level = this.player.level();
			if (level.dimension() == Level.END) {
				return this.gui.getBossOverlay().shouldPlayMusic() ? new MusicInfo(Musics.END_BOSS) : new MusicInfo(Musics.END);
			} else {
				Holder<Biome> holder = level.getBiome(this.player.blockPosition());
				Biome biome = (Biome)holder.value();
				float f = biome.getBackgroundMusicVolume();
				Optional<WeightedList<Music>> optional = biome.getBackgroundMusic();
				if (optional.isPresent()) {
					Optional<Music> optional2 = ((WeightedList)optional.get()).getRandom(level.random);
					return new MusicInfo((Music)optional2.orElse(null), f);
				} else if (!this.musicManager.isPlayingMusic(Musics.UNDER_WATER) && (!this.player.isUnderWater() || !holder.is(BiomeTags.PLAYS_UNDERWATER_MUSIC))) {
					return level.dimension() != Level.NETHER && this.player.getAbilities().instabuild && this.player.getAbilities().mayfly
						? new MusicInfo(Musics.CREATIVE, f)
						: new MusicInfo(Musics.GAME, f);
				} else {
					return new MusicInfo(Musics.UNDER_WATER, f);
				}
			}
		}
	}

	public Services services() {
		return this.services;
	}

	public SkinManager getSkinManager() {
		return this.skinManager;
	}

	public net.minecraft.client.resources.SkinLoader getSkinLoader() {
		return this.skinLoader;
	}

	@Nullable
	public Entity getCameraEntity() {
		return this.cameraEntity;
	}

	public void setCameraEntity(@Nullable Entity entity) {
		this.cameraEntity = entity;
		this.gameRenderer.checkEntityPostEffect(entity);
	}

	public boolean shouldEntityAppearGlowing(Entity entity) {
		return entity.isCurrentlyGlowing()
			|| this.player != null && this.player.isSpectator() && this.options.keySpectatorOutlines.isDown() && entity.getType() == EntityType.PLAYER;
	}

	protected Thread getRunningThread() {
		return this.gameThread;
	}

	public Runnable wrapRunnable(Runnable runnable) {
		return runnable;
	}

	protected boolean shouldRun(Runnable runnable) {
		return true;
	}

	public BlockRenderDispatcher getBlockRenderer() {
		return this.blockRenderer;
	}

	public EntityRenderDispatcher getEntityRenderDispatcher() {
		return this.entityRenderDispatcher;
	}

	public BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
		return this.blockEntityRenderDispatcher;
	}

	public ItemRenderer getItemRenderer() {
		return this.itemRenderer;
	}

	public MapRenderer getMapRenderer() {
		return this.mapRenderer;
	}

	public DataFixer getFixerUpper() {
		return this.fixerUpper;
	}

	public DeltaTracker getDeltaTracker() {
		return this.deltaTracker;
	}

	public BlockColors getBlockColors() {
		return this.blockColors;
	}

	public boolean showOnlyReducedInfo() {
		return this.player != null && this.player.isReducedDebugInfo() || this.options.reducedDebugInfo().get();
	}

	public ToastManager getToastManager() {
		return this.toastManager;
	}

	public Tutorial getTutorial() {
		return this.tutorial;
	}

	public boolean isWindowActive() {
		return this.windowActive;
	}

	public HotbarManager getHotbarManager() {
		return this.hotbarManager;
	}

	public ModelManager getModelManager() {
		return this.modelManager;
	}

	public AtlasManager getAtlasManager() {
		return this.atlasManager;
	}

	public MapTextureManager getMapTextureManager() {
		return this.mapTextureManager;
	}

	public WaypointStyleManager getWaypointStyles() {
		return this.waypointStyles;
	}

	@Override
	public void setWindowActive(boolean bl) {
		this.windowActive = bl;
	}

	public Component grabPanoramixScreenshot(File file) {
		int i = 4;
		int j = 4096;
		int k = 4096;
		int l = this.window.getWidth();
		int m = this.window.getHeight();
		RenderTarget renderTarget = this.getMainRenderTarget();
		float f = this.player.getXRot();
		float g = this.player.getYRot();
		float h = this.player.xRotO;
		float n = this.player.yRotO;
		this.gameRenderer.setRenderBlockOutline(false);

		MutableComponent var13;
		try {
			this.gameRenderer.setPanoramicMode(true);
			this.window.setWidth(4096);
			this.window.setHeight(4096);
			renderTarget.resize(4096, 4096);

			for (int o = 0; o < 6; o++) {
				switch (o) {
					case 0:
						this.player.setYRot(g);
						this.player.setXRot(0.0F);
						break;
					case 1:
						this.player.setYRot((g + 90.0F) % 360.0F);
						this.player.setXRot(0.0F);
						break;
					case 2:
						this.player.setYRot((g + 180.0F) % 360.0F);
						this.player.setXRot(0.0F);
						break;
					case 3:
						this.player.setYRot((g - 90.0F) % 360.0F);
						this.player.setXRot(0.0F);
						break;
					case 4:
						this.player.setYRot(g);
						this.player.setXRot(-90.0F);
						break;
					case 5:
					default:
						this.player.setYRot(g);
						this.player.setXRot(90.0F);
				}

				this.player.yRotO = this.player.getYRot();
				this.player.xRotO = this.player.getXRot();
				this.gameRenderer.renderLevel(DeltaTracker.ONE);

				try {
					Thread.sleep(10L);
				} catch (InterruptedException var18) {
				}

				Screenshot.grab(file, "panorama_" + o + ".png", renderTarget, 4, component -> {});
			}

			Component component = Component.literal(file.getName())
				.withStyle(ChatFormatting.UNDERLINE)
				.withStyle(style -> style.withClickEvent(new OpenFile(file.getAbsoluteFile())));
			return Component.translatable("screenshot.success", new Object[]{component});
		} catch (Exception var19) {
			LOGGER.error("Couldn't save image", (Throwable)var19);
			var13 = Component.translatable("screenshot.failure", new Object[]{var19.getMessage()});
		} finally {
			this.player.setXRot(f);
			this.player.setYRot(g);
			this.player.xRotO = h;
			this.player.yRotO = n;
			this.gameRenderer.setRenderBlockOutline(true);
			this.window.setWidth(l);
			this.window.setHeight(m);
			renderTarget.resize(l, m);
			this.gameRenderer.setPanoramicMode(false);
		}

		return var13;
	}

	public SplashManager getSplashManager() {
		return this.splashManager;
	}

	@Nullable
	public Overlay getOverlay() {
		return this.overlay;
	}

	public PlayerSocialManager getPlayerSocialManager() {
		return this.playerSocialManager;
	}

	public Window getWindow() {
		return this.window;
	}

	public FramerateLimitTracker getFramerateLimitTracker() {
		return this.framerateLimitTracker;
	}

	public DebugScreenOverlay getDebugOverlay() {
		return this.gui.getDebugOverlay();
	}

	public RenderBuffers renderBuffers() {
		return this.renderBuffers;
	}

	public void updateMaxMipLevel(int i) {
		this.atlasManager.updateMaxMipLevel(i);
	}

	public EntityModelSet getEntityModels() {
		return (EntityModelSet)this.modelManager.entityModels().get();
	}

	public boolean isTextFilteringEnabled() {
		return this.userProperties().flag(UserFlag.PROFANITY_FILTER_ENABLED);
	}

	public void prepareForMultiplayer() {
		this.playerSocialManager.startOnlineMode();
		this.getProfileKeyPairManager().prepareKeyPair();
	}

	public InputType getLastInputType() {
		return this.lastInputType;
	}

	public void setLastInputType(InputType inputType) {
		this.lastInputType = inputType;
	}

	public ChatListener getChatListener() {
		return this.chatListener;
	}

	public QuickPlayLog quickPlayLog() {
		return this.quickPlayLog;
	}

	public CommandHistory commandHistory() {
		return this.commandHistory;
	}

	public DirectoryValidator directoryValidator() {
		return this.directoryValidator;
	}

	public PlayerSkinRenderCache playerSkinRenderCache() {
		return this.playerSkinRenderCache;
	}

	private float getTickTargetMillis(float f) {
		if (this.level != null) {
			TickRateManager tickRateManager = this.level.tickRateManager();
			if (tickRateManager.runsNormally()) {
				return Math.max(f, tickRateManager.millisecondsPerTick());
			}
		}

		return f;
	}

	public ItemModelResolver getItemModelResolver() {
		return this.itemModelResolver;
	}

	public boolean canInterruptScreen() {
		return (this.screen == null || this.screen.canInterruptWithAnotherScreen()) && !this.clientLevelTeardownInProgress;
	}

	@Nullable
	public static String getLauncherBrand() {
		return System.getProperty("minecraft.launcher.brand");
	}

	public PacketProcessor packetProcessor() {
		return this.packetProcessor;
	}

	@Environment(EnvType.CLIENT)
	public static enum ChatStatus {
		ENABLED(CommonComponents.EMPTY) {
			@Override
			public boolean isChatAllowed(boolean bl) {
				return true;
			}
		},
		DISABLED_BY_OPTIONS(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED)) {
			@Override
			public boolean isChatAllowed(boolean bl) {
				return false;
			}
		},
		DISABLED_BY_LAUNCHER(Component.translatable("chat.disabled.launcher").withStyle(ChatFormatting.RED)) {
			@Override
			public boolean isChatAllowed(boolean bl) {
				return bl;
			}
		},
		DISABLED_BY_PROFILE(
			Component.translatable("chat.disabled.profile", new Object[]{Component.keybind(Minecraft.instance.options.keyChat.getName())}).withStyle(ChatFormatting.RED)
		) {
			@Override
			public boolean isChatAllowed(boolean bl) {
				return bl;
			}
		};

		static final Component INFO_DISABLED_BY_PROFILE = Component.translatable("chat.disabled.profile.moreInfo");
		private final Component message;

		ChatStatus(final Component component) {
			this.message = component;
		}

		public Component getMessage() {
			return this.message;
		}

		public abstract boolean isChatAllowed(boolean bl);
	}

	@Environment(EnvType.CLIENT)
	record GameLoadCookie(GameConfig.QuickPlayData quickPlayData) {
	}
}
