package net.vulkanic;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class VulkanPerfAudit {
    private static final boolean ENABLED = Boolean.getBoolean("mattmc.vulkan.perfAudit")
        || Boolean.getBoolean("mattmc.perfAudit");
    private static final long SNAPSHOT_INTERVAL_NANOS = 5_000_000_000L;
    private static final int MAX_FRAME_SAMPLES = Math.max(1, Integer.getInteger("mattmc.perfAudit.maxFrameSamples", 4096));

    private static final LongAdder beginFrameCallCount = new LongAdder();
    private static final LongAdder beginFrameAcquireSuccessCount = new LongAdder();
    private static final LongAdder beginFrameFenceWaitNanos = new LongAdder();
    private static final LongAdder renderPassBeginCount = new LongAdder();
    private static final LongAdder primarySubmitCount = new LongAdder();
    private static final LongAdder primarySubmitTotalNanos = new LongAdder();
    private static final LongAdder primarySubmitWaitBeforeNanos = new LongAdder();
    private static final LongAdder primarySubmitQueueSubmitNanos = new LongAdder();
    private static final LongAdder primarySubmitWaitAfterNanos = new LongAdder();
    private static final LongAdder primarySubmitOtherNanos = new LongAdder();
    private static final LongAdder vulkanSubmitCount = new LongAdder();
    private static final LongAdder vulkanSubmittedCommandBufferCount = new LongAdder();
    private static final LongAdder vulkanSubmitQueueSubmitNanos = new LongAdder();
    private static final LongAdder vulkanSubmitWaitBeforeNanos = new LongAdder();
    private static final LongAdder vulkanSubmitWaitAfterNanos = new LongAdder();
    private static final LongAdder vulkanSubmitOtherNanos = new LongAdder();
    private static final LongAdder vulkanSubmitImmediateCompletionCount = new LongAdder();
    private static final Map<String, SubmitCounters> submitCategories = new ConcurrentHashMap<>();
    private static final Map<String, SubmitCounters> submitDetails = new ConcurrentHashMap<>();
    private static final LongAdder descriptorBindCount = new LongAdder();
    private static final LongAdder descriptorBindTotalNanos = new LongAdder();
    private static final LongAdder bindingBuildCount = new LongAdder();
    private static final LongAdder bindingBuildTotalNanos = new LongAdder();
    private static final LongAdder bindingBuildCompleteCoverageCount = new LongAdder();
    private static final LongAdder presentedFrameCount = new LongAdder();
    private static final LongAdder deterministicWarmupFrameCount = new LongAdder();
    private static final LongAdder deterministicMeasuredFrameCount = new LongAdder();
    private static final LongAdder deterministicMeasuredFrameTotalNanos = new LongAdder();
    private static final LongAdder graphicsDrawCount = new LongAdder();
    private static final LongAdder computeDispatchCount = new LongAdder();
    private static final LongAdder clearCount = new LongAdder();
    private static final LongAdder transferCount = new LongAdder();
    private static final Map<String, PhaseCounters> phases = new ConcurrentHashMap<>();
    private static final long[] measuredFrameNanos = new long[MAX_FRAME_SAMPLES];
    private static final Object measuredFrameLock = new Object();
    private static int measuredFrameSampleCount;
    private static long measuredFrameOverflowCount;
    private static final long initialGcCount = gcCollectionCount();
    private static final long initialGcTimeMillis = gcCollectionTimeMillis();

    private static final AtomicLong lastSnapshotNanos = new AtomicLong();
    private static final Path reportFile = initReportFile();

    static {
        if (ENABLED && reportFile != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(VulkanPerfAudit::writeSnapshotQuietly, "vulkan-perf-audit-flush"));
        }
    }

    private VulkanPerfAudit() {
    }

    public static boolean isEnabled() {
        return ENABLED && reportFile != null;
    }

    public static void recordBeginFrameCall() {
        if (!isEnabled()) {
            return;
        }
        beginFrameCallCount.increment();
    }

    public static void recordBeginFrameFenceWait(long nanos) {
        if (!isEnabled()) {
            return;
        }
        beginFrameFenceWaitNanos.add(Math.max(0L, nanos));
    }

    public static void recordBeginFrameAcquireSuccess() {
        if (!isEnabled()) {
            return;
        }
        beginFrameAcquireSuccessCount.increment();
    }

    public static void recordRenderPassBegin() {
        if (!isEnabled()) {
            return;
        }
        renderPassBeginCount.increment();
    }

    public static void recordPrimarySubmit(long totalNanos, long waitBeforeNanos, long queueSubmitNanos, long waitAfterNanos, long otherNanos) {
        if (!isEnabled()) {
            return;
        }
        primarySubmitCount.increment();
        primarySubmitTotalNanos.add(Math.max(0L, totalNanos));
        primarySubmitWaitBeforeNanos.add(Math.max(0L, waitBeforeNanos));
        primarySubmitQueueSubmitNanos.add(Math.max(0L, queueSubmitNanos));
        primarySubmitWaitAfterNanos.add(Math.max(0L, waitAfterNanos));
        primarySubmitOtherNanos.add(Math.max(0L, otherNanos));
    }

    public static void recordVulkanSubmit(
        String category,
        String callsite,
        int commandBufferCount,
        long commandCount,
        long byteCount,
        boolean fenceWaitFollows,
        boolean immediateCompletionRequired,
        long totalNanos,
        long waitBeforeNanos,
        long queueSubmitNanos,
        long waitAfterNanos,
        long otherNanos,
        long retiredGenerationCount,
        String cannotJoinReason
    ) {
        if (!isEnabled()) {
            return;
        }
        String normalizedCategory = normalizeKey(category, "other");
        String normalizedCallsite = normalizeKey(callsite, "unknown");
        String normalizedReason = normalizeKey(cannotJoinReason, "none");
        SubmitCounters categoryCounters = submitCategories.computeIfAbsent(normalizedCategory, ignored -> new SubmitCounters());
        SubmitCounters detailCounters = submitDetails.computeIfAbsent(
            normalizedCategory + "|" + normalizedCallsite + "|" + normalizedReason,
            ignored -> new SubmitCounters()
        );
        long clampedTotal = Math.max(0L, totalNanos);
        long clampedWaitBefore = Math.max(0L, waitBeforeNanos);
        long clampedQueueSubmit = Math.max(0L, queueSubmitNanos);
        long clampedWaitAfter = Math.max(0L, waitAfterNanos);
        long clampedOther = Math.max(0L, otherNanos);
        int clampedCommandBuffers = Math.max(0, commandBufferCount);
        long clampedCommands = Math.max(0L, commandCount);
        long clampedBytes = Math.max(0L, byteCount);
        long clampedRetiredGenerations = Math.max(0L, retiredGenerationCount);
        vulkanSubmitCount.increment();
        vulkanSubmittedCommandBufferCount.add(clampedCommandBuffers);
        vulkanSubmitQueueSubmitNanos.add(clampedQueueSubmit);
        vulkanSubmitWaitBeforeNanos.add(clampedWaitBefore);
        vulkanSubmitWaitAfterNanos.add(clampedWaitAfter);
        vulkanSubmitOtherNanos.add(clampedOther);
        if (immediateCompletionRequired || fenceWaitFollows) {
            vulkanSubmitImmediateCompletionCount.increment();
        }
        categoryCounters.add(
            clampedTotal,
            clampedWaitBefore,
            clampedQueueSubmit,
            clampedWaitAfter,
            clampedOther,
            clampedCommandBuffers,
            clampedCommands,
            clampedBytes,
            clampedRetiredGenerations,
            fenceWaitFollows,
            immediateCompletionRequired
        );
        detailCounters.add(
            clampedTotal,
            clampedWaitBefore,
            clampedQueueSubmit,
            clampedWaitAfter,
            clampedOther,
            clampedCommandBuffers,
            clampedCommands,
            clampedBytes,
            clampedRetiredGenerations,
            fenceWaitFollows,
            immediateCompletionRequired
        );
    }

    public static void recordDescriptorBind(long nanos) {
        if (!isEnabled()) {
            return;
        }
        descriptorBindCount.increment();
        descriptorBindTotalNanos.add(Math.max(0L, nanos));
    }

    public static void recordBindingBuild(long nanos, boolean completeCoverage) {
        if (!isEnabled()) {
            return;
        }
        bindingBuildCount.increment();
        bindingBuildTotalNanos.add(Math.max(0L, nanos));
        if (completeCoverage) {
            bindingBuildCompleteCoverageCount.increment();
        }
    }

    public static void recordPresentedFrame() {
        if (!isEnabled()) {
            return;
        }
        presentedFrameCount.increment();
        maybeWriteSnapshot();
    }

    public static void recordDeterministicFrame(long nanos, boolean measurementFrame) {
        if (!isEnabled()) {
            return;
        }
        long clampedNanos = Math.max(0L, nanos);
        if (measurementFrame) {
            deterministicMeasuredFrameCount.increment();
            deterministicMeasuredFrameTotalNanos.add(clampedNanos);
            addMeasuredFrameSample(clampedNanos);
        } else {
            deterministicWarmupFrameCount.increment();
        }
        maybeWriteSnapshot();
    }

    public static void recordPhase(String name, long nanos) {
        if (!isEnabled() || name == null || name.isBlank()) {
            return;
        }
        phases.computeIfAbsent(name, ignored -> new PhaseCounters()).add(Math.max(0L, nanos));
    }

    public static void recordGraphicsDraw() {
        if (isEnabled()) {
            graphicsDrawCount.increment();
        }
    }

    public static void recordComputeDispatch() {
        if (isEnabled()) {
            computeDispatchCount.increment();
        }
    }

    public static void recordClear() {
        if (isEnabled()) {
            clearCount.increment();
        }
    }

    public static void recordTransfer() {
        if (isEnabled()) {
            transferCount.increment();
        }
    }

    public static void flush() {
        writeSnapshotQuietly();
    }

    private static void maybeWriteSnapshot() {
        long now = System.nanoTime();
        long last = lastSnapshotNanos.get();
        if (last != 0L && now - last < SNAPSHOT_INTERVAL_NANOS) {
            return;
        }
        if (lastSnapshotNanos.compareAndSet(last, now)) {
            writeSnapshotQuietly();
        }
    }

    private static void writeSnapshotQuietly() {
        if (!isEnabled()) {
            return;
        }

        try {
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile, buildReport(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String buildReport() {
        double primarySubmitMs = nanosToMillis(primarySubmitTotalNanos.sum());
        double descriptorBindMs = nanosToMillis(descriptorBindTotalNanos.sum());
        double bindingBuildMs = nanosToMillis(bindingBuildTotalNanos.sum());
        double frameStartWaitMs = nanosToMillis(beginFrameFenceWaitNanos.sum());
        double trackedCpuMs = primarySubmitMs + descriptorBindMs + bindingBuildMs + frameStartWaitMs;
        double presentedFrames = Math.max(1.0, presentedFrameCount.sum());
        String diagnosticIssue = dominantIssue(primarySubmitMs, descriptorBindMs, bindingBuildMs, frameStartWaitMs);

        StringBuilder builder = new StringBuilder(1024);
        builder.append("timestamp_utc=").append(Instant.now()).append('\n');
        builder.append("backend=").append(System.getProperty("mattmc.perfAudit.backend", "vulkan")).append('\n');
        builder.append("diagnostic_issue=").append(diagnosticIssue).append('\n');
        builder.append("diagnostic_hint=").append(diagnosticHint(diagnosticIssue)).append('\n');
        builder.append("begin_frame_call_count=").append(beginFrameCallCount.sum()).append('\n');
        builder.append("begin_frame_acquire_success_count=").append(beginFrameAcquireSuccessCount.sum()).append('\n');
        builder.append("begin_frame_fence_wait_ms=").append(format(frameStartWaitMs)).append('\n');
        builder.append("begin_frame_acquire_ms=0.0000\n");
        builder.append("render_pass_begin_count=").append(renderPassBeginCount.sum()).append('\n');
        builder.append("primary_submit_count=").append(primarySubmitCount.sum()).append('\n');
        builder.append("primary_submit_total_ms=").append(format(primarySubmitMs)).append('\n');
        builder.append("primary_submit_wait_before_ms=").append(format(nanosToMillis(primarySubmitWaitBeforeNanos.sum()))).append('\n');
        builder.append("primary_submit_queue_submit_ms=").append(format(nanosToMillis(primarySubmitQueueSubmitNanos.sum()))).append('\n');
        builder.append("primary_submit_wait_after_ms=").append(format(nanosToMillis(primarySubmitWaitAfterNanos.sum()))).append('\n');
        builder.append("primary_submit_other_ms=").append(format(nanosToMillis(primarySubmitOtherNanos.sum()))).append('\n');
        builder.append("submit_accounting=").append("submit timings are exclusive by submit operation; phase timings below are inclusive/nested\n");
        builder.append("vulkan_submit_count=").append(vulkanSubmitCount.sum()).append('\n');
        builder.append("vulkan_submitted_command_buffer_count=").append(vulkanSubmittedCommandBufferCount.sum()).append('\n');
        builder.append("vulkan_submit_queue_submit_ms=").append(format(nanosToMillis(vulkanSubmitQueueSubmitNanos.sum()))).append('\n');
        builder.append("vulkan_submit_wait_before_ms=").append(format(nanosToMillis(vulkanSubmitWaitBeforeNanos.sum()))).append('\n');
        builder.append("vulkan_submit_wait_after_ms=").append(format(nanosToMillis(vulkanSubmitWaitAfterNanos.sum()))).append('\n');
        builder.append("vulkan_submit_other_ms=").append(format(nanosToMillis(vulkanSubmitOtherNanos.sum()))).append('\n');
        builder.append("vulkan_submit_immediate_completion_count=").append(vulkanSubmitImmediateCompletionCount.sum()).append('\n');
        builder.append("frame_submit_count=").append(submitCategories.getOrDefault("normal_frame_graphics_submit", SubmitCounters.EMPTY).count.sum()).append('\n');
        builder.append("frame_submit_total_ms=")
            .append(format(nanosToMillis(submitCategories.getOrDefault("normal_frame_graphics_submit", SubmitCounters.EMPTY).totalNanos.sum())))
            .append('\n');
        builder.append("frame_submit_queue_submit_ms=")
            .append(format(nanosToMillis(submitCategories.getOrDefault("normal_frame_graphics_submit", SubmitCounters.EMPTY).queueSubmitNanos.sum())))
            .append('\n');
        builder.append("presented_frame_count=").append(presentedFrameCount.sum()).append('\n');
        builder.append("deterministic_warmup_frame_count=").append(deterministicWarmupFrameCount.sum()).append('\n');
        builder.append("deterministic_measured_frame_count=").append(deterministicMeasuredFrameCount.sum()).append('\n');
        FrameStats frameStats = frameStats();
        builder.append("deterministic_measured_frame_samples=").append(frameStats.count()).append('\n');
        builder.append("deterministic_measured_frame_overflow_count=").append(measuredFrameOverflowCount).append('\n');
        builder.append("deterministic_measured_frame_total_ms=").append(format(nanosToMillis(deterministicMeasuredFrameTotalNanos.sum()))).append('\n');
        builder.append("deterministic_measured_frame_median_ms=").append(format(nanosToMillis(frameStats.medianNanos()))).append('\n');
        builder.append("deterministic_measured_frame_p95_ms=").append(format(nanosToMillis(frameStats.p95Nanos()))).append('\n');
        builder.append("deterministic_measured_frame_p99_ms=").append(format(nanosToMillis(frameStats.p99Nanos()))).append('\n');
        builder.append("deterministic_measured_frame_worst_ms=").append(format(nanosToMillis(frameStats.worstNanos()))).append('\n');
        builder.append("graphics_draw_count=").append(graphicsDrawCount.sum()).append('\n');
        builder.append("compute_dispatch_count=").append(computeDispatchCount.sum()).append('\n');
        builder.append("clear_count=").append(clearCount.sum()).append('\n');
        builder.append("transfer_count=").append(transferCount.sum()).append('\n');
        builder.append("descriptor_bind_count=").append(descriptorBindCount.sum()).append('\n');
        builder.append("descriptor_bind_total_ms=").append(format(descriptorBindMs)).append('\n');
        builder.append("pipeline_resolve_count=0\n");
        builder.append("pipeline_resolve_total_ms=0.0000\n");
        builder.append("pipeline_resolve_miss_count=0\n");
        builder.append("binding_build_count=").append(bindingBuildCount.sum()).append('\n');
        builder.append("binding_build_total_ms=").append(format(bindingBuildMs)).append('\n');
        builder.append("binding_build_complete_coverage_count=").append(bindingBuildCompleteCoverageCount.sum()).append('\n');
        builder.append("primary_submits_per_presented_frame=").append(format(primarySubmitCount.sum() / presentedFrames)).append('\n');
        builder.append("vulkan_submits_per_presented_frame=").append(format(vulkanSubmitCount.sum() / presentedFrames)).append('\n');
        builder.append("render_passes_per_presented_frame=").append(format(renderPassBeginCount.sum() / presentedFrames)).append('\n');
        builder.append("descriptor_binds_per_presented_frame=").append(format(descriptorBindCount.sum() / presentedFrames)).append('\n');
        builder.append("binding_builds_per_presented_frame=").append(format(bindingBuildCount.sum() / presentedFrames)).append('\n');
        builder.append("primary_submit_share_of_tracked_cpu_time_pct=").append(format(percent(primarySubmitMs, trackedCpuMs))).append('\n');
        builder.append("descriptor_bind_share_of_tracked_cpu_time_pct=").append(format(percent(descriptorBindMs, trackedCpuMs))).append('\n');
        builder.append("binding_build_share_of_tracked_cpu_time_pct=").append(format(percent(bindingBuildMs, trackedCpuMs))).append('\n');
        builder.append("frame_start_wait_share_of_tracked_cpu_time_pct=").append(format(percent(frameStartWaitMs, trackedCpuMs))).append('\n');
        builder.append("gc_collection_count_delta=").append(Math.max(0L, gcCollectionCount() - initialGcCount)).append('\n');
        builder.append("gc_collection_time_ms_delta=").append(Math.max(0L, gcCollectionTimeMillis() - initialGcTimeMillis)).append('\n');
        appendSubmitSummary(builder, presentedFrames);
        appendPhaseSummary(builder);
        return builder.toString();
    }

    private static Path initReportFile() {
        if (!ENABLED) {
            return null;
        }

        String reportDir = System.getProperty("mattmc.perfAuditReportDir",
            System.getProperty("mattmc.vulkan.perfAuditReportDir", "")).trim();
        if (reportDir.isEmpty()) {
            return null;
        }

        return Path.of(reportDir).resolve("vulkan-perf-audit-" + currentPid() + ".txt");
    }

    private static String currentPid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int atIndex = runtimeName.indexOf('@');
        return atIndex > 0 ? runtimeName.substring(0, atIndex) : runtimeName;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double percent(double numerator, double denominator) {
        if (denominator <= 0.0) {
            return 0.0;
        }
        return numerator * 100.0 / denominator;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String normalizeKey(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char c = trimmed.charAt(index);
            builder.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return builder.toString();
    }

    private static String dominantIssue(double primarySubmitMs, double descriptorBindMs, double bindingBuildMs, double frameStartWaitMs) {
        String issue = "inconclusive";
        double max = primarySubmitMs;
        if (max > 0.0) {
            issue = "immediate_primary_submit_churn";
        }
        if (descriptorBindMs > max) {
            max = descriptorBindMs;
            issue = "descriptor_bind_churn";
        }
        if (bindingBuildMs > max) {
            max = bindingBuildMs;
            issue = "binding_build_churn";
        }
        if (frameStartWaitMs > max) {
            issue = "frame_start_wait";
        }
        return issue;
    }

    private static String diagnosticHint(String issue) {
        return switch (issue) {
            case "immediate_primary_submit_churn" -> "Immediate primary command buffer submits dominate tracked Vulkan CPU time.";
            case "descriptor_bind_churn" -> "Descriptor update and bind churn dominates tracked Vulkan CPU time.";
            case "binding_build_churn" -> "Pipeline resource binding construction dominates tracked Vulkan CPU time.";
            case "frame_start_wait" -> "Frame-start fence waits dominate tracked Vulkan CPU time.";
            default -> "No single tracked bucket dominated enough for an automatic call; inspect the raw timings.";
        };
    }

    private static void addMeasuredFrameSample(long nanos) {
        synchronized (measuredFrameLock) {
            if (measuredFrameSampleCount < measuredFrameNanos.length) {
                measuredFrameNanos[measuredFrameSampleCount++] = nanos;
            } else {
                measuredFrameOverflowCount++;
            }
        }
    }

    private static FrameStats frameStats() {
        long[] copy;
        synchronized (measuredFrameLock) {
            copy = java.util.Arrays.copyOf(measuredFrameNanos, measuredFrameSampleCount);
        }
        java.util.Arrays.sort(copy);
        if (copy.length == 0) {
            return new FrameStats(0, 0L, 0L, 0L, 0L);
        }
        return new FrameStats(
            copy.length,
            percentile(copy, 0.50),
            percentile(copy, 0.95),
            percentile(copy, 0.99),
            copy[copy.length - 1]
        );
    }

    private static long percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index];
    }

    private static void appendPhaseSummary(StringBuilder builder) {
        List<Map.Entry<String, PhaseCounters>> entries = new ArrayList<>(phases.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Map.Entry<String, PhaseCounters> entry : entries) {
            PhaseCounters counters = entry.getValue();
            double totalMs = nanosToMillis(counters.totalNanos.sum());
            totals.put(entry.getKey(), totalMs);
            builder.append("phase.").append(entry.getKey()).append(".count=").append(counters.count.sum()).append('\n');
            builder.append("phase.").append(entry.getKey()).append(".total_ms=").append(format(totalMs)).append('\n');
        }
        entries.sort((left, right) -> Double.compare(
            totals.getOrDefault(right.getKey(), 0.0),
            totals.getOrDefault(left.getKey(), 0.0)
        ));
        int rank = 1;
        for (Map.Entry<String, PhaseCounters> entry : entries) {
            if (rank > 10) {
                break;
            }
            double totalMs = totals.getOrDefault(entry.getKey(), 0.0);
            builder.append("phase_rank_").append(rank).append('=')
                .append(entry.getKey()).append(':').append(format(totalMs)).append("ms\n");
            rank++;
        }
    }

    private static void appendSubmitSummary(StringBuilder builder, double presentedFrames) {
        List<Map.Entry<String, SubmitCounters>> categories = new ArrayList<>(submitCategories.entrySet());
        categories.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, SubmitCounters> entry : categories) {
            SubmitCounters counters = entry.getValue();
            String key = "submit_category." + entry.getKey();
            long count = counters.count.sum();
            builder.append(key).append(".count=").append(count).append('\n');
            builder.append(key).append(".per_presented_frame=").append(format(count / presentedFrames)).append('\n');
            builder.append(key).append(".command_buffers=").append(counters.commandBufferCount.sum()).append('\n');
            builder.append(key).append(".commands=").append(counters.commandCount.sum()).append('\n');
            builder.append(key).append(".bytes=").append(counters.byteCount.sum()).append('\n');
            builder.append(key).append(".total_ms=").append(format(nanosToMillis(counters.totalNanos.sum()))).append('\n');
            builder.append(key).append(".queue_submit_ms=").append(format(nanosToMillis(counters.queueSubmitNanos.sum()))).append('\n');
            builder.append(key).append(".wait_before_ms=").append(format(nanosToMillis(counters.waitBeforeNanos.sum()))).append('\n');
            builder.append(key).append(".wait_after_ms=").append(format(nanosToMillis(counters.waitAfterNanos.sum()))).append('\n');
            builder.append(key).append(".fence_wait_follow_count=").append(counters.fenceWaitFollowCount.sum()).append('\n');
            builder.append(key).append(".immediate_completion_required_count=").append(counters.immediateCompletionRequiredCount.sum()).append('\n');
            builder.append(key).append(".retired_generation_count=").append(counters.retiredGenerationCount.sum()).append('\n');
        }

        List<Map.Entry<String, SubmitCounters>> details = new ArrayList<>(submitDetails.entrySet());
        details.sort((left, right) -> Long.compare(right.getValue().count.sum(), left.getValue().count.sum()));
        int rank = 1;
        for (Map.Entry<String, SubmitCounters> entry : details) {
            if (rank > 20) {
                break;
            }
            SubmitCounters counters = entry.getValue();
            builder.append("submit_detail_rank_").append(rank).append('=')
                .append(entry.getKey())
                .append("|count=").append(counters.count.sum())
                .append("|queue_submit_ms=").append(format(nanosToMillis(counters.queueSubmitNanos.sum())))
                .append("|wait_after_ms=").append(format(nanosToMillis(counters.waitAfterNanos.sum())))
                .append('\n');
            rank++;
        }
    }

    private static long gcCollectionCount() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count > 0L) {
                total += count;
            }
        }
        return total;
    }

    private static long gcCollectionTimeMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = bean.getCollectionTime();
            if (time > 0L) {
                total += time;
            }
        }
        return total;
    }

    private static final class PhaseCounters {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();

        private void add(long nanos) {
            count.increment();
            totalNanos.add(nanos);
        }
    }

    private static final class SubmitCounters {
        private static final SubmitCounters EMPTY = new SubmitCounters();

        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAdder waitBeforeNanos = new LongAdder();
        private final LongAdder queueSubmitNanos = new LongAdder();
        private final LongAdder waitAfterNanos = new LongAdder();
        private final LongAdder otherNanos = new LongAdder();
        private final LongAdder commandBufferCount = new LongAdder();
        private final LongAdder commandCount = new LongAdder();
        private final LongAdder byteCount = new LongAdder();
        private final LongAdder retiredGenerationCount = new LongAdder();
        private final LongAdder fenceWaitFollowCount = new LongAdder();
        private final LongAdder immediateCompletionRequiredCount = new LongAdder();

        private void add(
            long totalNanos,
            long waitBeforeNanos,
            long queueSubmitNanos,
            long waitAfterNanos,
            long otherNanos,
            int commandBufferCount,
            long commandCount,
            long byteCount,
            long retiredGenerationCount,
            boolean fenceWaitFollows,
            boolean immediateCompletionRequired
        ) {
            count.increment();
            this.totalNanos.add(totalNanos);
            this.waitBeforeNanos.add(waitBeforeNanos);
            this.queueSubmitNanos.add(queueSubmitNanos);
            this.waitAfterNanos.add(waitAfterNanos);
            this.otherNanos.add(otherNanos);
            this.commandBufferCount.add(commandBufferCount);
            this.commandCount.add(commandCount);
            this.byteCount.add(byteCount);
            this.retiredGenerationCount.add(retiredGenerationCount);
            if (fenceWaitFollows) {
                fenceWaitFollowCount.increment();
            }
            if (immediateCompletionRequired) {
                immediateCompletionRequiredCount.increment();
            }
        }
    }

    private record FrameStats(int count, long medianNanos, long p95Nanos, long p99Nanos, long worstNanos) {
    }
}
