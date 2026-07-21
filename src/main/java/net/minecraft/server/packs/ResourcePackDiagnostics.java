package net.minecraft.server.packs;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ResourcePackDiagnostics {
	private static final String STATUS_PATH_PROPERTY = "mattmc.dev.resourcePack.status";
	private static final String RELOAD_VALIDATION_PROPERTY = "mattmc.dev.resourcePackReloadValidation";
	private static final int TRACE_LIMIT = Math.max(16, Integer.getInteger("mattmc.dev.resourcePackReloadValidation.traceLimit", 96));

	private static final AtomicLong handlesOpened = new AtomicLong();
	private static final AtomicLong handlesClosed = new AtomicLong();
	private static final AtomicLong activeHandles = new AtomicLong();
	private static final AtomicLong nativeFailures = new AtomicLong();
	private static final AtomicLong invalidPathRejections = new AtomicLong();
	private static final AtomicLong staleHandleFailures = new AtomicLong();
	private static final AtomicLong reloadsRequested = new AtomicLong();
	private static final AtomicLong reloadsCompleted = new AtomicLong();
	private static final AtomicLong reloadsFailed = new AtomicLong();
	private static final AtomicLong reloadNanos = new AtomicLong();
	private static final AtomicLong targetedReads = new AtomicLong();
	private static final AtomicLong targetedListings = new AtomicLong();
	private static final AtomicLong fixtureEntries = new AtomicLong();
	private static final AtomicReference<String> firstProblem = new AtomicReference<>();
	private static final AtomicReference<String> validationPhase = new AtomicReference<>("disabled");
	private static final AtomicReference<String> terminalStatus = new AtomicReference<>("running");
	private static final AtomicReference<String> fixtureNames = new AtomicReference<>("");
	private static final AtomicReference<String> runtimePackStack = new AtomicReference<>("");
	private static final AtomicReference<String> overlayRuntimeCoverage = new AtomicReference<>("unobserved");
	private static final AtomicReference<String> directoryModificationCoverage = new AtomicReference<>("false");
	private static final AtomicReference<String> zipReplacementCoverage = new AtomicReference<>("false");
	private static final AtomicReference<String> packSelectionCoverage = new AtomicReference<>("false");
	private static final AtomicReference<String> reloadFutureState = new AtomicReference<>("none");
	private static final AtomicReference<String> currentScreen = new AtomicReference<>("");
	private static final AtomicReference<String> currentOverlay = new AtomicReference<>("");
	private static final AtomicReference<String> requestThread = new AtomicReference<>("");
	private static final AtomicReference<String> callbackThread = new AtomicReference<>("");
	private static final AtomicReference<String> exceptionClass = new AtomicReference<>("");
	private static final AtomicReference<String> exceptionMessage = new AtomicReference<>("");
	private static final AtomicReference<String> firstCause = new AtomicReference<>("");
	private static final AtomicLong lastProgressNanos = new AtomicLong();
	private static final ArrayDeque<String> lifecycleTrace = new ArrayDeque<>();

	static {
		if (enabled()) {
			Runtime.getRuntime().addShutdownHook(new Thread(ResourcePackDiagnostics::writeStatus, "MattMC resource pack diagnostics"));
		}
	}

	private ResourcePackDiagnostics() {
	}

	public static boolean enabled() {
		return Boolean.getBoolean(RELOAD_VALIDATION_PROPERTY);
	}

	static void opened() {
		if (enabled()) {
			handlesOpened.incrementAndGet();
			activeHandles.incrementAndGet();
			writeStatus();
		}
	}

	static void closed() {
		if (enabled()) {
			handlesClosed.incrementAndGet();
			activeHandles.decrementAndGet();
			writeStatus();
		}
	}

	static void nativeFailure(String operation, String packId, Throwable throwable) {
		if (enabled()) {
			nativeFailures.incrementAndGet();
			remember(operation + " failed for " + packId + ": " + throwable.getMessage());
			writeStatus();
		}
	}

	static void invalidPath(String packId, String path) {
		if (enabled()) {
			invalidPathRejections.incrementAndGet();
			remember("Invalid native pack path in " + packId + ": " + path);
			writeStatus();
		}
	}

	static void staleHandle(String packId) {
		if (enabled()) {
			staleHandleFailures.incrementAndGet();
			remember("Stale native pack supplier after close: " + packId);
			writeStatus();
		}
	}

	public static void validationPhase(String phase) {
		if (enabled()) {
			validationPhase.set(phase);
			trace("phase", phase);
			writeStatus();
		}
	}

	public static void validationLifecycle(String stage, String detail) {
		if (enabled()) {
			trace(stage, detail);
			writeStatus();
		}
	}

	public static void validationClientState(String screen, String overlay) {
		if (enabled()) {
			currentScreen.set(screen);
			currentOverlay.set(overlay);
		}
	}

	public static void validationFixture(String names, long packs, long entries, long bytes) {
		if (enabled()) {
			fixtureNames.set(names);
			fixtureEntries.set(entries);
			writeStatus();
		}
	}

	public static void validationPackStack(String packStack) {
		if (enabled()) {
			runtimePackStack.set(packStack);
		}
	}

	public static void validationReloadRequested() {
		if (enabled()) {
			reloadsRequested.incrementAndGet();
			reloadFutureState.set("created");
			requestThread.set(Thread.currentThread().getName());
			trace("reload_request", validationPhase.get());
			writeStatus();
		}
	}

	public static void validationReloadCompleted(long nanos) {
		if (enabled()) {
			reloadsCompleted.incrementAndGet();
			reloadNanos.addAndGet(Math.max(0L, nanos));
			reloadFutureState.set("completed");
			callbackThread.set(Thread.currentThread().getName());
			trace("reload_complete", validationPhase.get());
			writeStatus();
		}
	}

	public static void validationReloadFailed(String message) {
		if (enabled()) {
			reloadsFailed.incrementAndGet();
			remember(message);
			terminalStatus.set("failed");
			reloadFutureState.compareAndSet("created", "failed");
			trace("reload_failed", message);
			writeStatus();
		}
	}

	public static void validationReloadFutureState(String state, Throwable throwable) {
		if (!enabled()) {
			return;
		}
		reloadFutureState.set(state);
		callbackThread.set(Thread.currentThread().getName());
		if (throwable != null) {
			exceptionClass.set(throwable.getClass().getName());
			exceptionMessage.set(String.valueOf(throwable.getMessage()));
			Throwable cause = throwable.getCause();
			firstCause.set(cause == null ? "" : cause.getClass().getName() + ": " + cause.getMessage());
			remember("Resource reload future " + state + ": " + throwable.getMessage());
		}
		trace("reload_future_" + state, throwable == null ? validationPhase.get() : throwable.toString());
		writeStatus();
	}

	public static void validationTargetedRead(long bytes) {
		if (enabled()) {
			targetedReads.incrementAndGet();
		}
	}

	public static void validationTargetedListing(int entries) {
		if (enabled()) {
			targetedListings.incrementAndGet();
		}
	}

	public static void validationCoverage(String key, String value) {
		if (!enabled()) {
			return;
		}
		switch (key) {
			case "overlay" -> overlayRuntimeCoverage.set(value);
			case "directoryModification" -> directoryModificationCoverage.set(value);
			case "zipReplacement" -> zipReplacementCoverage.set(value);
			case "packSelection" -> packSelectionCoverage.set(value);
			default -> {
			}
		}
		writeStatus();
	}

	public static void validationComplete() {
		if (enabled()) {
			terminalStatus.set(firstProblem.get() == null ? "complete" : "failed");
			validationPhase.set(firstProblem.get() == null ? "complete" : validationPhase.get());
			writeStatus();
		}
	}

	static void writeStatus() {
		if (!enabled()) {
			return;
		}
		Path path = statusPath();
		if (path == null) {
			return;
		}
		JsonObject root = new JsonObject();
		boolean reloadValidation = Boolean.getBoolean(RELOAD_VALIDATION_PROPERTY);
		String status = reloadValidation ? terminalStatus.get() : activeHandles.get() == 0L ? "complete" : "running";
		root.addProperty("status", firstProblem.get() != null ? "failed" : status);
		root.addProperty("mode", "rust");
		root.addProperty("validationPhase", validationPhase.get());
		root.addProperty("reloadsRequested", reloadsRequested.get());
		root.addProperty("reloadsCompleted", reloadsCompleted.get());
		root.addProperty("reloadsFailed", reloadsFailed.get());
		root.addProperty("reloadNanos", reloadNanos.get());
		root.addProperty("fixtureNames", fixtureNames.get());
		root.addProperty("fixtureEntries", fixtureEntries.get());
		root.addProperty("runtimePackStack", runtimePackStack.get());
		root.addProperty("handlesOpened", handlesOpened.get());
		root.addProperty("handlesClosed", handlesClosed.get());
		root.addProperty("activeHandles", activeHandles.get());
		root.addProperty("nativeFailures", nativeFailures.get());
		root.addProperty("invalidPathRejections", invalidPathRejections.get());
		root.addProperty("javaFallbacks", 0);
		root.addProperty("staleHandleFailures", staleHandleFailures.get());
		root.addProperty("targetedReads", targetedReads.get());
		root.addProperty("targetedListings", targetedListings.get());
		root.addProperty("overlayRuntimeCoverage", overlayRuntimeCoverage.get());
		root.addProperty("directoryModificationCoverage", directoryModificationCoverage.get());
		root.addProperty("zipReplacementCoverage", zipReplacementCoverage.get());
		root.addProperty("packSelectionCoverage", packSelectionCoverage.get());
		root.addProperty("zeroLeakShutdown", "complete".equals(status) && activeHandles.get() == 0L);
		root.addProperty("reloadFutureState", reloadFutureState.get());
		root.addProperty("currentScreen", currentScreen.get());
		root.addProperty("currentOverlay", currentOverlay.get());
		root.addProperty("requestThread", requestThread.get());
		root.addProperty("callbackThread", callbackThread.get());
		root.addProperty("exceptionClass", exceptionClass.get());
		root.addProperty("exceptionMessage", exceptionMessage.get());
		root.addProperty("firstCause", firstCause.get());
		root.addProperty("lastProgressNanos", lastProgressNanos.get());
		root.add("lifecycleTrace", traceSnapshot());
		root.addProperty("firstProblem", firstProblem.get());
		try {
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static void remember(String message) {
		firstProblem.compareAndSet(null, message);
	}

	private static void trace(String stage, String detail) {
		lastProgressNanos.set(System.nanoTime());
		String event = System.currentTimeMillis() + "|" + Thread.currentThread().getName() + "|" + stage + "|" + detail;
		synchronized (lifecycleTrace) {
			if (lifecycleTrace.size() >= TRACE_LIMIT) {
				lifecycleTrace.removeFirst();
			}
			lifecycleTrace.addLast(event);
		}
	}

	private static com.google.gson.JsonArray traceSnapshot() {
		List<String> snapshot;
		synchronized (lifecycleTrace) {
			snapshot = new ArrayList<>(lifecycleTrace);
		}
		com.google.gson.JsonArray array = new com.google.gson.JsonArray();
		for (String event : snapshot) {
			array.add(event);
		}
		return array;
	}

	private static Path statusPath() {
		String value = System.getProperty(STATUS_PATH_PROPERTY, "");
		return value.isBlank() ? Path.of("run", "resource_pack_validation.json") : Path.of(value);
	}
}
