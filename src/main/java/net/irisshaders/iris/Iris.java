package net.irisshaders.iris;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.helpers.StringPair;
import net.blaze3d.platform.InputConstants;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.sodium.api.vertex.serializer.VertexSerializerRegistry;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gui.debug.DebugLoadFailedGridScreen;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.discovery.ShaderpackDirectoryManager;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.Profile;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipException;

public class Iris {
	public static final String MODID = "iris";

	/**
	 * The user-facing name of the mod. Moved into a constant to facilitate
	 * easy branding changes (for forks). You'll still need to change this
	 * separately in mixin plugin classes & the language files.
	 */
	public static final String MODNAME = "Iris";
	public static final IrisLogging logger = new IrisLogging(MODNAME);
	public static final boolean IS_FOOL;
	private static final Map<String, String> shaderPackOptionQueue = new HashMap<>();
	// Change this for snapshots!
	private static final String backupVersionNumber = "1.21.9";
	public static NamespacedId lastDimension = null;
	public static boolean testing = false;
	private static Path shaderpacksDirectory;
	private static ShaderpackDirectoryManager shaderpacksDirectoryManager;
	private static ShaderPack currentPack;
	/**
	 * Parsed pack used only by the shader-pack screens on the Rust Vulkan route.
	 * It is CPU-side option/menu semantics; Rust collects its own copy of the
	 * source and never receives this object or any Iris pipeline state.
	 */
	private static ShaderPack vulkanMenuPack;
	private static boolean vulkanMenuPackLoaded;
	private static String currentPackName;
	private static Optional<Exception> storedError = Optional.empty();
	private static boolean initialized;
	private static IrisConfig irisConfig;
	private static FileSystem zipFileSystem;
	private static final KeyMapping.Category irisKeybindCategory = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("iris", "keybinds"));
	private static KeyMapping toggleShadersKeybind;
	private static KeyMapping shaderpackScreenKeybind;
	private static KeyMapping wireframeKeybind;
	// Flag variable used when reloading
	// Used in favor of queueDefaultShaderPackOptionValues() for resetting as the
	// behavior is more concrete and therefore is more likely to repair a user's issues
	private static boolean resetShaderPackOptions = false;
	private static String IRIS_VERSION;
	private static boolean fallback;
	private static boolean loadShaderPackWhenPossible;

	static {
		if (!BuildConfig.ACTIVATE_RENDERDOC && IrisPlatformHelpers.getInstance().isDevelopmentEnvironment() && System.getProperty("user.name").contains("ims") && Util.getPlatform() == Util.OS.LINUX) {
		}

		Calendar c = Calendar.getInstance();
		IS_FOOL = c.get(Calendar.MONTH) == Calendar.APRIL && c.get(Calendar.DAY_OF_MONTH) == 1;
	}

	/**
	 * Called when the title screen is initialized for the first time.
	 */
	public static void onLoadingComplete() {
		if (!initialized) {
			Iris.logger.warn("Iris::onLoadingComplete was called, but Iris::onEarlyInitialize was not called." +
				" Trying to avoid a crash but this is an odd state.");
			return;
		}

		lastDimension = DimensionId.OVERWORLD;
	}

	public static void handleKeybinds(Minecraft minecraft) {
		if (loadShaderPackWhenPossible) {
			loadShaderPackWhenPossible = false;
			Iris.loadShaderpack();
		}

		if (toggleShadersKeybind.consumeClick()) {
			try {
				toggleShaders(minecraft, !irisConfig.areShadersEnabled());
			} catch (Exception e) {
				logger.error("Error while toggling shaders!", e);

				if (minecraft.player != null) {
					minecraft.player.displayClientMessage(Component.translatable("iris.shaders.toggled.failure", Throwables.getRootCause(e).getMessage()).withStyle(ChatFormatting.RED), false);
				}
				setShadersDisabled();
				fallback = true;
			}
		} else if (shaderpackScreenKeybind.consumeClick()) {
			minecraft.setScreen(new ShaderPackScreen(null));
		} else if (wireframeKeybind.consumeClick()) {
			if (irisConfig.areDebugOptionsEnabled() && minecraft.player != null && !Minecraft.getInstance().isLocalServer()) {
				minecraft.player.displayClientMessage(Component.literal("No cheating; wireframe only in singleplayer!"), false);
			}
		}
	}

	public static void toggleShaders(Minecraft minecraft, boolean enabled) throws IOException {
		irisConfig.setShadersEnabled(enabled);
		irisConfig.save();

		reload();
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(enabled ? Component.translatable("iris.shaders.toggled", currentPackName) : Component.translatable("iris.shaders.disabled"), false);
		}
	}

	public static void loadShaderpack() {
		if (irisConfig == null) {
			if (!initialized) {
				throw new IllegalStateException("Iris::loadShaderpack was called, but Iris::onInitializeClient wasn't" +
					" called yet. How did this happen?");
			} else {
				throw new NullPointerException("Iris.irisConfig was null unexpectedly");
			}
		}

		if (!irisConfig.areShadersEnabled()) {
			logger.info("Shaders are disabled because enableShaders is set to false in iris.properties");

			setShadersDisabled();

			return;
		}

		// Attempt to load an external shaderpack if it is available
		Optional<String> externalName = irisConfig.getShaderPackName();

		if (externalName.isEmpty()) {
			logger.info("Shaders are disabled because no valid shaderpack is selected");

			setShadersDisabled();

			return;
		}

		if (!loadExternalShaderpack(externalName.get())) {
			logger.warn("Falling back to normal rendering without shaders because the shaderpack could not be loaded");
			setShadersDisabled();
			fallback = true;
		}
	}

	@SuppressWarnings("unchecked")
	private static boolean loadExternalShaderpack(String name) {
		Path shaderPackRoot;
		Path shaderPackConfigTxt;

		try {
			shaderPackRoot = getShaderpacksDirectory().resolve(name);
			shaderPackConfigTxt = getShaderpacksDirectory().resolve(name + ".txt");
		} catch (InvalidPathException e) {
			logger.error("Failed to load the shaderpack \"{}\" because it contains invalid characters in its path", name);

			return false;
		}

		if (!isValidShaderpack(shaderPackRoot)) {
			logger.error("Pack \"{}\" is not valid! Can't load it.", name);
			return false;
		}

		Path shaderPackPath;
		boolean isZip = false;

		if (!Files.isDirectory(shaderPackRoot) && shaderPackRoot.toString().endsWith(".zip")) {
			Optional<Path> optionalPath;

			try {
				optionalPath = loadExternalZipShaderpack(shaderPackRoot);
			} catch (FileSystemNotFoundException | NoSuchFileException e) {
				logger.error("Failed to load the shaderpack \"{}\" because it does not exist in your shaderpacks folder!", name);

				return false;
			} catch (ZipException e) {
				logger.error("The shaderpack \"{}\" appears to be corrupted, please try downloading it again!", name);

				return false;
			} catch (IOException e) {
				logger.error("Failed to load the shaderpack \"{}\"!", name);
				logger.error("", e);

				return false;
			}

			if (optionalPath.isPresent()) {
				shaderPackPath = optionalPath.get();
			} else {
				logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
				return false;
			}
			isZip = true;
		} else {
			if (!Files.exists(shaderPackRoot)) {
				logger.error("Failed to load the shaderpack \"{}\" because it does not exist!", name);
				return false;
			}

			// If it's a folder-based shaderpack, just use the shaders subdirectory
			shaderPackPath = shaderPackRoot.resolve("shaders");
		}

		if (!Files.exists(shaderPackPath)) {
			logger.error("Could not load the shaderpack \"{}\" because it appears to lack a \"shaders\" directory", name);
			return false;
		}

		Map<String, String> changedConfigs = tryReadConfigProperties(shaderPackConfigTxt)
			.map(properties -> (Map<String, String>) (Object) properties)
			.orElse(new HashMap<>());

		changedConfigs.putAll(shaderPackOptionQueue);
		clearShaderPackOptionQueue();

		if (resetShaderPackOptions) {
			changedConfigs.clear();
		}
		resetShaderPackOptions = false;

		try {
			ShaderPack pack = new ShaderPack(shaderPackPath, changedConfigs, vulkanMenuEnvironmentDefines(), isZip);
			vulkanMenuPack = pack;

			MutableOptionValues changedConfigsValues = pack.getShaderPackOptions().getOptionValues().mutableCopy();

			// Store changed values from those currently in use by the shader pack
			Properties configsToSave = new Properties();
			changedConfigsValues.getBooleanValues().forEach((k, v) -> configsToSave.setProperty(k, Boolean.toString(v)));
			changedConfigsValues.getStringValues().forEach(configsToSave::setProperty);

			tryUpdateConfigPropertiesFile(shaderPackConfigTxt, configsToSave);
		} catch (Exception e) {
			logger.error("Failed to load the shaderpack \"{}\"!", name);
			logger.error("", e);
			handleException(e);

			return false;
		}

		fallback = false;
		currentPackName = name;

		logger.info("Using shaderpack: " + name);

		return true;
	}

	private static void handleException(Exception e) {
		if (irisConfig.areDebugOptionsEnabled()) {
			Minecraft.getInstance().setScreen(new DebugLoadFailedGridScreen(Minecraft.getInstance().screen, Component.literal(e instanceof ShaderCompileException ? "Failed to compile shaders" : "Exception"), e));
		} else {
			if (Minecraft.getInstance().player != null) {
				Minecraft.getInstance().player.displayClientMessage(Component.translatable(e instanceof ShaderCompileException ? "iris.load.failure.shader" : "iris.load.failure.generic").append(Component.literal("Copy Info").withStyle(arg -> arg.withUnderlined(true).withColor(
					ChatFormatting.BLUE).withClickEvent(new ClickEvent.CopyToClipboard(e.getMessage())).withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click"))))), false);
			} else {
				storedError = Optional.of(e);
			}
		}
	}

	private static Optional<Path> loadExternalZipShaderpack(Path shaderpackPath) throws IOException {
		FileSystem zipSystem = FileSystems.newFileSystem(shaderpackPath, Iris.class.getClassLoader());
		zipFileSystem = zipSystem;

		// Should only be one root directory for a zip shaderpack
		Path root = zipSystem.getRootDirectories().iterator().next();

		Path potentialShaderDir = zipSystem.getPath("shaders");

		// If the shaders dir was immediately found return it
		// Otherwise, manually search through each directory path until it ends with "shaders"
		if (Files.exists(potentialShaderDir)) {
			return Optional.of(potentialShaderDir);
		}

		// Sometimes shaderpacks have their shaders directory within another folder in the shaderpack
		// For example Sildurs-Vibrant-Shaders.zip/shaders
		// While other packs have Trippy-Shaderpack-master.zip/Trippy-Shaderpack-master/shaders
		// This makes it hard to determine what is the actual shaders dir
		try (Stream<Path> stream = Files.walk(root)) {
			return stream
				.filter(Files::isDirectory)
				.filter(path -> path.endsWith("shaders"))
				.findFirst();
		}
	}

	/**
	 * Environment for parsing pack options on the Rust Vulkan route. Uses the
	 * same engine defines Rust receives instead of querying Java GPU state.
	 */
	private static ImmutableList<StringPair> vulkanMenuEnvironmentDefines() {
		ImmutableList.Builder<StringPair> defines = ImmutableList.builder();
		net.vulkanic.shaderpack.RustShaderPackSourceCollector.engineEnvironmentDefines()
			.forEach((key, value) -> defines.add(new StringPair(key, value)));
		return defines.build();
	}

	private static void closeZipFileSystem() {
		if (zipFileSystem == null) {
			return;
		}
		try {
			zipFileSystem.close();
		} catch (IOException e) {
			logger.warn("Failed to close the shaderpack zip before reloading", e);
		}
		zipFileSystem = null;
	}

	private static void setShadersDisabled() {
		currentPack = null;
		vulkanMenuPack = null;
		fallback = false;
		currentPackName = "(off)";
	}

	public static void setDebug(boolean enable) {
		try {
			irisConfig.setDebugEnabled(enable);
			irisConfig.save();
		} catch (IOException e) {
			Iris.logger.fatal("Failed to save config!", e);
		}

		// Rust VulkanicGAL owns graphics-API debug output; this toggles Iris's
		// verbose logging and persists the preference.
		int success = 1;

		logger.info("Debug functionality is " + (enable ? "enabled, logging will be more verbose!" : "disabled."));
		if (Minecraft.getInstance().player != null) {
			if (IrisPlatformHelpers.getInstance().useELS()) {
				Minecraft.getInstance().player.displayClientMessage(Component.translatable("iris.shaders.debug.restartNoDebug"), false);
			} else {
				Minecraft.getInstance().player.displayClientMessage(Component.translatable(success != 0 ? (enable ? "iris.shaders.debug.enabled" : "iris.shaders.debug.disabled") : "iris.shaders.debug.failure"), false);
			}

			if (success == 2 && !IrisPlatformHelpers.getInstance().useELS()) {
				Minecraft.getInstance().player.displayClientMessage(Component.translatable("iris.shaders.debug.restart"), false);
			}
		}
	}

	private static Optional<Properties> tryReadConfigProperties(Path path) {
		Properties properties = new Properties();

		if (Files.exists(path)) {
			try (InputStream is = Files.newInputStream(path)) {
				// NB: config properties are specified to be encoded with ISO-8859-1 by OptiFine,
				//     so we don't need to do the UTF-8 workaround here.
				properties.load(is);
			} catch (IOException e) {
				// TODO: Better error handling
				return Optional.empty();
			}
		}

		return Optional.of(properties);
	}

	private static void tryUpdateConfigPropertiesFile(Path path, Properties properties) {
		try {
			if (properties.isEmpty()) {
				// Delete the file or don't create it if there are no changed configs
				if (Files.exists(path)) {
					Files.delete(path);
				}

				return;
			}

			try (OutputStream out = Files.newOutputStream(path)) {
				properties.store(out, null);
			}
		} catch (IOException e) {
			// TODO: Better error handling
		}
	}

	public static boolean isValidToShowPack(Path pack) {
		return Files.isDirectory(pack) || pack.toString().endsWith(".zip");
	}

	public static boolean isValidShaderpack(Path pack) {
		if (Files.isDirectory(pack)) {
			// Sometimes the shaderpack directory itself can be
			// identified as a shader pack due to it containing
			// folders which contain "shaders" folders, this is
			// necessary to check against that
			if (pack.equals(getShaderpacksDirectory())) {
				return false;
			}
			return pack.resolve("shaders").toFile().exists();
		}

		if (pack.toString().endsWith(".zip")) {
			try (FileSystem zipSystem = FileSystems.newFileSystem(pack, Iris.class.getClassLoader())) {
				Path root = zipSystem.getRootDirectories().iterator().next();
				try (Stream<Path> stream = Files.walk(root)) {
					return stream
						.filter(Files::isDirectory)
						.anyMatch(path -> path.endsWith("shaders"));
				}
			} catch (IOException ignored) {
				// ignored, not a valid shader pack.
			}
		}

		return false;
	}

	public static Map<String, String> getShaderPackOptionQueue() {
		return shaderPackOptionQueue;
	}

	public static void queueShaderPackOptionsFromProfile(Profile profile) {
		getShaderPackOptionQueue().putAll(profile.optionValues);
	}

	public static void queueShaderPackOptionsFromProperties(Properties properties) {
		queueDefaultShaderPackOptionValues();

		properties.stringPropertyNames().forEach(key ->
			getShaderPackOptionQueue().put(key, properties.getProperty(key)));
	}

	// Used in favor of resetShaderPackOptions as the aforementioned requires the pack to be reloaded
	public static void queueDefaultShaderPackOptionValues() {
		clearShaderPackOptionQueue();

		getMenuPack().ifPresent(pack -> {
			OptionSet options = pack.getShaderPackOptions().getOptionSet();
			OptionValues values = pack.getShaderPackOptions().getOptionValues();

			options.getStringOptions().forEach((key, mOpt) -> {
				if (values.getStringValue(key).isPresent()) {
					getShaderPackOptionQueue().put(key, mOpt.getOption().getDefaultValue());
				}
			});
			options.getBooleanOptions().forEach((key, mOpt) -> {
				if (values.getBooleanValue(key) != OptionalBoolean.DEFAULT) {
					getShaderPackOptionQueue().put(key, Boolean.toString(mOpt.getOption().getDefaultValue()));
				}
			});
		});
	}

	public static void clearShaderPackOptionQueue() {
		getShaderPackOptionQueue().clear();
	}

	public static void resetShaderPackOptionsOnNextReload() {
		resetShaderPackOptions = true;
	}

	public static boolean shouldResetShaderPackOptionsOnNextReload() {
		return resetShaderPackOptions;
	}

	public static void reload() throws IOException {
		// Rust owns shader-pack discovery and GPU resources on this route. A
		// keybind or resource reload may still reach Iris.reload(), but it must
		// not dereference the skipped Java initialization or enter Java pipeline
		// destruction/loading. Rust observes the persisted configuration on its
		// own generation boundary.
		if (irisConfig != null) {
			irisConfig.initialize();
		}
		currentPack = null;
		fallback = false;
		if (irisConfig != null) {
			// Parse the selected pack for the options screen and persist queued
			// option edits to <pack>.txt, which Rust reads as copied config.
			reloadVulkanMenuPack();
		}
		net.vulkanic.gui.RustGalFrameCoordinator.requestShaderPackSourceRefresh();
		return;
	}

	private static void reloadVulkanMenuPack() {
		vulkanMenuPackLoaded = true;
		vulkanMenuPack = null;
		closeZipFileSystem();
		loadShaderpack();
		currentPack = null;
	}

	/**
	 * The pack whose options the shader-pack screens edit. On OpenGL this is the
	 * active Iris pack; on the Rust Vulkan route it is a CPU-only parse of the
	 * selected pack, loaded on first use.
	 */
	public static Optional<ShaderPack> getMenuPack() {
		if (!vulkanMenuPackLoaded && irisConfig != null) {
			reloadVulkanMenuPack();
		}
		return Optional.ofNullable(vulkanMenuPack);
	}

	public static Optional<ShaderPack> getCurrentPack() {
		return Optional.ofNullable(currentPack);
	}

	public static String getCurrentPackName() {
		return currentPackName;
	}

	public static IrisConfig getIrisConfig() {
		return irisConfig;
	}

	public static boolean isFallback() {
		return fallback;
	}

	public static String getVersion() {
		if (IRIS_VERSION == null) {
			return "Version info unknown!";
		}

		return IRIS_VERSION;
	}

	public static Path getShaderpacksDirectory() {
		if (shaderpacksDirectory == null) {
			shaderpacksDirectory = IrisPlatformHelpers.getInstance().getGameDir().resolve("shaderpacks");
		}

		return shaderpacksDirectory;
	}

	public static ShaderpackDirectoryManager getShaderpacksDirectoryManager() {
		if (shaderpacksDirectoryManager == null) {
			shaderpacksDirectoryManager = new ShaderpackDirectoryManager(getShaderpacksDirectory());
		}

		return shaderpacksDirectoryManager;
	}

	/** True while Rust is running a selected shader pack. */
	public static boolean isPackInUseQuick() {
		return net.vulkanic.gui.RustGalFrameCoordinator.shaderPackSourceActive();
	}

	/**
	 * Called very early on in Minecraft initialization. At this point we *cannot* safely access OpenGL, but we can do
	 * some very basic setup, config loading, and environment checks.
	 *
	 * <p>This is roughly equivalent to Fabric Loader's ClientModInitializer#onInitializeClient entrypoint, except
	 * it's entirely cross platform & we get to decide its exact semantics.</p>
	 *
	 * <p>This is called right before options are loaded, so we can add key bindings here.</p>
	 */
	public void onEarlyInitialize() {
		IRIS_VERSION = IrisPlatformHelpers.getInstance().getVersion();

		toggleShadersKeybind = IrisPlatformHelpers.getInstance().registerKeyBinding(new KeyMapping("iris.keybind.toggleShaders", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, irisKeybindCategory));
		shaderpackScreenKeybind = IrisPlatformHelpers.getInstance().registerKeyBinding(new KeyMapping("iris.keybind.shaderPackSelection", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, irisKeybindCategory));
		wireframeKeybind = IrisPlatformHelpers.getInstance().registerKeyBinding(new KeyMapping("iris.keybind.wireframe", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), irisKeybindCategory));

		try {
			if (!Files.exists(getShaderpacksDirectory())) {
				Files.createDirectories(getShaderpacksDirectory());
			}
		} catch (IOException e) {
			logger.warn("Failed to create the shaderpacks directory!");
			logger.warn("", e);
		}

		irisConfig = new IrisConfig(IrisPlatformHelpers.getInstance().getConfigDir().resolve("iris.properties"), IrisPlatformHelpers.getInstance().getConfigDir().resolve("iris-excluded.json"));

		try {
			irisConfig.initialize();
		} catch (IOException e) {
			logger.error("Failed to initialize Iris configuration, default values will be used instead");
			logger.error("", e);
		}

		initialized = true;
	}
}
