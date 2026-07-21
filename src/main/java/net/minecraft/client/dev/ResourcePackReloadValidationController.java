package net.minecraft.client.dev;

import net.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.ResourcePackDiagnostics;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Development-only validator for the Rust resource-pack byte/index backend.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.resourcePackReloadValidation=true}. The controller
 * drives the normal client resource-reload path with generated directory and ZIP packs,
 * mutates those packs between reloads, and records bounded diagnostics. It deliberately does
 * not own pack ordering, filtering, metadata interpretation, reload listeners, or resources
 * above the existing Java {@link ResourceManager} API.
 */
public final class ResourcePackReloadValidationController {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.resourcePackReloadValidation");
	private static final int MAX_READY_WAIT_TICKS = Math.max(20, Integer.getInteger("mattmc.dev.resourcePackReloadValidation.maxReadyWaitTicks", 2400));
	private static final long RELOAD_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(
		Math.max(10, Integer.getInteger("mattmc.dev.resourcePackReloadValidation.reloadTimeoutSeconds", 120))
	);
	private static final String DIR_PACK_ID = System.getProperty("mattmc.dev.resourcePackReloadValidation.dirPackId", "file/mattmc-validation-dir");
	private static final String ZIP_PACK_ID = System.getProperty("mattmc.dev.resourcePackReloadValidation.zipPackId", "file/mattmc-validation-zip.zip");
	private static final String EXTRA_PACK_ID = System.getProperty("mattmc.dev.resourcePackReloadValidation.extraPackId", "file/mattmc-validation-extra");
	private static final String WORLD = System.getProperty("mattmc.dev.resourcePackReloadValidation.world", "Origin");

	private static Phase phase = ENABLED ? Phase.WAITING_FOR_INITIAL_LOAD : Phase.DISABLED;
	private static int readyWaitTicks;
	private static long reloadStartNanos;
	private static boolean initialHookSeen;
	private static boolean terminal;
	private static Phase queuedReloadPhase;
	private static CompletableFuture<Void> currentReloadFuture;
	private static int currentReloadId;
	private static final AtomicInteger completedReloadId = new AtomicInteger();
	private static final AtomicReference<String> completedReloadFailure = new AtomicReference<>();

	private ResourcePackReloadValidationController() {
	}

	public static boolean isEnabled() {
		return ENABLED;
	}

	public static boolean allowTickDrivenLoadingOverlayFadeIn() {
		return ENABLED;
	}

	public static void beforeTick(Minecraft minecraft) {
		if (!ENABLED || terminal) {
			return;
		}

		ResourcePackDiagnostics.validationClientState(screenName(minecraft), overlayName(minecraft));
		minecraft.options.pauseOnLostFocus = false;
		freezeInput(minecraft.player);
		if (!initialHookSeen) {
			return;
		}
		consumeCompletedReload(minecraft);
		if (phase == Phase.WAITING_FOR_INITIAL_LOAD) {
			if (minecraft.getOverlay() == null) {
				runInitialPhase(minecraft);
			} else if (++readyWaitTicks > MAX_READY_WAIT_TICKS) {
				fail(minecraft, "Timed out waiting for initial loading overlay before resource-pack reload validation");
			}
		} else if (queuedReloadPhase != null && minecraft.getOverlay() == null) {
			Phase nextPhase = queuedReloadPhase;
			queuedReloadPhase = null;
			requestReload(minecraft, nextPhase);
		} else if (phase == Phase.OPENING_WORLD_AFTER_RELOAD || phase == Phase.WAITING_FOR_WORLD_AFTER_RELOAD) {
			if (isWorldReady(minecraft)) {
				complete(minecraft);
			} else if (++readyWaitTicks > MAX_READY_WAIT_TICKS) {
				fail(minecraft, "Timed out waiting for quick-play world after resource-pack reload validation");
			}
		} else if (isReloadRequestedPhase(phase) && reloadStartNanos != 0L && System.nanoTime() - reloadStartNanos > RELOAD_TIMEOUT_NANOS) {
			fail(
				minecraft,
				"Timed out waiting for resource reload completion in phase "
					+ phase.key
					+ " future="
					+ futureState(currentReloadFuture)
					+ " screen="
					+ screenName(minecraft)
					+ " overlay="
					+ overlayName(minecraft)
			);
		}
	}

	public static void afterInitialReload(Minecraft minecraft) {
		if (!ENABLED || terminal) {
			return;
		}
		ResourcePackDiagnostics.validationLifecycle("initial_reload_callback", stateSummary(minecraft));
		initialHookSeen = true;
		setPhase(Phase.WAITING_FOR_INITIAL_LOAD);
	}

	public static void afterResourceReload(Minecraft minecraft) {
		if (!ENABLED || terminal) {
			return;
		}

		ResourcePackDiagnostics.validationLifecycle("minecraft_reload_callback", stateSummary(minecraft));
		completedReloadId.set(currentReloadId);
		completedReloadFailure.set(null);
	}

	public static void onLoadingOverlayTick(boolean reloadDone, boolean fadeIn, long fadeInStart, long fadeOutStart) {
		if (ENABLED && reloadDone) {
			ResourcePackDiagnostics.validationLifecycle(
				"loading_overlay_reload_done",
				"fadeIn=" + fadeIn + " fadeInStart=" + fadeInStart + " fadeOutStart=" + fadeOutStart
			);
		}
	}

	public static void onLoadingOverlayTickFadeStarted(long fadeInStart) {
		if (ENABLED) {
			ResourcePackDiagnostics.validationLifecycle("loading_overlay_tick_fade_started", Long.toString(fadeInStart));
		}
	}

	private static void consumeCompletedReload(Minecraft minecraft) {
		int reloadId = completedReloadId.get();
		if (reloadId == 0 || reloadId != currentReloadId || !isReloadRequestedPhase(phase)) {
			return;
		}
		completedReloadId.compareAndSet(reloadId, 0);
		String failure = completedReloadFailure.getAndSet(null);
		if (failure != null) {
			fail(minecraft, failure);
			return;
		}

		long elapsed = reloadStartNanos == 0L ? 0L : System.nanoTime() - reloadStartNanos;
		ResourcePackDiagnostics.validationReloadCompleted(elapsed);
		try {
			switch (phase) {
				case RELOAD_1_REQUESTED -> {
					setPhase(Phase.RELOAD_1_COMPLETE);
					validateResources(minecraft, "reload_1_complete", false, true, false);
					queueReload(Phase.RELOAD_2_REQUESTED);
				}
				case RELOAD_2_REQUESTED -> {
					setPhase(Phase.RELOAD_2_COMPLETE);
					validateResources(minecraft, "reload_2_complete", false, true, false);
					selectPacks(minecraft, List.of(DIR_PACK_ID));
					setPhase(Phase.ZIP_PACK_DESELECTED);
					queueReload(Phase.RELOAD_3_REQUESTED);
				}
				case RELOAD_3_REQUESTED -> {
					setPhase(Phase.RELOAD_3_COMPLETE);
					validateResources(minecraft, "reload_3_complete", false, false, false);
					modifyPacks(minecraft.getResourcePackDirectory());
					ResourcePackDiagnostics.validationCoverage("directoryModification", "true");
					ResourcePackDiagnostics.validationCoverage("zipReplacement", "true");
					setPhase(Phase.PACK_SET_MODIFIED);
					selectPacks(minecraft, List.of(DIR_PACK_ID, ZIP_PACK_ID, EXTRA_PACK_ID));
					ResourcePackDiagnostics.validationCoverage("packSelection", "true");
					queueReload(Phase.RELOAD_4_REQUESTED);
				}
				case RELOAD_4_REQUESTED -> {
					setPhase(Phase.RELOAD_4_COMPLETE);
					validateResources(minecraft, "reload_4_complete", true, true, true);
					selectPacks(minecraft, List.of(DIR_PACK_ID, ZIP_PACK_ID));
					setPhase(Phase.PACK_SET_RESTORED);
					queueReload(Phase.RELOAD_5_REQUESTED);
				}
				case RELOAD_5_REQUESTED -> {
					setPhase(Phase.RELOAD_5_COMPLETE);
					validateResources(minecraft, "reload_5_complete", true, true, false);
					readyWaitTicks = 0;
					openWorldAfterReload(minecraft);
				}
				default -> {
				}
			}
		} catch (Exception exception) {
			fail(minecraft, exception.getMessage());
		}
	}

	private static void openWorldAfterReload(Minecraft minecraft) throws IOException {
		if (!minecraft.getLevelSource().levelExists(WORLD)) {
			throw new IOException("Resource-pack reload validation world does not exist: " + WORLD);
		}
		setPhase(Phase.OPENING_WORLD_AFTER_RELOAD);
		minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
		setPhase(Phase.WAITING_FOR_WORLD_AFTER_RELOAD);
	}

	private static void queueReload(Phase requestedPhase) {
		queuedReloadPhase = requestedPhase;
	}

	private static void runInitialPhase(Minecraft minecraft) {
		try {
			setPhase(Phase.INITIAL_STATE_CAPTURED);
			recordFixture(minecraft.getResourcePackDirectory());
			selectPacks(minecraft, List.of(DIR_PACK_ID, ZIP_PACK_ID));
			requestReload(minecraft, Phase.RELOAD_1_REQUESTED);
		} catch (Exception exception) {
			fail(minecraft, exception.getMessage());
		}
	}

	private static void recordFixture(Path resourcePackDirectory) throws IOException {
		try (Stream<Path> paths = Files.walk(resourcePackDirectory)) {
			List<Path> files = paths.filter(Files::isRegularFile).toList();
			long bytes = 0L;
			for (Path file : files) {
				bytes += Files.size(file);
			}
			ResourcePackDiagnostics.validationFixture(
				"mattmc-validation-dir,mattmc-validation-zip.zip,mattmc-validation-extra",
				3L,
				files.size(),
				bytes
			);
		}
	}

	private static void requestReload(Minecraft minecraft, Phase requestedPhase) {
		setPhase(requestedPhase);
		reloadStartNanos = System.nanoTime();
		currentReloadId++;
		completedReloadId.set(0);
		completedReloadFailure.set(null);
		ResourcePackDiagnostics.validationLifecycle("controller_request_reload", stateSummary(minecraft));
		ResourcePackDiagnostics.validationReloadRequested();
		try {
			CompletableFuture<Void> future = minecraft.reloadResourcePacks();
			currentReloadFuture = future;
			ResourcePackDiagnostics.validationLifecycle("controller_reload_future_retained", futureState(future));
			future.whenComplete((ignored, throwable) -> {
				if (throwable != null) {
					ResourcePackDiagnostics.validationReloadFutureState(future.isCancelled() ? "cancelled" : "failed", throwable);
					completedReloadFailure.compareAndSet(null, "Resource reload failed: " + throwable.getMessage());
					completedReloadId.set(currentReloadId);
				} else {
					ResourcePackDiagnostics.validationReloadFutureState(future.isCancelled() ? "cancelled" : "completed", null);
				}
			});
			if (future.isDone()) {
				ResourcePackDiagnostics.validationLifecycle("controller_reload_future_synchronous", futureState(future));
			}
		} catch (Throwable throwable) {
			ResourcePackDiagnostics.validationReloadFutureState("request_threw", throwable);
			fail(minecraft, "Resource reload request threw: " + throwable.getMessage());
		}
	}

	private static void validateResources(
		Minecraft minecraft,
		String label,
		boolean expectModified,
		boolean expectBothPrimaryPacks,
		boolean expectExtraPack
	) throws IOException {
		ResourceManager manager = minecraft.getResourceManager();
		String packStack = manager.listPacks().map(pack -> pack.packId()).reduce((left, right) -> left + "," + right).orElse("");
		ResourcePackDiagnostics.validationPackStack(packStack);

		if (expectBothPrimaryPacks && (!packStack.contains(DIR_PACK_ID) || !packStack.contains(ZIP_PACK_ID))) {
			throw new IOException(label + " missing expected primary validation packs: " + packStack);
		}
		if (expectExtraPack && !packStack.contains(EXTRA_PACK_ID)) {
			throw new IOException(label + " did not observe expected add/remove pack stack: " + packStack);
		}
		if (!expectExtraPack && label.startsWith("reload_5") && packStack.contains(EXTRA_PACK_ID)) {
			throw new IOException(label + " still sees extra validation pack after restore: " + packStack);
		}

		readRequired(manager, "mattmc_validation", "validation/common_000.txt");
		readRequired(manager, "mattmc_validation", "validation/override.txt");
		readRequired(manager, "mattmc_validation", "validation/large.bin");
		readRequired(manager, "mattmc_validation", "validation/binary_000.bin");
		validateOverlayRuntimeCoverage(manager, label, expectModified, expectBothPrimaryPacks);

		if (expectModified) {
			readRequired(manager, "mattmc_validation", "validation/dir_changed.txt");
			readRequired(manager, "mattmc_validation", "validation/zip_changed.txt");
			if (readOptional(manager, "mattmc_validation", "validation/dir_removed.txt")) {
				throw new IOException(label + " still sees removed directory resource");
			}
		}
		if (expectExtraPack) {
			readRequired(manager, "mattmc_extra", "validation/extra_only.txt");
		}

		Map<ResourceLocation, Resource> listed = manager.listResources(
			"validation",
			location -> location.getNamespace().equals("mattmc_validation") || location.getNamespace().equals("mattmc_extra")
		);
		ResourcePackDiagnostics.validationTargetedListing(listed.size());
		if (listed.size() < 100) {
			throw new IOException(label + " listed too few fixture resources: " + listed.size());
		}

		List<Resource> stack = manager.getResourceStack(ResourceLocation.fromNamespaceAndPath("mattmc_validation", "validation/override.txt"));
		ResourcePackDiagnostics.validationTargetedListing(stack.size());
		if (expectBothPrimaryPacks && stack.size() < 2) {
			throw new IOException(label + " did not observe both override resources in stack");
		}
		for (Resource resource : stack) {
			resource.metadata();
		}
	}

	private static void validateOverlayRuntimeCoverage(ResourceManager manager, String label, boolean expectModified, boolean expectBothPrimaryPacks) throws IOException {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath("mattmc_validation", "validation/overlay_marker.txt");
		List<Resource> stack = manager.getResourceStack(location);
		ResourcePackDiagnostics.validationTargetedListing(stack.size());
		if (stack.isEmpty()) {
			ResourcePackDiagnostics.validationCoverage("overlay", "not-materialized-or-not-selected");
			throw new IOException(label + " did not materialize selected validation overlay resource");
		}

		boolean sawDirectoryOverlay = false;
		boolean sawZipOverlay = false;
		boolean sawModifiedZipOverlay = false;
		for (Resource resource : stack) {
			String content = new String(read(resource), StandardCharsets.UTF_8);
			sawDirectoryOverlay |= content.contains("dir overlay");
			sawZipOverlay |= content.contains("zip overlay");
			sawModifiedZipOverlay |= content.contains("zip overlay true");
		}

		if (!sawDirectoryOverlay) {
			ResourcePackDiagnostics.validationCoverage("overlay", "directory-overlay-missing");
			throw new IOException(label + " did not read the directory validation overlay");
		}
		if (expectBothPrimaryPacks && !sawZipOverlay) {
			ResourcePackDiagnostics.validationCoverage("overlay", "zip-overlay-missing");
			throw new IOException(label + " did not read the ZIP validation overlay");
		}
		if (expectModified && !sawModifiedZipOverlay) {
			ResourcePackDiagnostics.validationCoverage("overlay", "modified-zip-overlay-missing");
			throw new IOException(label + " did not read the replaced ZIP overlay");
		}

		ResourcePackDiagnostics.validationCoverage("overlay", "runtime-proven");
	}

	private static void selectPacks(Minecraft minecraft, List<String> selected) {
		minecraft.getResourcePackRepository().reload();
		minecraft.getResourcePackRepository().setSelected(selected);
		minecraft.options.resourcePacks = new ArrayList<>(selected);
		minecraft.options.incompatibleResourcePacks = new ArrayList<>(selected);
		minecraft.options.save();
	}

	private static void modifyPacks(Path resourcePackDirectory) throws IOException {
		Path dirPack = resourcePackDirectory.resolve("mattmc-validation-dir");
		write(dirPack.resolve("assets/mattmc_validation/validation/dir_changed.txt"), "dir changed after reload\n".getBytes(StandardCharsets.UTF_8));
		write(dirPack.resolve("assets/mattmc_validation/validation/common_000.txt"), "common changed in directory pack\n".getBytes(StandardCharsets.UTF_8));
		Files.deleteIfExists(dirPack.resolve("assets/mattmc_validation/validation/dir_removed.txt"));
		write(dirPack.resolve("assets/mattmc_validation/validation/override.txt.mcmeta"), "{\"phase\":\"modified\"}".getBytes(StandardCharsets.UTF_8));

		Path zipPath = resourcePackDirectory.resolve("mattmc-validation-zip.zip");
		Path replacement = resourcePackDirectory.resolve("mattmc-validation-zip.zip.tmp");
		writeValidationZip(replacement, true);
		Files.move(replacement, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static boolean readOptional(ResourceManager manager, String namespace, String path) throws IOException {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
		Resource resource = manager.getResource(location).orElse(null);
		if (resource == null) {
			return false;
		}
		read(resource);
		return true;
	}

	private static void readRequired(ResourceManager manager, String namespace, String path) throws IOException {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
		Resource resource = manager.getResource(location).orElseThrow(() -> new IOException("Missing fixture resource " + location));
		read(resource);
	}

	private static byte[] read(Resource resource) throws IOException {
		try (InputStream input = resource.open()) {
			byte[] bytes = input.readAllBytes();
			ResourcePackDiagnostics.validationTargetedRead(bytes.length);
			return bytes;
		}
	}

	private static void write(Path path, byte[] bytes) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}

	private static void writeValidationZip(Path zipPath, boolean modified) throws IOException {
		Files.createDirectories(zipPath.getParent());
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
			putZip(zip, "pack.mcmeta", packMeta("ZIP validation pack", true));
			for (int i = 0; i < 220; i++) {
				putZip(
					zip,
					"assets/mattmc_validation/validation/zip_" + String.format("%03d", i) + ".txt",
					("zip " + i + " modified=" + modified + "\n").getBytes(StandardCharsets.UTF_8)
				);
			}
			putZip(zip, "assets/mattmc_validation/validation/zip_only.txt", "zip only\n".getBytes(StandardCharsets.UTF_8));
			putZip(zip, "assets/mattmc_validation/validation/zip_changed.txt", ("zip changed " + modified + "\n").getBytes(StandardCharsets.UTF_8));
			putZip(zip, "assets/mattmc_validation/validation/override.txt", ("zip override " + modified + "\n").getBytes(StandardCharsets.UTF_8));
			putZip(zip, "assets/mattmc_validation/validation/override.txt.mcmeta", "{\"pack\":\"zip\"}".getBytes(StandardCharsets.UTF_8));
			putZip(zip, "overlay64/assets/mattmc_validation/validation/overlay_marker.txt", ("zip overlay " + modified + "\n").getBytes(StandardCharsets.UTF_8));
		}
	}

	private static byte[] packMeta(String description, boolean overlay) {
		String format = "\"min_format\":["
			+ SharedConstants.RESOURCE_PACK_FORMAT_MAJOR
			+ ","
			+ SharedConstants.RESOURCE_PACK_FORMAT_MINOR
			+ "],\"max_format\":["
			+ SharedConstants.RESOURCE_PACK_FORMAT_MAJOR
			+ ","
			+ SharedConstants.RESOURCE_PACK_FORMAT_MINOR
			+ "]";
		String overlays = overlay ? ",\"overlays\":{\"entries\":[{" + format + ",\"directory\":\"overlay64\"}]}" : "";
		return ("{\"pack\":{\"pack_format\":" + SharedConstants.RESOURCE_PACK_FORMAT_MAJOR + "," + format + ",\"description\":\"" + description + "\"}" + overlays + "}")
			.getBytes(StandardCharsets.UTF_8);
	}

	private static void putZip(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(bytes);
		zip.closeEntry();
	}

	private static void complete(Minecraft minecraft) {
		terminal = true;
		ResourcePackDiagnostics.validationLifecycle("controller_complete", stateSummary(minecraft));
		setPhase(Phase.COMPLETE);
		ResourcePackDiagnostics.validationComplete();
		minecraft.stop();
	}

	private static void fail(Minecraft minecraft, String message) {
		if (terminal) {
			return;
		}
		terminal = true;
		LOGGER.error("Resource-pack reload validation failed: {}", message);
		ResourcePackDiagnostics.validationLifecycle("controller_fail", message);
		setPhase(Phase.FAILED);
		ResourcePackDiagnostics.validationReloadFailed(message);
		minecraft.stop();
	}

	private static void setPhase(Phase nextPhase) {
		phase = nextPhase;
		ResourcePackDiagnostics.validationPhase(nextPhase.key);
	}

	private static boolean isWorldReady(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		return minecraft.level != null
			&& minecraft.player != null
			&& minecraft.getConnection() != null
			&& server != null
			&& server.isReady();
	}

	private static String stateSummary(Minecraft minecraft) {
		return "phase=" + phase.key + " screen=" + screenName(minecraft) + " overlay=" + overlayName(minecraft) + " thread=" + Thread.currentThread().getName();
	}

	private static String screenName(Minecraft minecraft) {
		return minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName();
	}

	private static String overlayName(Minecraft minecraft) {
		return minecraft.getOverlay() == null ? "null" : minecraft.getOverlay().getClass().getSimpleName();
	}

	private static String futureState(CompletableFuture<Void> future) {
		if (future == null) {
			return "none";
		}
		if (future.isCancelled()) {
			return "cancelled";
		}
		if (future.isCompletedExceptionally()) {
			return "failed";
		}
		return future.isDone() ? "completed" : "pending";
	}

	private static boolean isReloadRequestedPhase(Phase phase) {
		return phase == Phase.RELOAD_1_REQUESTED
			|| phase == Phase.RELOAD_2_REQUESTED
			|| phase == Phase.RELOAD_3_REQUESTED
			|| phase == Phase.RELOAD_4_REQUESTED
			|| phase == Phase.RELOAD_5_REQUESTED;
	}

	private static void freezeInput(LocalPlayer player) {
		if (player == null) {
			return;
		}

		player.input.keyPresses = Input.EMPTY;
		player.xxa = 0.0F;
		player.zza = 0.0F;
		player.setSprinting(false);
		player.setShiftKeyDown(false);
		player.setDeltaMovement(Vec3.ZERO);
	}

	private enum Phase {
		DISABLED("disabled"),
		WAITING_FOR_INITIAL_LOAD("waiting_for_initial_load"),
		INITIAL_STATE_CAPTURED("initial_state_captured"),
		RELOAD_1_REQUESTED("reload_1_requested"),
		RELOAD_1_COMPLETE("reload_1_complete"),
		ZIP_PACK_DESELECTED("zip_pack_deselected"),
		PACK_SET_MODIFIED("pack_set_modified"),
		RELOAD_2_REQUESTED("reload_2_requested"),
		RELOAD_2_COMPLETE("reload_2_complete"),
		RELOAD_3_REQUESTED("reload_3_requested"),
		RELOAD_3_COMPLETE("reload_3_complete"),
		RELOAD_4_REQUESTED("reload_4_requested"),
		RELOAD_4_COMPLETE("reload_4_complete"),
		PACK_SET_RESTORED("pack_set_restored"),
		RELOAD_5_REQUESTED("reload_5_requested"),
		RELOAD_5_COMPLETE("reload_5_complete"),
		OPENING_WORLD_AFTER_RELOAD("opening_world_after_reload"),
		WAITING_FOR_WORLD_AFTER_RELOAD("waiting_for_world_after_reload"),
		COMPLETE("complete"),
		FAILED("failed");

		final String key;

		Phase(String key) {
			this.key = key;
		}
	}
}
