package net.vulkanic;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class VulkanPerfAudit {
    private static final boolean ENABLED = Boolean.getBoolean("mattmc.vulkan.perfAudit");
    private static final long SNAPSHOT_INTERVAL_NANOS = 5_000_000_000L;

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
        builder.append("backend=vulkan\n");
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
        builder.append("frame_submit_count=0\n");
        builder.append("frame_submit_total_ms=0.0000\n");
        builder.append("frame_submit_queue_submit_ms=0.0000\n");
        builder.append("presented_frame_count=").append(presentedFrameCount.sum()).append('\n');
        builder.append("descriptor_bind_count=").append(descriptorBindCount.sum()).append('\n');
        builder.append("descriptor_bind_total_ms=").append(format(descriptorBindMs)).append('\n');
        builder.append("pipeline_resolve_count=0\n");
        builder.append("pipeline_resolve_total_ms=0.0000\n");
        builder.append("pipeline_resolve_miss_count=0\n");
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
        return builder.toString();
    }

    private static Path initReportFile() {
        if (!ENABLED) {
            return null;
        }

        String reportDir = System.getProperty("mattmc.vulkan.perfAuditReportDir", "").trim();
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
}