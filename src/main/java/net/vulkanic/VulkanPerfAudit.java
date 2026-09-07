package net.vulkanic;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final LongAdder descriptorBindCount = new LongAdder();
    private static final LongAdder descriptorBindTotalNanos = new LongAdder();
    private static final LongAdder bindingBuildCount = new LongAdder();
    private static final LongAdder bindingBuildTotalNanos = new LongAdder();
    private static final LongAdder bindingBuildCompleteCoverageCount = new LongAdder();
    private static final LongAdder presentedFrameCount = new LongAdder();
    private static final LongAdder graphicsDrawCount = new LongAdder();
    private static final LongAdder deterministicWarmupFrameCount = new LongAdder();
    private static final LongAdder deterministicMeasuredFrameCount = new LongAdder();
    private static final LongAdder deterministicMeasuredFrameTotalNanos = new LongAdder();
    private static final Map<String, PhaseCounters> phases = new ConcurrentHashMap<>();

    private static final Object measuredFrameLock = new Object();
    private static final long[] measuredFrameNanos = new long[MAX_FRAME_SAMPLES];
    private static int measuredFrameSampleCount;
    private static long measuredFrameOverflowCount;

    private static final long initialGcCount = gcCollectionCount();
    private static final long initialGcTimeMillis = gcCollectionTimeMillis();
    private static final AtomicLong lastSnapshotNanos = new AtomicLong();
    private static final AtomicInteger deterministicMeasurementFrameActive = new AtomicInteger();
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

    public static void setDeterministicMeasurementFrameActive(boolean active) {
        deterministicMeasurementFrameActive.set(active ? 1 : 0);
    }

    public static boolean isDeterministicMeasurementFrameActive() {
        return isEnabled() && deterministicMeasurementFrameActive.get() != 0;
    }

    public static void recordBeginFrameCall() {
        if (isEnabled()) {
            beginFrameCallCount.increment();
        }
    }

    public static void recordBeginFrameFenceWait(long nanos) {
        if (isEnabled()) {
            beginFrameFenceWaitNanos.add(Math.max(0L, nanos));
        }
    }

    public static void recordBeginFrameAcquireSuccess() {
        if (isEnabled()) {
            beginFrameAcquireSuccessCount.increment();
        }
    }

    public static void recordRenderPassBegin() {
        if (isEnabled()) {
            renderPassBeginCount.increment();
        }
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

    public static void recordGraphicsDraw() {
        if (isEnabled()) {
            graphicsDrawCount.increment();
        }
    }

    public static void recordPhase(String phase, long nanos) {
        if (!isEnabled() || phase == null || phase.isBlank()) {
            return;
        }
        PhaseCounters counters = phases.computeIfAbsent(phase, ignored -> new PhaseCounters());
        counters.count.increment();
        counters.totalNanos.add(Math.max(0L, nanos));
    }

    public static void recordDeterministicFrame(long nanos, boolean measured) {
        if (!isEnabled()) {
            return;
        }
        if (measured) {
            deterministicMeasuredFrameCount.increment();
            deterministicMeasuredFrameTotalNanos.add(Math.max(0L, nanos));
            addMeasuredFrameSample(Math.max(0L, nanos));
        } else {
            deterministicWarmupFrameCount.increment();
        }
        maybeWriteSnapshot();
    }

    public static void recordPresentedFrame() {
        if (!isEnabled()) {
            return;
        }
        presentedFrameCount.increment();
        maybeWriteSnapshot();
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
        FrameStats frameStats = frameStats();
        Runtime runtime = Runtime.getRuntime();

        StringBuilder builder = new StringBuilder(2048);
        builder.append("timestamp_utc=").append(Instant.now()).append('\n');
        builder.append("backend=").append(System.getProperty("mattmc.perfAudit.backend", "vulkan")).append('\n');
        builder.append("diagnostic_issue=").append(dominantIssue(primarySubmitMs, descriptorBindMs, bindingBuildMs, frameStartWaitMs)).append('\n');
        builder.append("begin_frame_call_count=").append(beginFrameCallCount.sum()).append('\n');
        builder.append("begin_frame_acquire_success_count=").append(beginFrameAcquireSuccessCount.sum()).append('\n');
        builder.append("begin_frame_fence_wait_ms=").append(format(frameStartWaitMs)).append('\n');
        builder.append("render_pass_begin_count=").append(renderPassBeginCount.sum()).append('\n');
        builder.append("primary_submit_count=").append(primarySubmitCount.sum()).append('\n');
        builder.append("primary_submit_total_ms=").append(format(primarySubmitMs)).append('\n');
        builder.append("primary_submit_wait_before_ms=").append(format(nanosToMillis(primarySubmitWaitBeforeNanos.sum()))).append('\n');
        builder.append("primary_submit_queue_submit_ms=").append(format(nanosToMillis(primarySubmitQueueSubmitNanos.sum()))).append('\n');
        builder.append("primary_submit_wait_after_ms=").append(format(nanosToMillis(primarySubmitWaitAfterNanos.sum()))).append('\n');
        builder.append("primary_submit_other_ms=").append(format(nanosToMillis(primarySubmitOtherNanos.sum()))).append('\n');
        builder.append("presented_frame_count=").append(presentedFrameCount.sum()).append('\n');
        builder.append("deterministic_warmup_frame_count=").append(deterministicWarmupFrameCount.sum()).append('\n');
        builder.append("deterministic_measured_frame_count=").append(deterministicMeasuredFrameCount.sum()).append('\n');
        builder.append("deterministic_measured_frame_samples=").append(frameStats.count()).append('\n');
        builder.append("deterministic_measured_frame_overflow_count=").append(measuredFrameOverflowCount).append('\n');
        builder.append("deterministic_measured_frame_total_ms=").append(format(nanosToMillis(deterministicMeasuredFrameTotalNanos.sum()))).append('\n');
        builder.append("deterministic_measured_frame_median_ms=").append(format(nanosToMillis(frameStats.medianNanos()))).append('\n');
        builder.append("deterministic_measured_frame_p95_ms=").append(format(nanosToMillis(frameStats.p95Nanos()))).append('\n');
        builder.append("deterministic_measured_frame_p99_ms=").append(format(nanosToMillis(frameStats.p99Nanos()))).append('\n');
        builder.append("deterministic_measured_frame_worst_ms=").append(format(nanosToMillis(frameStats.worstNanos()))).append('\n');
        builder.append("graphics_draw_count=").append(graphicsDrawCount.sum()).append('\n');
        builder.append("descriptor_bind_count=").append(descriptorBindCount.sum()).append('\n');
        builder.append("descriptor_bind_total_ms=").append(format(descriptorBindMs)).append('\n');
        builder.append("binding_build_count=").append(bindingBuildCount.sum()).append('\n');
        builder.append("binding_build_total_ms=").append(format(bindingBuildMs)).append('\n');
        builder.append("binding_build_complete_coverage_count=").append(bindingBuildCompleteCoverageCount.sum()).append('\n');
        builder.append("primary_submits_per_presented_frame=").append(format(primarySubmitCount.sum() / presentedFrames)).append('\n');
        builder.append("render_passes_per_presented_frame=").append(format(renderPassBeginCount.sum() / presentedFrames)).append('\n');
        builder.append("descriptor_binds_per_presented_frame=").append(format(descriptorBindCount.sum() / presentedFrames)).append('\n');
        builder.append("binding_builds_per_presented_frame=").append(format(bindingBuildCount.sum() / presentedFrames)).append('\n');
        builder.append("primary_submit_share_of_tracked_cpu_time_pct=").append(format(percent(primarySubmitMs, trackedCpuMs))).append('\n');
        builder.append("descriptor_bind_share_of_tracked_cpu_time_pct=").append(format(percent(descriptorBindMs, trackedCpuMs))).append('\n');
        builder.append("binding_build_share_of_tracked_cpu_time_pct=").append(format(percent(bindingBuildMs, trackedCpuMs))).append('\n');
        builder.append("frame_start_wait_share_of_tracked_cpu_time_pct=").append(format(percent(frameStartWaitMs, trackedCpuMs))).append('\n');
        builder.append("gc_collection_count_delta=").append(Math.max(0L, gcCollectionCount() - initialGcCount)).append('\n');
        builder.append("gc_collection_time_ms_delta=").append(Math.max(0L, gcCollectionTimeMillis() - initialGcTimeMillis)).append('\n');
        builder.append("java_heap_used_bytes=").append(Math.max(0L, runtime.totalMemory() - runtime.freeMemory())).append('\n');
        builder.append("java_heap_committed_bytes=").append(runtime.totalMemory()).append('\n');
        builder.append("java_heap_max_bytes=").append(runtime.maxMemory()).append('\n');
        appendPhaseSummary(builder);
        return builder.toString();
    }

    private static void appendPhaseSummary(StringBuilder builder) {
        List<Map.Entry<String, PhaseCounters>> entries = new ArrayList<>(phases.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, PhaseCounters> entry : entries) {
            String key = normalizeKey(entry.getKey());
            PhaseCounters counters = entry.getValue();
            builder.append("phase.").append(key).append(".count=").append(counters.count.sum()).append('\n');
            builder.append("phase.").append(key).append(".total_ms=").append(format(nanosToMillis(counters.totalNanos.sum()))).append('\n');
        }
        entries.sort((left, right) -> Long.compare(right.getValue().totalNanos.sum(), left.getValue().totalNanos.sum()));
        int rank = 1;
        for (Map.Entry<String, PhaseCounters> entry : entries) {
            if (rank > 10) {
                break;
            }
            builder.append("phase_rank_").append(rank).append('=')
                .append(entry.getKey()).append(':').append(format(nanosToMillis(entry.getValue().totalNanos.sum()))).append("ms\n");
            rank++;
        }
    }

    private static String normalizeKey(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            builder.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return builder.toString();
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
            copy = Arrays.copyOf(measuredFrameNanos, measuredFrameSampleCount);
        }
        Arrays.sort(copy);
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

    private record PhaseCounters(LongAdder count, LongAdder totalNanos) {
        private PhaseCounters() {
            this(new LongAdder(), new LongAdder());
        }
    }

    private record FrameStats(int count, long medianNanos, long p95Nanos, long p99Nanos, long worstNanos) {
    }
}
