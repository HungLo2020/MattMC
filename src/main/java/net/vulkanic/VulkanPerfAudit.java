package net.vulkanic;

import net.vulkanic.diagnostics.VulkanicDiagnostics;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final boolean LEGACY_GRAPHICS_LOWERING_ENABLED =
        Boolean.getBoolean("mattmc.perfAudit.legacyGraphicsLowering");
    private static final boolean RESOURCE_PLAN_BREAKDOWN_ENABLED =
        Boolean.getBoolean("mattmc.perfAudit.resourcePlanBreakdown");
    private static final boolean OPENGL_DRAW_DETAIL_ENABLED =
        Boolean.getBoolean("mattmc.perfAudit.openGlDrawDetails");
    private static final boolean OPENGL_STATE_DETAIL_ENABLED =
        Boolean.getBoolean("mattmc.perfAudit.openGlStateDetails");
    private static final boolean DETERMINISTIC_PERFORMANCE_CAPTURE =
        Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.performanceMode");
    private static final boolean MEMORY_SAMPLES_ENABLED =
        Boolean.getBoolean("mattmc.perfAudit.memorySamples");
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
    private static final LongAdder descriptorPlanCount = new LongAdder();
    private static final LongAdder descriptorPlanTotalNanos = new LongAdder();
    private static final LongAdder descriptorPlanCacheableCount = new LongAdder();
    private static final LongAdder descriptorPlanNonCacheableCount = new LongAdder();
    private static final LongAdder descriptorPlanBindingCount = new LongAdder();
    private static final LongAdder descriptorPlanReuseLookupCount = new LongAdder();
    private static final LongAdder descriptorPlanReuseHitCount = new LongAdder();
    private static final LongAdder descriptorPlanReuseMissCount = new LongAdder();
    private static final LongAdder descriptorPlanReuseEquivalentConsecutiveCount = new LongAdder();
    private static final AtomicLong descriptorPlanReuseCacheSize = new AtomicLong();
    private static final LongAdder descriptorCacheLookupCount = new LongAdder();
    private static final LongAdder descriptorCacheHitCount = new LongAdder();
    private static final LongAdder descriptorCacheMissCount = new LongAdder();
    private static final LongAdder descriptorCacheInvalidationCount = new LongAdder();
    private static final LongAdder descriptorCacheInvalidatedEntryCount = new LongAdder();
    private static final Map<String, LongAdder> descriptorCacheMissReasons = new ConcurrentHashMap<>();
    private static final LongAdder descriptorSetAllocationCount = new LongAdder();
    private static final LongAdder descriptorSetAllocationNanos = new LongAdder();
    private static final LongAdder descriptorSetUpdateCount = new LongAdder();
    private static final LongAdder descriptorSetUpdateNanos = new LongAdder();
    private static final LongAdder descriptorWriteCount = new LongAdder();
    private static final LongAdder descriptorWriteSamplerCount = new LongAdder();
    private static final LongAdder descriptorWriteUniformBufferCount = new LongAdder();
    private static final LongAdder descriptorWriteStorageImageCount = new LongAdder();
    private static final LongAdder descriptorWriteTexelBufferCount = new LongAdder();
    private static final LongAdder descriptorCommandBindCount = new LongAdder();
    private static final LongAdder descriptorCommandBindNanos = new LongAdder();
    private static final LongAdder descriptorTransientUniformCopyCount = new LongAdder();
    private static final LongAdder descriptorTransientUniformCopyBytes = new LongAdder();
    private static final LongAdder descriptorTransientUniformCopyNanos = new LongAdder();
    private static final LongAdder dynamicTransformsBindingCount = new LongAdder();
    private static final LongAdder dynamicTransformsHandleChangeCount = new LongAdder();
    private static final LongAdder dynamicTransformsOffsetChangeCount = new LongAdder();
    private static final LongAdder dynamicTransformsRangeChangeCount = new LongAdder();
    private static final LongAdder dynamicTransformsContentChangeCount = new LongAdder();
    private static final LongAdder dynamicTransformsContentReuseCount = new LongAdder();
    private static final AtomicLong dynamicTransformsDistinctContentCount = new AtomicLong();
    private static final LongAdder dynamicTransformsArenaBufferAllocationCount = new LongAdder();
    private static final LongAdder dynamicTransformsArenaGrowthCount = new LongAdder();
    private static final LongAdder dynamicTransformsArenaUploadCount = new LongAdder();
    private static final LongAdder dynamicTransformsArenaUploadBytes = new LongAdder();
    private static final LongAdder dynamicTransformsArenaReservedBytes = new LongAdder();
    private static final LongAdder dynamicTransformsArenaReuseHitCount = new LongAdder();
    private static final AtomicLong dynamicTransformsArenaHighWaterBytes = new AtomicLong();
    private static final AtomicLong dynamicTransformsArenaCapacityBytes = new AtomicLong();
    private static final LongAdder standaloneUniformBindingCount = new LongAdder();
    private static final LongAdder standaloneUniformHandleChangeCount = new LongAdder();
    private static final LongAdder standaloneUniformOffsetChangeCount = new LongAdder();
    private static final LongAdder standaloneUniformRangeChangeCount = new LongAdder();
    private static final LongAdder standaloneUniformContentChangeCount = new LongAdder();
    private static final LongAdder standaloneUniformContentReuseCount = new LongAdder();
    private static final AtomicLong standaloneUniformDistinctContentCount = new AtomicLong();
    private static final LongAdder standaloneUniformArenaBufferAllocationCount = new LongAdder();
    private static final LongAdder standaloneUniformArenaGrowthCount = new LongAdder();
    private static final LongAdder standaloneUniformArenaUploadCount = new LongAdder();
    private static final LongAdder standaloneUniformArenaUploadBytes = new LongAdder();
    private static final LongAdder standaloneUniformArenaReservedBytes = new LongAdder();
    private static final LongAdder standaloneUniformArenaReuseHitCount = new LongAdder();
    private static final LongAdder standaloneUniformSourceReuseCount = new LongAdder();
    private static final AtomicLong standaloneUniformArenaHighWaterBytes = new AtomicLong();
    private static final AtomicLong standaloneUniformArenaCapacityBytes = new AtomicLong();
    private static final Map<String, DescriptorCounters> descriptorPipelines = new ConcurrentHashMap<>();
    private static final Map<String, StandaloneUniformCounters> standaloneUniformPipelines = new ConcurrentHashMap<>();
    private static final Map<String, LegacyGraphicsLoweringCounters> legacyGraphicsLoweringFamilies = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> legacyGraphicsLoweringCacheInvalidationReasons = new ConcurrentHashMap<>();
    private static final LongAdder bindingBuildCount = new LongAdder();
    private static final LongAdder bindingBuildTotalNanos = new LongAdder();
    private static final LongAdder bindingBuildCompleteCoverageCount = new LongAdder();
    private static final LongAdder presentedFrameCount = new LongAdder();
    private static final LongAdder deterministicWarmupFrameCount = new LongAdder();
    private static final LongAdder deterministicMeasuredFrameCount = new LongAdder();
    private static final LongAdder deterministicMeasuredFrameTotalNanos = new LongAdder();
    private static final LongAdder graphicsDrawCount = new LongAdder();
    private static final LongAdder galV2GraphicsDrawCount = new LongAdder();
    private static final LongAdder galV2LegacyFallbackDrawCount = new LongAdder();
    private static final Map<String, LongAdder> galV2FallbackReasons = new ConcurrentHashMap<>();
    private static final LongAdder galV2ResourceLayoutLookupCount = new LongAdder();
    private static final LongAdder galV2ResourceLayoutCreateCount = new LongAdder();
    private static final AtomicLong galV2ResourceLayoutCacheSize = new AtomicLong();
    private static final LongAdder galV2ResourceSetLookupCount = new LongAdder();
    private static final LongAdder galV2ResourceSetCreateCount = new LongAdder();
    private static final AtomicLong galV2ResourceSetCacheSize = new AtomicLong();
    private static final LongAdder galV2DrawTemplateLookupCount = new LongAdder();
    private static final LongAdder galV2DrawTemplateCreateCount = new LongAdder();
    private static final AtomicLong galV2DrawTemplateCacheSize = new AtomicLong();
    private static final LongAdder galV2CommandStreamCount = new LongAdder();
    private static final LongAdder galV2CommandStreamCommandCount = new LongAdder();
    private static final LongAdder galV2CommandStreamBindCount = new LongAdder();
    private static final LongAdder galV2CommandStreamDrawCount = new LongAdder();
    private static final LongAdder galV2CommandStreamSuppressedBindCount = new LongAdder();
    private static final LongAdder galV2PassCommandBufferCount = new LongAdder();
    private static final LongAdder galV2PassCommandBufferDrawCount = new LongAdder();
    private static final LongAdder galV2PassCommandBufferCommandCount = new LongAdder();
    private static final LongAdder galV2PassCommandBufferEliminatedExecutorCalls = new LongAdder();
    private static final LongAdder galV2RegistryPruneCount = new LongAdder();
    private static final AtomicLong galV2RegistryPrunedEntryCount = new AtomicLong();
    private static final AtomicLong galV2RegistryEntryCount = new AtomicLong();
    private static final AtomicLong galV2RegistryHighWaterEntryCount = new AtomicLong();
    private static final AtomicLong galV2RegistryHandleCount = new AtomicLong();
    private static final AtomicLong galV2RegistryGraphicsObjectCount = new AtomicLong();
    private static final AtomicLong galV2RegistryResourceLayoutCount = new AtomicLong();
    private static final AtomicLong galV2RegistryResourceSetCount = new AtomicLong();
    private static final AtomicLong galV2RegistryUniformLayoutCount = new AtomicLong();
    private static final AtomicLong galV2RegistryUniformBindingCount = new AtomicLong();
    private static final AtomicLong galV2RegistryDrawTemplateCount = new AtomicLong();
    private static final Map<String, LongAdder> openGlGalV2RequestedDraws = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> openGlGalV2EmittedDraws = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> openGlGalV2RequestedDrawDetails = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> openGlGalV2EmittedDrawDetails = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> openGlGalV2StateRequested = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> openGlGalV2StateEmitted = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> measuredFrameWorkloadCounters = new ConcurrentHashMap<>();
    private static final AtomicLong deterministicPerformanceFrameOrdinal = new AtomicLong(-1L);
    private static final AtomicLong deterministicPerformanceMeasuredOrdinal = new AtomicLong(-1L);
    private static final LongAdder computeDispatchCount = new LongAdder();
    private static final LongAdder clearCount = new LongAdder();
    private static final LongAdder transferCount = new LongAdder();
    private static final Map<String, PhaseCounters> phases = new ConcurrentHashMap<>();
    private static final long[] measuredFrameNanos = new long[MAX_FRAME_SAMPLES];
    private static final Object measuredFrameLock = new Object();
    private static int measuredFrameSampleCount;
    private static long measuredFrameOverflowCount;
    private static final LongAdder memorySampleCount = new LongAdder();
    private static final AtomicLong memoryLatestHeapUsedBytes = new AtomicLong(-1L);
    private static final AtomicLong memoryLatestHeapCommittedBytes = new AtomicLong(-1L);
    private static final AtomicLong memoryLatestHeapMaxBytes = new AtomicLong(-1L);
    private static final AtomicLong memoryLatestProcessRssKb = new AtomicLong(-1L);
    private static final AtomicLong memoryLatestRssAnonKb = new AtomicLong(-1L);
    private static final AtomicLong memoryLatestRssFileKb = new AtomicLong(-1L);
    private static final AtomicLong memoryLatestRssShmemKb = new AtomicLong(-1L);
    private static final AtomicLong memoryPeakHeapCommittedBytes = new AtomicLong(-1L);
    private static final AtomicLong memoryPeakProcessRssKb = new AtomicLong(-1L);
    private static final AtomicLong memoryPeakRssAnonKb = new AtomicLong(-1L);
    private static final AtomicLong memoryPeakRssShmemKb = new AtomicLong(-1L);
    private static final long initialGcCount = gcCollectionCount();
    private static final long initialGcTimeMillis = gcCollectionTimeMillis();

    private static final AtomicLong lastSnapshotNanos = new AtomicLong();
    private static volatile boolean deterministicMeasurementFrameActive;
    private static volatile DynamicTransformsSample lastDynamicTransformsSample;
    private static final Map<Long, Boolean> dynamicTransformsContentHashes = new ConcurrentHashMap<>();
    private static final Map<String, UniformBindingSample> lastStandaloneUniformSampleByPipeline = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> standaloneUniformContentHashes = new ConcurrentHashMap<>();
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

    public static boolean shouldTraceLegacyGraphicsLowering() {
        return descriptorEventsEnabled() && LEGACY_GRAPHICS_LOWERING_ENABLED;
    }

    public static boolean shouldTraceResourcePlanBreakdown() {
        return shouldTraceLegacyGraphicsLowering() && RESOURCE_PLAN_BREAKDOWN_ENABLED;
    }

    public static void setDeterministicMeasurementFrameActive(boolean active) {
        if (isEnabled() && DETERMINISTIC_PERFORMANCE_CAPTURE) {
            deterministicMeasurementFrameActive = active;
        }
    }

    private static boolean descriptorEventsEnabled() {
        return isEnabled() && (!DETERMINISTIC_PERFORMANCE_CAPTURE || deterministicMeasurementFrameActive);
    }

    public static void recordBeginFrameCall() {
        if (!descriptorEventsEnabled()) {
            return;
        }
        beginFrameCallCount.increment();
    }

    public static void recordBeginFrameFenceWait(long nanos) {
        if (!descriptorEventsEnabled()) {
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
        if (!descriptorEventsEnabled()) {
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
        if (!descriptorEventsEnabled()) {
            return;
        }
        descriptorBindCount.increment();
        descriptorBindTotalNanos.add(Math.max(0L, nanos));
    }

    public static void recordDescriptorPlan(
        String pipelineLocation,
        boolean cacheable,
        int bindingCount,
        int samplerBindings,
        int uniformBufferBindings,
        int storageImageBindings,
        int texelBufferBindings,
        long nanos
    ) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        long clampedNanos = Math.max(0L, nanos);
        int clampedBindingCount = Math.max(0, bindingCount);
        descriptorPlanCount.increment();
        descriptorPlanTotalNanos.add(clampedNanos);
        descriptorPlanBindingCount.add(clampedBindingCount);
        counters.planCount.increment();
        counters.planNanos.add(clampedNanos);
        counters.planBindings.add(clampedBindingCount);
        counters.samplerBindings.add(Math.max(0, samplerBindings));
        counters.uniformBufferBindings.add(Math.max(0, uniformBufferBindings));
        counters.storageImageBindings.add(Math.max(0, storageImageBindings));
        counters.texelBufferBindings.add(Math.max(0, texelBufferBindings));
        if (cacheable) {
            descriptorPlanCacheableCount.increment();
            counters.cacheablePlanCount.increment();
        } else {
            descriptorPlanNonCacheableCount.increment();
            counters.nonCacheablePlanCount.increment();
        }
    }

    public static void recordDescriptorPlanReuseLookup(
        String pipelineLocation,
        boolean hit,
        boolean equivalentConsecutive,
        int cacheSize
    ) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        descriptorPlanReuseLookupCount.increment();
        counters.planReuseLookupCount.increment();
        if (hit) {
            descriptorPlanReuseHitCount.increment();
            counters.planReuseHitCount.increment();
        } else {
            descriptorPlanReuseMissCount.increment();
            counters.planReuseMissCount.increment();
        }
        if (equivalentConsecutive) {
            descriptorPlanReuseEquivalentConsecutiveCount.increment();
            counters.equivalentConsecutivePlanCount.increment();
        }
        descriptorPlanReuseCacheSize.set(Math.max(0, cacheSize));
    }

    public static void recordDynamicTransformsBinding(long bufferHandle, long offset, long range, long contentHash) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        dynamicTransformsBindingCount.increment();
        DynamicTransformsSample previous = lastDynamicTransformsSample;
        if (previous != null) {
            if (previous.bufferHandle() != bufferHandle) {
                dynamicTransformsHandleChangeCount.increment();
            }
            if (previous.offset() != offset) {
                dynamicTransformsOffsetChangeCount.increment();
            }
            if (previous.range() != range) {
                dynamicTransformsRangeChangeCount.increment();
            }
            if (previous.contentHash() != contentHash) {
                dynamicTransformsContentChangeCount.increment();
            } else {
                dynamicTransformsContentReuseCount.increment();
            }
        }
        if (dynamicTransformsContentHashes.size() < 4096) {
            dynamicTransformsContentHashes.putIfAbsent(contentHash, Boolean.TRUE);
            dynamicTransformsDistinctContentCount.set(dynamicTransformsContentHashes.size());
        }
        lastDynamicTransformsSample = new DynamicTransformsSample(bufferHandle, offset, range, contentHash);
    }

    public static void recordDynamicTransformsArenaBufferAllocation(int capacityBytes, boolean growth) {
        if (!isEnabled()) {
            return;
        }
        dynamicTransformsArenaBufferAllocationCount.increment();
        if (growth) {
            dynamicTransformsArenaGrowthCount.increment();
        }
        updateMax(dynamicTransformsArenaCapacityBytes, Math.max(0, capacityBytes));
    }

    public static void recordDynamicTransformsArenaAllocation(
        boolean reused,
        int reservedBytes,
        int writtenBytes,
        int frameHighWaterBytes,
        int frameCapacityBytes
    ) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        dynamicTransformsArenaReservedBytes.add(Math.max(0, reservedBytes));
        if (reused) {
            dynamicTransformsArenaReuseHitCount.increment();
        }
        if (writtenBytes > 0) {
            dynamicTransformsArenaUploadCount.increment();
            dynamicTransformsArenaUploadBytes.add(writtenBytes);
        }
        updateMax(dynamicTransformsArenaHighWaterBytes, Math.max(0, frameHighWaterBytes));
        updateMax(dynamicTransformsArenaCapacityBytes, Math.max(0, frameCapacityBytes));
    }

    public static void recordStandaloneUniformBinding(
        String pipelineLocation,
        String bindingName,
        long bufferHandle,
        long offset,
        long range,
        long contentHash
    ) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        String pipeline = descriptorFamily(pipelineLocation);
        StandaloneUniformCounters counters =
            standaloneUniformPipelines.computeIfAbsent(pipeline, ignored -> new StandaloneUniformCounters());
        standaloneUniformBindingCount.increment();
        counters.bindingCount.increment();

        String key = pipeline + "|" + normalizeKey(bindingName, "unknown");
        UniformBindingSample previous = lastStandaloneUniformSampleByPipeline.get(key);
        if (previous != null) {
            if (previous.bufferHandle() != bufferHandle) {
                standaloneUniformHandleChangeCount.increment();
                counters.handleChangeCount.increment();
            }
            if (previous.offset() != offset) {
                standaloneUniformOffsetChangeCount.increment();
                counters.offsetChangeCount.increment();
            }
            if (previous.range() != range) {
                standaloneUniformRangeChangeCount.increment();
                counters.rangeChangeCount.increment();
            }
            if (previous.contentHash() != contentHash) {
                standaloneUniformContentChangeCount.increment();
                counters.contentChangeCount.increment();
            } else {
                standaloneUniformContentReuseCount.increment();
                counters.contentReuseCount.increment();
            }
        }
        if (standaloneUniformContentHashes.size() < 8192) {
            standaloneUniformContentHashes.putIfAbsent(contentHash, Boolean.TRUE);
            standaloneUniformDistinctContentCount.set(standaloneUniformContentHashes.size());
        }
        lastStandaloneUniformSampleByPipeline.put(key, new UniformBindingSample(bufferHandle, offset, range, contentHash));
    }

    public static void recordStandaloneUniformArenaBufferAllocation(int capacityBytes, boolean growth) {
        if (!isEnabled()) {
            return;
        }
        standaloneUniformArenaBufferAllocationCount.increment();
        if (growth) {
            standaloneUniformArenaGrowthCount.increment();
        }
        updateMax(standaloneUniformArenaCapacityBytes, Math.max(0, capacityBytes));
    }

    public static void recordStandaloneUniformArenaAllocation(
        String pipelineLocation,
        boolean reused,
        int reservedBytes,
        int writtenBytes,
        int frameHighWaterBytes,
        int frameCapacityBytes
    ) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        StandaloneUniformCounters counters = standaloneUniformPipelines.computeIfAbsent(
            descriptorFamily(pipelineLocation),
            ignored -> new StandaloneUniformCounters()
        );
        int clampedReserved = Math.max(0, reservedBytes);
        int clampedWritten = Math.max(0, writtenBytes);
        standaloneUniformArenaReservedBytes.add(clampedReserved);
        counters.reservedBytes.add(clampedReserved);
        if (reused) {
            standaloneUniformArenaReuseHitCount.increment();
            counters.reuseHitCount.increment();
        }
        if (clampedWritten > 0) {
            standaloneUniformArenaUploadCount.increment();
            standaloneUniformArenaUploadBytes.add(clampedWritten);
            counters.uploadCount.increment();
            counters.uploadBytes.add(clampedWritten);
        }
        updateMax(standaloneUniformArenaHighWaterBytes, Math.max(0, frameHighWaterBytes));
        updateMax(standaloneUniformArenaCapacityBytes, Math.max(0, frameCapacityBytes));
    }

    public static void recordStandaloneUniformSourceReuse(String pipelineLocation) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        standaloneUniformSourceReuseCount.increment();
        standaloneUniformPipelines.computeIfAbsent(
            descriptorFamily(pipelineLocation),
            ignored -> new StandaloneUniformCounters()
        ).sourceReuseCount.increment();
    }

    public static void recordLegacyGraphicsLoweringStep(String pipelineLocation, String step, long nanos) {
        if (!shouldTraceLegacyGraphicsLowering()) {
            return;
        }
        legacyGraphicsLoweringCounters(pipelineLocation).step(normalizeKey(step, "unknown")).add(Math.max(0L, nanos));
    }

    public static void recordLegacyGraphicsLoweringCacheLookup(
        String pipelineLocation,
        String artifact,
        boolean hit,
        int cacheSize,
        int highWater
    ) {
        if (!shouldTraceLegacyGraphicsLowering()) {
            return;
        }
        legacyGraphicsLoweringCounters(pipelineLocation)
            .cache(normalizeKey(artifact, "unknown"))
            .record(hit, cacheSize, highWater);
    }

    public static void recordLegacyGraphicsLoweringCacheInvalidation(String reason, int removedEntries) {
        if (!shouldTraceLegacyGraphicsLowering()) {
            return;
        }
        legacyGraphicsLoweringCacheInvalidationReasons
            .computeIfAbsent(normalizeKey(reason, "unknown"), ignored -> new LongAdder())
            .add(Math.max(1, removedEntries));
    }

    public static void recordLegacyGraphicsLoweringAllocation(
        String pipelineLocation,
        String structure,
        long objectCount,
        long estimatedBytes
    ) {
        if (!shouldTraceLegacyGraphicsLowering()) {
            return;
        }
        legacyGraphicsLoweringCounters(pipelineLocation)
            .allocation(normalizeKey(structure, "unknown"))
            .record(objectCount, estimatedBytes);
    }

    public static void recordDescriptorCacheLookup(String pipelineLocation, boolean cacheable, boolean hit) {
        if (!descriptorEventsEnabled() || !cacheable) {
            return;
        }
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        descriptorCacheLookupCount.increment();
        counters.cacheLookupCount.increment();
        if (hit) {
            descriptorCacheHitCount.increment();
            counters.cacheHitCount.increment();
        } else {
            descriptorCacheMissCount.increment();
            counters.cacheMissCount.increment();
        }
    }

    public static void recordDescriptorCacheMissReason(String pipelineLocation, String reason) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        String normalizedReason = normalizeKey(reason, "unknown");
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        descriptorCacheMissReasons
            .computeIfAbsent(normalizedReason, ignored -> new LongAdder())
            .increment();
        counters.cacheMissReasons
            .computeIfAbsent(normalizedReason, ignored -> new LongAdder())
            .increment();
    }

    public static void recordDescriptorCacheInvalidation(String reason, int entryCount) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        descriptorCacheInvalidationCount.increment();
        descriptorCacheInvalidatedEntryCount.add(Math.max(0, entryCount));
        String normalizedReason = "invalidation:" + normalizeKey(reason, "unknown");
        descriptorCacheMissReasons
            .computeIfAbsent(normalizedReason, ignored -> new LongAdder())
            .increment();
    }

    public static void recordDescriptorSetAllocation(String pipelineLocation, long nanos) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        long clampedNanos = Math.max(0L, nanos);
        descriptorSetAllocationCount.increment();
        descriptorSetAllocationNanos.add(clampedNanos);
        counters.allocationCount.increment();
        counters.allocationNanos.add(clampedNanos);
    }

    public static void recordDescriptorSetUpdate(
        String pipelineLocation,
        int bindingCount,
        int samplerWrites,
        int uniformBufferWrites,
        int storageImageWrites,
        int texelBufferWrites,
        long nanos
    ) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        long clampedNanos = Math.max(0L, nanos);
        int clampedBindingCount = Math.max(0, bindingCount);
        int clampedSamplerWrites = Math.max(0, samplerWrites);
        int clampedUniformWrites = Math.max(0, uniformBufferWrites);
        int clampedStorageWrites = Math.max(0, storageImageWrites);
        int clampedTexelWrites = Math.max(0, texelBufferWrites);
        descriptorSetUpdateCount.increment();
        descriptorSetUpdateNanos.add(clampedNanos);
        descriptorWriteCount.add(clampedBindingCount);
        descriptorWriteSamplerCount.add(clampedSamplerWrites);
        descriptorWriteUniformBufferCount.add(clampedUniformWrites);
        descriptorWriteStorageImageCount.add(clampedStorageWrites);
        descriptorWriteTexelBufferCount.add(clampedTexelWrites);
        counters.updateCount.increment();
        counters.updateNanos.add(clampedNanos);
        counters.writeCount.add(clampedBindingCount);
        counters.samplerWrites.add(clampedSamplerWrites);
        counters.uniformBufferWrites.add(clampedUniformWrites);
        counters.storageImageWrites.add(clampedStorageWrites);
        counters.texelBufferWrites.add(clampedTexelWrites);
    }

    public static void recordDescriptorCommandBind(String pipelineLocation, long nanos) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        long clampedNanos = Math.max(0L, nanos);
        descriptorCommandBindCount.increment();
        descriptorCommandBindNanos.add(clampedNanos);
        counters.commandBindCount.increment();
        counters.commandBindNanos.add(clampedNanos);
    }

    public static void recordDescriptorTransientUniformCopy(String pipelineLocation, int bytes, long nanos) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        DescriptorCounters counters = descriptorCounters(pipelineLocation);
        long clampedNanos = Math.max(0L, nanos);
        int clampedBytes = Math.max(0, bytes);
        descriptorTransientUniformCopyCount.increment();
        descriptorTransientUniformCopyBytes.add(clampedBytes);
        descriptorTransientUniformCopyNanos.add(clampedNanos);
        counters.transientUniformCopyCount.increment();
        counters.transientUniformCopyBytes.add(clampedBytes);
        counters.transientUniformCopyNanos.add(clampedNanos);
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
            recordMemorySample();
        } else {
            deterministicWarmupFrameCount.increment();
        }
        maybeWriteSnapshot();
    }

    public static void setDeterministicPerformanceFrameContext(int frameOrdinal, boolean measurementFrame) {
        if (!isEnabled()) {
            return;
        }
        deterministicPerformanceFrameOrdinal.set(Math.max(0, frameOrdinal));
        deterministicPerformanceMeasuredOrdinal.set(measurementFrame ? Math.max(0, deterministicMeasuredFrameCount.sum()) : -1L);
    }

    public static void recordPhase(String name, long nanos) {
        if (!descriptorEventsEnabled() || name == null || name.isBlank()) {
            return;
        }
        phases.computeIfAbsent(name, ignored -> new PhaseCounters()).add(Math.max(0L, nanos));
    }

    public static void recordGraphicsDraw() {
        if (descriptorEventsEnabled()) {
            graphicsDrawCount.increment();
            recordMeasuredFrameWorkload("graphicsDraws", 1L);
        }
    }

    public static void recordGalV2GraphicsDraw(boolean explicitV2) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        if (explicitV2) {
            galV2GraphicsDrawCount.increment();
            recordMeasuredFrameWorkload("galV2GraphicsDraws", 1L);
        } else {
            galV2LegacyFallbackDrawCount.increment();
            recordMeasuredFrameWorkload("galV2FallbackDraws", 1L);
        }
    }

    public static void recordGalV2FallbackReason(String reason) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        String normalized = reason == null || reason.isBlank() ? "unknown" : reason;
        galV2FallbackReasons.computeIfAbsent(normalized, ignored -> new LongAdder()).increment();
    }

    public static void recordGalV2ResourceLayoutLookup(boolean created, int cacheSize) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        galV2ResourceLayoutLookupCount.increment();
        if (created) {
            galV2ResourceLayoutCreateCount.increment();
        }
        galV2ResourceLayoutCacheSize.set(Math.max(0, cacheSize));
    }

    public static void recordGalV2ResourceSetLookup(boolean created, int cacheSize) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        galV2ResourceSetLookupCount.increment();
        if (created) {
            galV2ResourceSetCreateCount.increment();
        }
        galV2ResourceSetCacheSize.set(Math.max(0, cacheSize));
    }

    public static void recordGalV2DrawTemplateLookup(boolean created, int cacheSize) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        galV2DrawTemplateLookupCount.increment();
        if (created) {
            galV2DrawTemplateCreateCount.increment();
        }
        galV2DrawTemplateCacheSize.set(Math.max(0, cacheSize));
    }

    public static void recordGalV2CommandStream(int commandCount, int bindCount, int drawCount, int suppressedBindCount) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        galV2CommandStreamCount.increment();
        galV2CommandStreamCommandCount.add(Math.max(0, commandCount));
        galV2CommandStreamBindCount.add(Math.max(0, bindCount));
        galV2CommandStreamDrawCount.add(Math.max(0, drawCount));
        galV2CommandStreamSuppressedBindCount.add(Math.max(0, suppressedBindCount));
    }

    public static void recordGalV2PassCommandBuffer(int drawCount, int commandCount) {
        if (!descriptorEventsEnabled()) {
            return;
        }
        int safeDrawCount = Math.max(0, drawCount);
        galV2PassCommandBufferCount.increment();
        galV2PassCommandBufferDrawCount.add(safeDrawCount);
        galV2PassCommandBufferCommandCount.add(Math.max(0, commandCount));
        if (safeDrawCount > 1) {
            galV2PassCommandBufferEliminatedExecutorCalls.add(safeDrawCount - 1L);
        }
    }

    public static void recordGalV2RegistryPrune(int entryCount) {
        if (!isEnabled()) {
            return;
        }
        galV2RegistryPruneCount.increment();
        galV2RegistryPrunedEntryCount.set(Math.max(0, entryCount));
    }

    public static void recordGalV2RegistrySnapshot(
        int totalEntries,
        int handleEntries,
        int graphicsObjectEntries,
        int resourceLayoutEntries,
        int resourceSetEntries,
        int uniformLayoutEntries,
        int uniformBindingEntries,
        int drawTemplateEntries
    ) {
        if (!isEnabled()) {
            return;
        }
        int clampedTotal = Math.max(0, totalEntries);
        galV2RegistryEntryCount.set(clampedTotal);
        updateMax(galV2RegistryHighWaterEntryCount, clampedTotal);
        galV2RegistryHandleCount.set(Math.max(0, handleEntries));
        galV2RegistryGraphicsObjectCount.set(Math.max(0, graphicsObjectEntries));
        galV2RegistryResourceLayoutCount.set(Math.max(0, resourceLayoutEntries));
        galV2RegistryResourceSetCount.set(Math.max(0, resourceSetEntries));
        galV2RegistryUniformLayoutCount.set(Math.max(0, uniformLayoutEntries));
        galV2RegistryUniformBindingCount.set(Math.max(0, uniformBindingEntries));
        galV2RegistryDrawTemplateCount.set(Math.max(0, drawTemplateEntries));
    }

    public static void recordOpenGlGalV2RequestedDraw(String family, String commandKind, int semanticSubdraws) {
        if (!shouldTraceLegacyGraphicsLowering()) {
            return;
        }
        String key = descriptorFamily(family) + "." + normalizeKey(commandKind, "unknown");
        openGlGalV2RequestedDraws.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        recordMeasuredFrameWorkload("openglRequestedDraws." + key, 1L);
        if (OPENGL_DRAW_DETAIL_ENABLED) {
            String detailKey = normalizeKey(family, "unknown") + "." + normalizeKey(commandKind, "unknown");
            openGlGalV2RequestedDrawDetails.computeIfAbsent(detailKey, ignored -> new LongAdder()).increment();
            recordMeasuredFrameWorkload("openglRequestedDrawDetails." + detailKey, 1L);
        }
        recordLegacyGraphicsLoweringAllocation(
            family,
            "opengl_requested_subdraws_" + normalizeKey(commandKind, "unknown"),
            Math.max(0, semanticSubdraws),
            0L
        );
    }

    public static void recordOpenGlGalV2EmittedDraw(String family, String commandKind, int glCommandCount) {
        if (!shouldTraceLegacyGraphicsLowering()) {
            return;
        }
        String key = descriptorFamily(family) + "." + normalizeKey(commandKind, "unknown");
        openGlGalV2EmittedDraws.computeIfAbsent(key, ignored -> new LongAdder()).add(Math.max(0, glCommandCount));
        recordMeasuredFrameWorkload("openglEmittedDraws." + key, Math.max(0, glCommandCount));
        if (OPENGL_DRAW_DETAIL_ENABLED) {
            String detailKey = normalizeKey(family, "unknown") + "." + normalizeKey(commandKind, "unknown");
            openGlGalV2EmittedDrawDetails.computeIfAbsent(detailKey, ignored -> new LongAdder()).add(Math.max(0, glCommandCount));
            recordMeasuredFrameWorkload("openglEmittedDrawDetails." + detailKey, Math.max(0, glCommandCount));
        }
    }

    public static void recordOpenGlGalV2StateOperation(String family, String operation, boolean emitted) {
        if (!shouldTraceLegacyGraphicsLowering()) {
            return;
        }
        if (!OPENGL_STATE_DETAIL_ENABLED && operation.indexOf('.') >= 0) {
            return;
        }
        String key = descriptorFamily(family) + "." + normalizeKey(operation, "unknown");
        openGlGalV2StateRequested.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        if (emitted) {
            openGlGalV2StateEmitted.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        }
    }

    public static void recordComputeDispatch() {
        if (descriptorEventsEnabled()) {
            computeDispatchCount.increment();
            recordMeasuredFrameWorkload("computeDispatches", 1L);
        }
    }

    public static void recordClear() {
        if (descriptorEventsEnabled()) {
            clearCount.increment();
            recordMeasuredFrameWorkload("clears", 1L);
        }
    }

    public static void recordTransfer() {
        if (descriptorEventsEnabled()) {
            transferCount.increment();
            recordMeasuredFrameWorkload("transfers", 1L);
        }
    }

    private static void recordMeasuredFrameWorkload(String key, long amount) {
        long measuredOrdinal = deterministicPerformanceMeasuredOrdinal.get();
        if (measuredOrdinal < 0L || amount <= 0L) {
            return;
        }
        String counterKey = "frame" + measuredOrdinal + "." + normalizeKey(key, "unknown");
        measuredFrameWorkloadCounters.computeIfAbsent(counterKey, ignored -> new LongAdder()).add(amount);
    }

    public static void flush() {
        writeSnapshotQuietly();
    }

    public static String observedWorkloadSignatureHash() {
        return sha256Hex(observedWorkloadSignatureCanonical());
    }

    public static void appendObservedWorkloadSignatureJson(StringBuilder builder, int indent) {
        String spaces = " ".repeat(Math.max(0, indent));
        builder.append(spaces).append("\"observedWorkloadSignature\": {\n");
        appendJsonNumberField(builder, indent + 2, "schemaVersion", 1).append(",\n");
        appendJsonNumberField(builder, indent + 2, "presentedFrames", presentedFrameCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "warmupFrames", deterministicWarmupFrameCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "measuredFrames", deterministicMeasuredFrameCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "renderPassBegins", renderPassBeginCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "graphicsDraws", graphicsDrawCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "galV2GraphicsDraws", galV2GraphicsDrawCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "galV2FallbackDraws", galV2LegacyFallbackDrawCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "computeDispatches", computeDispatchCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "clears", clearCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "transfers", transferCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "descriptorPlans", descriptorPlanCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "descriptorSetsAllocated", descriptorSetAllocationCount.sum()).append(",\n");
        appendJsonNumberField(builder, indent + 2, "descriptorSetsUpdated", descriptorSetUpdateCount.sum()).append(",\n");
        appendJsonMap(builder, indent + 2, "galV2FallbackReasons", galV2FallbackReasons).append(",\n");
        appendJsonMap(builder, indent + 2, "openglRequestedDraws", openGlGalV2RequestedDraws).append(",\n");
        appendJsonMap(builder, indent + 2, "openglEmittedDraws", openGlGalV2EmittedDraws).append(",\n");
        appendJsonMap(builder, indent + 2, "openglRequestedDrawDetails", openGlGalV2RequestedDrawDetails).append(",\n");
        appendJsonMap(builder, indent + 2, "openglEmittedDrawDetails", openGlGalV2EmittedDrawDetails).append(",\n");
        appendJsonMap(builder, indent + 2, "openglStateRequested", openGlGalV2StateRequested).append(",\n");
        appendJsonMap(builder, indent + 2, "openglStateEmitted", openGlGalV2StateEmitted).append(",\n");
        appendJsonLegacyGraphicsFamilies(builder, indent + 2).append(",\n");
        appendJsonMap(builder, indent + 2, "measuredFrameWorkload", measuredFrameWorkloadCounters);
        builder.append('\n').append(spaces).append('}');
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
        Runtime runtime = Runtime.getRuntime();
        long heapUsedBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        ProcessMemorySnapshot processMemory = processMemorySnapshot();

        StringBuilder builder = new StringBuilder(1024);
        builder.append("timestamp_utc=").append(Instant.now()).append('\n');
        builder.append("backend=").append(System.getProperty("mattmc.perfAudit.backend", "vulkan")).append('\n');
        builder.append("observed_workload_signature_hash=").append(observedWorkloadSignatureHash()).append('\n');
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
        builder.append("gal_v2_graphics_draw_count=").append(galV2GraphicsDrawCount.sum()).append('\n');
        builder.append("gal_v2_legacy_fallback_draw_count=").append(galV2LegacyFallbackDrawCount.sum()).append('\n');
        builder.append("gal_v2_resource_layout_lookup_count=").append(galV2ResourceLayoutLookupCount.sum()).append('\n');
        builder.append("gal_v2_resource_layout_create_count=").append(galV2ResourceLayoutCreateCount.sum()).append('\n');
        builder.append("gal_v2_resource_layout_reuse_count=")
            .append(Math.max(0L, galV2ResourceLayoutLookupCount.sum() - galV2ResourceLayoutCreateCount.sum()))
            .append('\n');
        builder.append("gal_v2_resource_layout_cache_size=").append(galV2ResourceLayoutCacheSize.get()).append('\n');
        builder.append("gal_v2_resource_set_lookup_count=").append(galV2ResourceSetLookupCount.sum()).append('\n');
        builder.append("gal_v2_resource_set_create_count=").append(galV2ResourceSetCreateCount.sum()).append('\n');
        builder.append("gal_v2_resource_set_reuse_count=")
            .append(Math.max(0L, galV2ResourceSetLookupCount.sum() - galV2ResourceSetCreateCount.sum()))
            .append('\n');
        builder.append("gal_v2_resource_set_cache_size=").append(galV2ResourceSetCacheSize.get()).append('\n');
        builder.append("gal_v2_draw_template_lookup_count=").append(galV2DrawTemplateLookupCount.sum()).append('\n');
        builder.append("gal_v2_draw_template_create_count=").append(galV2DrawTemplateCreateCount.sum()).append('\n');
        builder.append("gal_v2_draw_template_reuse_count=")
            .append(Math.max(0L, galV2DrawTemplateLookupCount.sum() - galV2DrawTemplateCreateCount.sum()))
            .append('\n');
        builder.append("gal_v2_draw_template_cache_size=").append(galV2DrawTemplateCacheSize.get()).append('\n');
        builder.append("gal_v2_command_stream_count=").append(galV2CommandStreamCount.sum()).append('\n');
        builder.append("gal_v2_command_stream_command_count=").append(galV2CommandStreamCommandCount.sum()).append('\n');
        builder.append("gal_v2_command_stream_bind_count=").append(galV2CommandStreamBindCount.sum()).append('\n');
        builder.append("gal_v2_command_stream_draw_count=").append(galV2CommandStreamDrawCount.sum()).append('\n');
        builder.append("gal_v2_command_stream_suppressed_bind_count=").append(galV2CommandStreamSuppressedBindCount.sum()).append('\n');
        builder.append("gal_v2_pass_command_buffer_count=").append(galV2PassCommandBufferCount.sum()).append('\n');
        builder.append("gal_v2_pass_command_buffer_draw_count=").append(galV2PassCommandBufferDrawCount.sum()).append('\n');
        builder.append("gal_v2_pass_command_buffer_command_count=").append(galV2PassCommandBufferCommandCount.sum()).append('\n');
        builder.append("gal_v2_pass_command_buffer_eliminated_executor_calls=")
            .append(galV2PassCommandBufferEliminatedExecutorCalls.sum())
            .append('\n');
        builder.append("gal_v2_registry_prune_count=").append(galV2RegistryPruneCount.sum()).append('\n');
        builder.append("gal_v2_registry_pruned_entry_count=").append(galV2RegistryPrunedEntryCount.get()).append('\n');
        builder.append("gal_v2_registry_entry_count=").append(galV2RegistryEntryCount.get()).append('\n');
        builder.append("gal_v2_registry_high_water_entry_count=").append(galV2RegistryHighWaterEntryCount.get()).append('\n');
        builder.append("gal_v2_registry_handle_count=").append(galV2RegistryHandleCount.get()).append('\n');
        builder.append("gal_v2_registry_graphics_object_count=").append(galV2RegistryGraphicsObjectCount.get()).append('\n');
        builder.append("gal_v2_registry_resource_layout_count=").append(galV2RegistryResourceLayoutCount.get()).append('\n');
        builder.append("gal_v2_registry_resource_set_count=").append(galV2RegistryResourceSetCount.get()).append('\n');
        builder.append("gal_v2_registry_uniform_layout_count=").append(galV2RegistryUniformLayoutCount.get()).append('\n');
        builder.append("gal_v2_registry_uniform_binding_count=").append(galV2RegistryUniformBindingCount.get()).append('\n');
        builder.append("gal_v2_registry_draw_template_count=").append(galV2RegistryDrawTemplateCount.get()).append('\n');
        appendLongAdderMap(builder, "opengl_gal_v2_requested_draw", openGlGalV2RequestedDraws);
        appendLongAdderMap(builder, "opengl_gal_v2_emitted_draw", openGlGalV2EmittedDraws);
        appendLongAdderMap(builder, "opengl_gal_v2_requested_draw_detail", openGlGalV2RequestedDrawDetails);
        appendLongAdderMap(builder, "opengl_gal_v2_emitted_draw_detail", openGlGalV2EmittedDrawDetails);
        appendLongAdderMap(builder, "opengl_gal_v2_state_requested", openGlGalV2StateRequested);
        appendLongAdderMap(builder, "opengl_gal_v2_state_emitted", openGlGalV2StateEmitted);
        appendLongAdderMap(builder, "gal_v2_fallback_reason", galV2FallbackReasons);
        builder.append("compute_dispatch_count=").append(computeDispatchCount.sum()).append('\n');
        builder.append("clear_count=").append(clearCount.sum()).append('\n');
        builder.append("transfer_count=").append(transferCount.sum()).append('\n');
        builder.append("descriptor_bind_count=").append(descriptorBindCount.sum()).append('\n');
        builder.append("descriptor_bind_total_ms=").append(format(descriptorBindMs)).append('\n');
        appendDescriptorSummary(builder, presentedFrames);
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
        builder.append("java_heap_used_bytes=").append(heapUsedBytes).append('\n');
        builder.append("java_heap_committed_bytes=").append(runtime.totalMemory()).append('\n');
        builder.append("java_heap_max_bytes=").append(runtime.maxMemory()).append('\n');
        builder.append("process_rss_kb=").append(processMemory.rssKb()).append('\n');
        builder.append("process_rss_anon_kb=").append(processMemory.rssAnonKb()).append('\n');
        builder.append("process_rss_file_kb=").append(processMemory.rssFileKb()).append('\n');
        builder.append("process_rss_shmem_kb=").append(processMemory.rssShmemKb()).append('\n');
        builder.append("perf_memory_sample_count=").append(memorySampleCount.sum()).append('\n');
        builder.append("perf_memory_latest_heap_used_bytes=").append(memoryLatestHeapUsedBytes.get()).append('\n');
        builder.append("perf_memory_latest_heap_committed_bytes=").append(memoryLatestHeapCommittedBytes.get()).append('\n');
        builder.append("perf_memory_latest_heap_max_bytes=").append(memoryLatestHeapMaxBytes.get()).append('\n');
        builder.append("perf_memory_latest_process_rss_kb=").append(memoryLatestProcessRssKb.get()).append('\n');
        builder.append("perf_memory_latest_rss_anon_kb=").append(memoryLatestRssAnonKb.get()).append('\n');
        builder.append("perf_memory_latest_rss_file_kb=").append(memoryLatestRssFileKb.get()).append('\n');
        builder.append("perf_memory_latest_rss_shmem_kb=").append(memoryLatestRssShmemKb.get()).append('\n');
        builder.append("perf_memory_peak_heap_committed_bytes=").append(memoryPeakHeapCommittedBytes.get()).append('\n');
        builder.append("perf_memory_peak_process_rss_kb=").append(memoryPeakProcessRssKb.get()).append('\n');
        builder.append("perf_memory_peak_rss_anon_kb=").append(memoryPeakRssAnonKb.get()).append('\n');
        builder.append("perf_memory_peak_rss_shmem_kb=").append(memoryPeakRssShmemKb.get()).append('\n');
        appendSubmitSummary(builder, presentedFrames);
        appendLegacyGraphicsLoweringSummary(builder, presentedFrames);
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

    private static void appendLongAdderMap(StringBuilder builder, String prefix, Map<String, LongAdder> values) {
        if (values.isEmpty()) {
            return;
        }
        ArrayList<Map.Entry<String, LongAdder>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, LongAdder> entry : entries) {
            builder.append(prefix)
                .append('.')
                .append(normalizeKey(entry.getKey(), "unknown"))
                .append('=')
                .append(entry.getValue().sum())
                .append('\n');
        }
    }

    private static String observedWorkloadSignatureCanonical() {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("schemaVersion=1\n");
        builder.append("presentedFrames=").append(presentedFrameCount.sum()).append('\n');
        builder.append("warmupFrames=").append(deterministicWarmupFrameCount.sum()).append('\n');
        builder.append("measuredFrames=").append(deterministicMeasuredFrameCount.sum()).append('\n');
        builder.append("renderPassBegins=").append(renderPassBeginCount.sum()).append('\n');
        builder.append("graphicsDraws=").append(graphicsDrawCount.sum()).append('\n');
        builder.append("galV2GraphicsDraws=").append(galV2GraphicsDrawCount.sum()).append('\n');
        builder.append("galV2FallbackDraws=").append(galV2LegacyFallbackDrawCount.sum()).append('\n');
        builder.append("computeDispatches=").append(computeDispatchCount.sum()).append('\n');
        builder.append("clears=").append(clearCount.sum()).append('\n');
        builder.append("transfers=").append(transferCount.sum()).append('\n');
        builder.append("descriptorPlans=").append(descriptorPlanCount.sum()).append('\n');
        builder.append("descriptorSetsAllocated=").append(descriptorSetAllocationCount.sum()).append('\n');
        builder.append("descriptorSetsUpdated=").append(descriptorSetUpdateCount.sum()).append('\n');
        appendCanonicalMap(builder, "galV2FallbackReasons", galV2FallbackReasons);
        appendCanonicalMap(builder, "openglRequestedDraws", openGlGalV2RequestedDraws);
        appendCanonicalMap(builder, "openglEmittedDraws", openGlGalV2EmittedDraws);
        appendCanonicalMap(builder, "openglRequestedDrawDetails", openGlGalV2RequestedDrawDetails);
        appendCanonicalMap(builder, "openglEmittedDrawDetails", openGlGalV2EmittedDrawDetails);
        appendCanonicalMap(builder, "openglStateRequested", openGlGalV2StateRequested);
        appendCanonicalMap(builder, "openglStateEmitted", openGlGalV2StateEmitted);
        appendCanonicalLegacyGraphicsFamilies(builder);
        appendCanonicalMap(builder, "measuredFrameWorkload", measuredFrameWorkloadCounters);
        return builder.toString();
    }

    private static void appendCanonicalMap(StringBuilder builder, String prefix, Map<String, LongAdder> values) {
        ArrayList<Map.Entry<String, LongAdder>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, LongAdder> entry : entries) {
            builder.append(prefix)
                .append('.')
                .append(normalizeKey(entry.getKey(), "unknown"))
                .append('=')
                .append(entry.getValue().sum())
                .append('\n');
        }
    }

    private static void appendCanonicalLegacyGraphicsFamilies(StringBuilder builder) {
        ArrayList<Map.Entry<String, LegacyGraphicsLoweringCounters>> families =
            new ArrayList<>(legacyGraphicsLoweringFamilies.entrySet());
        families.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, LegacyGraphicsLoweringCounters> family : families) {
            String familyKey = normalizeKey(family.getKey(), "unknown");
            builder.append("legacyGraphicsFamily.").append(familyKey)
                .append(".stepCount=").append(family.getValue().stepCount()).append('\n');
            appendCanonicalPhaseCounts(builder, "legacyGraphicsFamily." + familyKey + ".step", family.getValue().steps);
            appendCanonicalCacheCounts(builder, "legacyGraphicsFamily." + familyKey + ".cache", family.getValue().caches);
            appendCanonicalAllocationCounts(builder, "legacyGraphicsFamily." + familyKey + ".allocation", family.getValue().allocations);
        }
    }

    private static void appendCanonicalPhaseCounts(StringBuilder builder, String prefix, Map<String, PhaseCounters> values) {
        ArrayList<Map.Entry<String, PhaseCounters>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, PhaseCounters> entry : entries) {
            builder.append(prefix).append('.').append(normalizeKey(entry.getKey(), "unknown"))
                .append(".count=").append(entry.getValue().count.sum()).append('\n');
        }
    }

    private static void appendCanonicalCacheCounts(StringBuilder builder, String prefix, Map<String, LoweringCacheCounters> values) {
        ArrayList<Map.Entry<String, LoweringCacheCounters>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, LoweringCacheCounters> entry : entries) {
            LoweringCacheCounters counters = entry.getValue();
            String key = prefix + "." + normalizeKey(entry.getKey(), "unknown");
            builder.append(key).append(".lookups=").append(counters.lookupCount.sum()).append('\n');
            builder.append(key).append(".hits=").append(counters.hitCount.sum()).append('\n');
            builder.append(key).append(".misses=").append(counters.missCount.sum()).append('\n');
        }
    }

    private static void appendCanonicalAllocationCounts(
        StringBuilder builder,
        String prefix,
        Map<String, LoweringAllocationCounters> values
    ) {
        ArrayList<Map.Entry<String, LoweringAllocationCounters>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, LoweringAllocationCounters> entry : entries) {
            LoweringAllocationCounters counters = entry.getValue();
            String key = prefix + "." + normalizeKey(entry.getKey(), "unknown");
            builder.append(key).append(".objects=").append(counters.objectCount.sum()).append('\n');
            builder.append(key).append(".bytes=").append(counters.estimatedBytes.sum()).append('\n');
        }
    }

    private static StringBuilder appendJsonNumberField(StringBuilder builder, int indent, String key, long value) {
        builder.append(" ".repeat(Math.max(0, indent))).append('"').append(key).append("\": ").append(value);
        return builder;
    }

    private static StringBuilder appendJsonMap(StringBuilder builder, int indent, String key, Map<String, LongAdder> values) {
        builder.append(" ".repeat(Math.max(0, indent))).append('"').append(key).append("\": {");
        ArrayList<Map.Entry<String, LongAdder>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        if (!entries.isEmpty()) {
            builder.append('\n');
            for (int index = 0; index < entries.size(); index++) {
                Map.Entry<String, LongAdder> entry = entries.get(index);
                builder.append(" ".repeat(Math.max(0, indent + 2)))
                    .append('"').append(jsonEscape(normalizeKey(entry.getKey(), "unknown"))).append("\": ")
                    .append(entry.getValue().sum())
                    .append(index + 1 == entries.size() ? "\n" : ",\n");
            }
            builder.append(" ".repeat(Math.max(0, indent)));
        }
        builder.append('}');
        return builder;
    }

    private static StringBuilder appendJsonLegacyGraphicsFamilies(StringBuilder builder, int indent) {
        builder.append(" ".repeat(Math.max(0, indent))).append("\"legacyGraphicsFamilies\": {");
        ArrayList<Map.Entry<String, LegacyGraphicsLoweringCounters>> families =
            new ArrayList<>(legacyGraphicsLoweringFamilies.entrySet());
        families.sort(Map.Entry.comparingByKey());
        if (!families.isEmpty()) {
            builder.append('\n');
            for (int index = 0; index < families.size(); index++) {
                Map.Entry<String, LegacyGraphicsLoweringCounters> family = families.get(index);
                String key = normalizeKey(family.getKey(), "unknown");
                builder.append(" ".repeat(Math.max(0, indent + 2)))
                    .append('"').append(jsonEscape(key)).append("\": { ");
                builder.append("\"stepCount\": ").append(family.getValue().stepCount());
                builder.append(", \"cacheLookups\": ").append(cacheLookupCount(family.getValue().caches));
                builder.append(", \"allocationObjects\": ").append(allocationObjectCount(family.getValue().allocations));
                builder.append(" }").append(index + 1 == families.size() ? "\n" : ",\n");
            }
            builder.append(" ".repeat(Math.max(0, indent)));
        }
        builder.append('}');
        return builder;
    }

    private static long cacheLookupCount(Map<String, LoweringCacheCounters> values) {
        long total = 0L;
        for (LoweringCacheCounters counters : values.values()) {
            total += counters.lookupCount.sum();
        }
        return total;
    }

    private static long allocationObjectCount(Map<String, LoweringAllocationCounters> values) {
        long total = 0L;
        for (LoweringAllocationCounters counters : values.values()) {
            total += counters.objectCount.sum();
        }
        return total;
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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

    private static void recordMemorySample() {
        if (!MEMORY_SAMPLES_ENABLED) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        long heapUsedBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long heapCommittedBytes = Math.max(0L, runtime.totalMemory());
        long heapMaxBytes = Math.max(0L, runtime.maxMemory());
        ProcessMemorySnapshot process = processMemorySnapshot();
        memorySampleCount.increment();
        memoryLatestHeapUsedBytes.set(heapUsedBytes);
        memoryLatestHeapCommittedBytes.set(heapCommittedBytes);
        memoryLatestHeapMaxBytes.set(heapMaxBytes);
        memoryLatestProcessRssKb.set(process.rssKb());
        memoryLatestRssAnonKb.set(process.rssAnonKb());
        memoryLatestRssFileKb.set(process.rssFileKb());
        memoryLatestRssShmemKb.set(process.rssShmemKb());
        updateMax(memoryPeakHeapCommittedBytes, heapCommittedBytes);
        updateMax(memoryPeakProcessRssKb, process.rssKb());
        updateMax(memoryPeakRssAnonKb, process.rssAnonKb());
        updateMax(memoryPeakRssShmemKb, process.rssShmemKb());
    }

    private static ProcessMemorySnapshot processMemorySnapshot() {
        Path status = Path.of("/proc/self/status");
        if (!Files.isRegularFile(status)) {
            return ProcessMemorySnapshot.UNAVAILABLE;
        }
        long rssKb = -1L;
        long rssAnonKb = -1L;
        long rssFileKb = -1L;
        long rssShmemKb = -1L;
        try {
            for (String line : Files.readAllLines(status, StandardCharsets.UTF_8)) {
                if (line.startsWith("VmRSS:")) {
                    rssKb = parseStatusKilobytes(line);
                } else if (line.startsWith("RssAnon:")) {
                    rssAnonKb = parseStatusKilobytes(line);
                } else if (line.startsWith("RssFile:")) {
                    rssFileKb = parseStatusKilobytes(line);
                } else if (line.startsWith("RssShmem:")) {
                    rssShmemKb = parseStatusKilobytes(line);
                }
            }
        } catch (IOException ignored) {
            return ProcessMemorySnapshot.UNAVAILABLE;
        }
        return new ProcessMemorySnapshot(rssKb, rssAnonKb, rssFileKb, rssShmemKb);
    }

    private static long parseStatusKilobytes(String line) {
        String[] pieces = line.trim().split("\\s+");
        if (pieces.length < 2) {
            return -1L;
        }
        try {
            return Math.max(-1L, Long.parseLong(pieces[1]));
        } catch (NumberFormatException ignored) {
            return -1L;
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

    private static void appendDescriptorSummary(StringBuilder builder, double presentedFrames) {
        long planCount = descriptorPlanCount.sum();
        long lookupCount = descriptorCacheLookupCount.sum();
        long hitCount = descriptorCacheHitCount.sum();
        long commandBindCount = descriptorCommandBindCount.sum();
        long drawDispatchCount = graphicsDrawCount.sum() + computeDispatchCount.sum();
        double measuredFrames = Math.max(1.0, deterministicMeasuredFrameCount.sum());
        double uniquePlans = Math.max(1.0, descriptorPlanCacheableCount.sum() + descriptorPlanNonCacheableCount.sum());
        builder.append("descriptor_plan_count=").append(planCount).append('\n');
        builder.append("descriptor_plan_total_ms=").append(format(nanosToMillis(descriptorPlanTotalNanos.sum()))).append('\n');
        builder.append("descriptor_plan_cacheable_count=").append(descriptorPlanCacheableCount.sum()).append('\n');
        builder.append("descriptor_plan_noncacheable_count=").append(descriptorPlanNonCacheableCount.sum()).append('\n');
        builder.append("descriptor_plan_bindings=").append(descriptorPlanBindingCount.sum()).append('\n');
        builder.append("descriptor_plan_reuse_lookup_count=").append(descriptorPlanReuseLookupCount.sum()).append('\n');
        builder.append("descriptor_plan_reuse_hit_count=").append(descriptorPlanReuseHitCount.sum()).append('\n');
        builder.append("descriptor_plan_reuse_miss_count=").append(descriptorPlanReuseMissCount.sum()).append('\n');
        builder.append("descriptor_plan_reuse_hit_rate_pct=")
            .append(format(percent(descriptorPlanReuseHitCount.sum(), Math.max(1.0, descriptorPlanReuseLookupCount.sum()))))
            .append('\n');
        builder.append("descriptor_plan_reuse_equivalent_consecutive_count=")
            .append(descriptorPlanReuseEquivalentConsecutiveCount.sum()).append('\n');
        builder.append("descriptor_plan_reuse_cache_size=").append(descriptorPlanReuseCacheSize.get()).append('\n');
        builder.append("descriptor_cache_lookup_count=").append(lookupCount).append('\n');
        builder.append("descriptor_cache_hit_count=").append(hitCount).append('\n');
        builder.append("descriptor_cache_miss_count=").append(descriptorCacheMissCount.sum()).append('\n');
        builder.append("descriptor_cache_hit_rate_pct=").append(format(percent(hitCount, Math.max(1.0, lookupCount)))).append('\n');
        builder.append("descriptor_cache_invalidation_count=").append(descriptorCacheInvalidationCount.sum()).append('\n');
        builder.append("descriptor_cache_invalidated_entries=").append(descriptorCacheInvalidatedEntryCount.sum()).append('\n');
        builder.append("descriptor_set_allocation_count=").append(descriptorSetAllocationCount.sum()).append('\n');
        builder.append("descriptor_set_allocation_ms=").append(format(nanosToMillis(descriptorSetAllocationNanos.sum()))).append('\n');
        builder.append("descriptor_set_update_count=").append(descriptorSetUpdateCount.sum()).append('\n');
        builder.append("descriptor_set_update_ms=").append(format(nanosToMillis(descriptorSetUpdateNanos.sum()))).append('\n');
        builder.append("descriptor_write_count=").append(descriptorWriteCount.sum()).append('\n');
        builder.append("descriptor_write_sampler_count=").append(descriptorWriteSamplerCount.sum()).append('\n');
        builder.append("descriptor_write_uniform_buffer_count=").append(descriptorWriteUniformBufferCount.sum()).append('\n');
        builder.append("descriptor_write_storage_image_count=").append(descriptorWriteStorageImageCount.sum()).append('\n');
        builder.append("descriptor_write_texel_buffer_count=").append(descriptorWriteTexelBufferCount.sum()).append('\n');
        builder.append("descriptor_command_bind_count=").append(commandBindCount).append('\n');
        builder.append("descriptor_command_bind_ms=").append(format(nanosToMillis(descriptorCommandBindNanos.sum()))).append('\n');
        builder.append("descriptor_transient_uniform_copy_count=").append(descriptorTransientUniformCopyCount.sum()).append('\n');
        builder.append("descriptor_transient_uniform_copy_bytes=").append(descriptorTransientUniformCopyBytes.sum()).append('\n');
        builder.append("descriptor_transient_uniform_copy_ms=").append(format(nanosToMillis(descriptorTransientUniformCopyNanos.sum()))).append('\n');
        builder.append("dynamic_transforms_binding_count=").append(dynamicTransformsBindingCount.sum()).append('\n');
        builder.append("dynamic_transforms_handle_change_count=").append(dynamicTransformsHandleChangeCount.sum()).append('\n');
        builder.append("dynamic_transforms_offset_change_count=").append(dynamicTransformsOffsetChangeCount.sum()).append('\n');
        builder.append("dynamic_transforms_range_change_count=").append(dynamicTransformsRangeChangeCount.sum()).append('\n');
        builder.append("dynamic_transforms_content_change_count=").append(dynamicTransformsContentChangeCount.sum()).append('\n');
        builder.append("dynamic_transforms_content_reuse_count=").append(dynamicTransformsContentReuseCount.sum()).append('\n');
        builder.append("dynamic_transforms_distinct_content_count=").append(dynamicTransformsDistinctContentCount.get()).append('\n');
        builder.append("dynamic_transforms_arena_buffer_allocation_count=").append(dynamicTransformsArenaBufferAllocationCount.sum()).append('\n');
        builder.append("dynamic_transforms_arena_growth_count=").append(dynamicTransformsArenaGrowthCount.sum()).append('\n');
        builder.append("dynamic_transforms_arena_upload_count=").append(dynamicTransformsArenaUploadCount.sum()).append('\n');
        builder.append("dynamic_transforms_arena_upload_bytes=").append(dynamicTransformsArenaUploadBytes.sum()).append('\n');
        builder.append("dynamic_transforms_arena_reserved_bytes=").append(dynamicTransformsArenaReservedBytes.sum()).append('\n');
        builder.append("dynamic_transforms_arena_reuse_hit_count=").append(dynamicTransformsArenaReuseHitCount.sum()).append('\n');
        builder.append("dynamic_transforms_arena_high_water_bytes=").append(dynamicTransformsArenaHighWaterBytes.get()).append('\n');
        builder.append("dynamic_transforms_arena_capacity_bytes=").append(dynamicTransformsArenaCapacityBytes.get()).append('\n');
        builder.append("standalone_uniform_binding_count=").append(standaloneUniformBindingCount.sum()).append('\n');
        builder.append("standalone_uniform_handle_change_count=").append(standaloneUniformHandleChangeCount.sum()).append('\n');
        builder.append("standalone_uniform_offset_change_count=").append(standaloneUniformOffsetChangeCount.sum()).append('\n');
        builder.append("standalone_uniform_range_change_count=").append(standaloneUniformRangeChangeCount.sum()).append('\n');
        builder.append("standalone_uniform_content_change_count=").append(standaloneUniformContentChangeCount.sum()).append('\n');
        builder.append("standalone_uniform_content_reuse_count=").append(standaloneUniformContentReuseCount.sum()).append('\n');
        builder.append("standalone_uniform_distinct_content_count=").append(standaloneUniformDistinctContentCount.get()).append('\n');
        builder.append("standalone_uniform_arena_buffer_allocation_count=").append(standaloneUniformArenaBufferAllocationCount.sum()).append('\n');
        builder.append("standalone_uniform_arena_growth_count=").append(standaloneUniformArenaGrowthCount.sum()).append('\n');
        builder.append("standalone_uniform_arena_upload_count=").append(standaloneUniformArenaUploadCount.sum()).append('\n');
        builder.append("standalone_uniform_arena_upload_bytes=").append(standaloneUniformArenaUploadBytes.sum()).append('\n');
        builder.append("standalone_uniform_arena_reserved_bytes=").append(standaloneUniformArenaReservedBytes.sum()).append('\n');
        builder.append("standalone_uniform_arena_reuse_hit_count=").append(standaloneUniformArenaReuseHitCount.sum()).append('\n');
        builder.append("standalone_uniform_source_reuse_count=").append(standaloneUniformSourceReuseCount.sum()).append('\n');
        builder.append("standalone_uniform_arena_high_water_bytes=").append(standaloneUniformArenaHighWaterBytes.get()).append('\n');
        builder.append("standalone_uniform_arena_capacity_bytes=").append(standaloneUniformArenaCapacityBytes.get()).append('\n');
        builder.append("descriptor_plans_per_measured_frame=").append(format(planCount / measuredFrames)).append('\n');
        builder.append("descriptor_sets_allocated_per_measured_frame=").append(format(descriptorSetAllocationCount.sum() / measuredFrames)).append('\n');
        builder.append("descriptor_sets_updated_per_measured_frame=").append(format(descriptorSetUpdateCount.sum() / measuredFrames)).append('\n');
        builder.append("descriptor_commands_bound_per_measured_frame=").append(format(commandBindCount / measuredFrames)).append('\n');
        builder.append("descriptor_plans_per_presented_frame=").append(format(planCount / presentedFrames)).append('\n');
        builder.append("descriptor_sets_allocated_per_presented_frame=").append(format(descriptorSetAllocationCount.sum() / presentedFrames)).append('\n');
        builder.append("descriptor_sets_updated_per_presented_frame=").append(format(descriptorSetUpdateCount.sum() / presentedFrames)).append('\n');
        builder.append("descriptor_commands_bound_per_presented_frame=").append(format(commandBindCount / presentedFrames)).append('\n');
        builder.append("descriptor_plans_per_draw_dispatch=").append(format(planCount / Math.max(1.0, drawDispatchCount))).append('\n');
        builder.append("descriptor_commands_bound_per_draw_dispatch=").append(format(commandBindCount / Math.max(1.0, drawDispatchCount))).append('\n');
        builder.append("descriptor_lookups_per_unique_plan=").append(format(lookupCount / uniquePlans)).append('\n');
        List<Map.Entry<String, LongAdder>> missReasons = new ArrayList<>(descriptorCacheMissReasons.entrySet());
        missReasons.sort((left, right) -> Long.compare(right.getValue().sum(), left.getValue().sum()));
        int missReasonRank = 1;
        for (Map.Entry<String, LongAdder> entry : missReasons) {
            if (missReasonRank > 12) {
                break;
            }
            builder.append("descriptor_cache_miss_reason_rank_").append(missReasonRank)
                .append('=').append(entry.getKey())
                .append("|count=").append(entry.getValue().sum())
                .append('\n');
            missReasonRank++;
        }

        List<Map.Entry<String, DescriptorCounters>> entries = new ArrayList<>(descriptorPipelines.entrySet());
        entries.sort((left, right) -> Long.compare(right.getValue().totalWeight(), left.getValue().totalWeight()));
        int rank = 1;
        for (Map.Entry<String, DescriptorCounters> entry : entries) {
            if (rank > 20) {
                break;
            }
            DescriptorCounters counters = entry.getValue();
            String prefix = "descriptor_pipeline_rank_" + rank;
            builder.append(prefix).append('=').append(entry.getKey())
                .append("|plans=").append(counters.planCount.sum())
                .append("|planReuseLookups=").append(counters.planReuseLookupCount.sum())
                .append("|planReuseHits=").append(counters.planReuseHitCount.sum())
                .append("|equivConsecutive=").append(counters.equivalentConsecutivePlanCount.sum())
                .append("|noncacheable=").append(counters.nonCacheablePlanCount.sum())
                .append("|lookups=").append(counters.cacheLookupCount.sum())
                .append("|hits=").append(counters.cacheHitCount.sum())
                .append("|misses=").append(counters.cacheMissCount.sum())
                .append("|missReason=").append(counters.dominantMissReason())
                .append("|allocs=").append(counters.allocationCount.sum())
                .append("|updates=").append(counters.updateCount.sum())
                .append("|writes=").append(counters.writeCount.sum())
                .append("|cmdBinds=").append(counters.commandBindCount.sum())
                .append("|transientCopies=").append(counters.transientUniformCopyCount.sum())
                .append("|transientBytes=").append(counters.transientUniformCopyBytes.sum())
                .append("|planMs=").append(format(nanosToMillis(counters.planNanos.sum())))
                .append("|allocMs=").append(format(nanosToMillis(counters.allocationNanos.sum())))
                .append("|updateMs=").append(format(nanosToMillis(counters.updateNanos.sum())))
                .append("|cmdBindMs=").append(format(nanosToMillis(counters.commandBindNanos.sum())))
                .append("|transientCopyMs=").append(format(nanosToMillis(counters.transientUniformCopyNanos.sum())))
                .append('\n');
            rank++;
        }

        List<Map.Entry<String, StandaloneUniformCounters>> standaloneEntries =
            new ArrayList<>(standaloneUniformPipelines.entrySet());
        standaloneEntries.sort((left, right) -> Long.compare(right.getValue().totalWeight(), left.getValue().totalWeight()));
        int standaloneRank = 1;
        for (Map.Entry<String, StandaloneUniformCounters> entry : standaloneEntries) {
            if (standaloneRank > 12) {
                break;
            }
            StandaloneUniformCounters counters = entry.getValue();
            builder.append("standalone_uniform_pipeline_rank_").append(standaloneRank)
                .append('=').append(entry.getKey())
                .append("|bindings=").append(counters.bindingCount.sum())
                .append("|handleChanges=").append(counters.handleChangeCount.sum())
                .append("|offsetChanges=").append(counters.offsetChangeCount.sum())
                .append("|rangeChanges=").append(counters.rangeChangeCount.sum())
                .append("|contentChanges=").append(counters.contentChangeCount.sum())
                .append("|contentReuse=").append(counters.contentReuseCount.sum())
                .append("|uploads=").append(counters.uploadCount.sum())
                .append("|uploadBytes=").append(counters.uploadBytes.sum())
                .append("|reuseHits=").append(counters.reuseHitCount.sum())
                .append("|sourceReuse=").append(counters.sourceReuseCount.sum())
                .append("|reservedBytes=").append(counters.reservedBytes.sum())
                .append('\n');
            standaloneRank++;
        }
    }

    private static DescriptorCounters descriptorCounters(String pipelineLocation) {
        return descriptorPipelines.computeIfAbsent(descriptorFamily(pipelineLocation), ignored -> new DescriptorCounters());
    }

    private static LegacyGraphicsLoweringCounters legacyGraphicsLoweringCounters(String pipelineLocation) {
        return legacyGraphicsLoweringFamilies.computeIfAbsent(
            descriptorFamily(pipelineLocation),
            ignored -> new LegacyGraphicsLoweringCounters()
        );
    }

    private static String descriptorFamily(String pipelineLocation) {
        String key = normalizeKey(pipelineLocation, "unknown");
        if (key.startsWith("gal-v2_legacy-program_")) {
            int start = "gal-v2_legacy-program_".length();
            int end = start;
            while (end < key.length() && Character.isDigit(key.charAt(end))) {
                end++;
            }
            if (end > start) {
                key = "vulkanic_legacy_program_" + key.substring(start, end);
            } else {
                key = "gal_v2_legacy_program";
            }
        }
        if (key.startsWith("legacy_") || key.contains("compatibility-state")) {
            String semanticFamily = VulkanicDiagnostics.currentSemanticWorkloadFamily();
            String normalizedSemanticFamily = normalizeKey(semanticFamily, "");
            if (!normalizedSemanticFamily.isEmpty() && !"unavailable".equals(normalizedSemanticFamily)) {
                key = key + "." + normalizedSemanticFamily;
            }
        }
        if (key.contains("sodium") || key.contains("chunk") || key.contains("terrain")) {
            return "terrain";
        }
        if (key.contains("distanthorizons") || key.contains("distant_horizons") || key.contains("dh")) {
            return "distant_horizons";
        }
        if (key.contains("iris") && (key.contains("composite") || key.contains("deferred") || key.contains("shadow"))) {
            return key;
        }
        if (key.contains("gui") || key.contains("text")) {
            return "gui_text";
        }
        if (key.contains("entity") || key.contains("armor") || key.contains("player") || key.contains("item")) {
            return "entities_items";
        }
        if (key.contains("hand")) {
            return "hand";
        }
        if (key.contains("compute")) {
            return "compute";
        }
        return key;
    }

    private static void appendLegacyGraphicsLoweringSummary(StringBuilder builder, double presentedFrames) {
        List<Map.Entry<String, LegacyGraphicsLoweringCounters>> entries =
            new ArrayList<>(legacyGraphicsLoweringFamilies.entrySet());
        entries.sort((left, right) -> Long.compare(right.getValue().totalWeight(), left.getValue().totalWeight()));
        int rank = 1;
        for (Map.Entry<String, LegacyGraphicsLoweringCounters> entry : entries) {
            if (rank > 20) {
                break;
            }
            LegacyGraphicsLoweringCounters counters = entry.getValue();
            String prefix = "legacy_graphics_lowering_rank_" + rank;
            builder.append(prefix).append('=').append(entry.getKey())
                .append("|steps=").append(counters.stepCount())
                .append("|totalMs=").append(format(nanosToMillis(counters.stepNanos())))
                .append("|perPresentedFrameMs=").append(format(nanosToMillis(counters.stepNanos()) / presentedFrames))
                .append('\n');

            List<Map.Entry<String, PhaseCounters>> steps = new ArrayList<>(counters.steps.entrySet());
            steps.sort((left, right) -> Long.compare(right.getValue().totalNanos.sum(), left.getValue().totalNanos.sum()));
            int stepRank = 1;
            for (Map.Entry<String, PhaseCounters> step : steps) {
                if (stepRank > 24) {
                    break;
                }
                PhaseCounters stepCounters = step.getValue();
                builder.append(prefix).append(".step_rank_").append(stepRank)
                    .append('=').append(step.getKey())
                    .append("|count=").append(stepCounters.count.sum())
                    .append("|ms=").append(format(nanosToMillis(stepCounters.totalNanos.sum())))
                    .append('\n');
                stepRank++;
            }

            List<Map.Entry<String, LoweringCacheCounters>> caches = new ArrayList<>(counters.caches.entrySet());
            caches.sort((left, right) -> Long.compare(right.getValue().lookupCount.sum(), left.getValue().lookupCount.sum()));
            int cacheRank = 1;
            for (Map.Entry<String, LoweringCacheCounters> cache : caches) {
                if (cacheRank > 8) {
                    break;
                }
                LoweringCacheCounters cacheCounters = cache.getValue();
                long lookups = cacheCounters.lookupCount.sum();
                builder.append(prefix).append(".cache_rank_").append(cacheRank)
                    .append('=').append(cache.getKey())
                    .append("|lookups=").append(lookups)
                    .append("|hits=").append(cacheCounters.hitCount.sum())
                    .append("|misses=").append(cacheCounters.missCount.sum())
                    .append("|hitRatePct=").append(format(percent(cacheCounters.hitCount.sum(), Math.max(1.0, lookups))))
                    .append("|size=").append(cacheCounters.cacheSize.get())
                    .append("|highWater=").append(cacheCounters.highWater.get())
                    .append('\n');
                cacheRank++;
            }

            List<Map.Entry<String, LoweringAllocationCounters>> allocations =
                new ArrayList<>(counters.allocations.entrySet());
            allocations.sort((left, right) -> Long.compare(right.getValue().estimatedBytes.sum(), left.getValue().estimatedBytes.sum()));
            int allocationRank = 1;
            for (Map.Entry<String, LoweringAllocationCounters> allocation : allocations) {
                if (allocationRank > 12) {
                    break;
                }
                LoweringAllocationCounters allocationCounters = allocation.getValue();
                builder.append(prefix).append(".allocation_rank_").append(allocationRank)
                    .append('=').append(allocation.getKey())
                    .append("|events=").append(allocationCounters.eventCount.sum())
                    .append("|objects=").append(allocationCounters.objectCount.sum())
                    .append("|estimatedBytes=").append(allocationCounters.estimatedBytes.sum())
                    .append('\n');
                allocationRank++;
            }
            rank++;
        }

        List<Map.Entry<String, LongAdder>> invalidations =
            new ArrayList<>(legacyGraphicsLoweringCacheInvalidationReasons.entrySet());
        invalidations.sort((left, right) -> Long.compare(right.getValue().sum(), left.getValue().sum()));
        int invalidationRank = 1;
        for (Map.Entry<String, LongAdder> invalidation : invalidations) {
            if (invalidationRank > 8) {
                break;
            }
            builder.append("legacy_graphics_lowering_invalidation_rank_").append(invalidationRank)
                .append('=').append(invalidation.getKey())
                .append("|removed=").append(invalidation.getValue().sum())
                .append('\n');
            invalidationRank++;
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

    private static void updateMax(AtomicLong target, long value) {
        long current = target.get();
        while (value > current && !target.compareAndSet(current, value)) {
            current = target.get();
        }
    }

    private record DynamicTransformsSample(long bufferHandle, long offset, long range, long contentHash) {
    }

    private record UniformBindingSample(long bufferHandle, long offset, long range, long contentHash) {
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

    private static final class DescriptorCounters {
        private final LongAdder planCount = new LongAdder();
        private final LongAdder planNanos = new LongAdder();
        private final LongAdder planBindings = new LongAdder();
        private final LongAdder planReuseLookupCount = new LongAdder();
        private final LongAdder planReuseHitCount = new LongAdder();
        private final LongAdder planReuseMissCount = new LongAdder();
        private final LongAdder equivalentConsecutivePlanCount = new LongAdder();
        private final LongAdder cacheablePlanCount = new LongAdder();
        private final LongAdder nonCacheablePlanCount = new LongAdder();
        private final LongAdder samplerBindings = new LongAdder();
        private final LongAdder uniformBufferBindings = new LongAdder();
        private final LongAdder storageImageBindings = new LongAdder();
        private final LongAdder texelBufferBindings = new LongAdder();
        private final LongAdder cacheLookupCount = new LongAdder();
        private final LongAdder cacheHitCount = new LongAdder();
        private final LongAdder cacheMissCount = new LongAdder();
        private final Map<String, LongAdder> cacheMissReasons = new ConcurrentHashMap<>();
        private final LongAdder allocationCount = new LongAdder();
        private final LongAdder allocationNanos = new LongAdder();
        private final LongAdder updateCount = new LongAdder();
        private final LongAdder updateNanos = new LongAdder();
        private final LongAdder writeCount = new LongAdder();
        private final LongAdder samplerWrites = new LongAdder();
        private final LongAdder uniformBufferWrites = new LongAdder();
        private final LongAdder storageImageWrites = new LongAdder();
        private final LongAdder texelBufferWrites = new LongAdder();
        private final LongAdder commandBindCount = new LongAdder();
        private final LongAdder commandBindNanos = new LongAdder();
        private final LongAdder transientUniformCopyCount = new LongAdder();
        private final LongAdder transientUniformCopyBytes = new LongAdder();
        private final LongAdder transientUniformCopyNanos = new LongAdder();

        private long totalWeight() {
            return planCount.sum()
                + planReuseLookupCount.sum()
                + allocationCount.sum()
                + updateCount.sum()
                + commandBindCount.sum()
                + transientUniformCopyCount.sum();
        }

        private String dominantMissReason() {
            String dominantReason = "none";
            long dominantCount = 0L;
            for (Map.Entry<String, LongAdder> entry : cacheMissReasons.entrySet()) {
                long count = entry.getValue().sum();
                if (count > dominantCount) {
                    dominantCount = count;
                    dominantReason = entry.getKey() + ":" + count;
                }
            }
            return dominantReason;
        }
    }

    private static final class StandaloneUniformCounters {
        private final LongAdder bindingCount = new LongAdder();
        private final LongAdder handleChangeCount = new LongAdder();
        private final LongAdder offsetChangeCount = new LongAdder();
        private final LongAdder rangeChangeCount = new LongAdder();
        private final LongAdder contentChangeCount = new LongAdder();
        private final LongAdder contentReuseCount = new LongAdder();
        private final LongAdder uploadCount = new LongAdder();
        private final LongAdder uploadBytes = new LongAdder();
        private final LongAdder reuseHitCount = new LongAdder();
        private final LongAdder sourceReuseCount = new LongAdder();
        private final LongAdder reservedBytes = new LongAdder();

        private long totalWeight() {
            return bindingCount.sum() + uploadCount.sum() + reuseHitCount.sum() + sourceReuseCount.sum();
        }
    }

    private static final class LegacyGraphicsLoweringCounters {
        private final Map<String, PhaseCounters> steps = new ConcurrentHashMap<>();
        private final Map<String, LoweringCacheCounters> caches = new ConcurrentHashMap<>();
        private final Map<String, LoweringAllocationCounters> allocations = new ConcurrentHashMap<>();

        private PhaseCounters step(String name) {
            return steps.computeIfAbsent(name, ignored -> new PhaseCounters());
        }

        private LoweringCacheCounters cache(String name) {
            return caches.computeIfAbsent(name, ignored -> new LoweringCacheCounters());
        }

        private LoweringAllocationCounters allocation(String name) {
            return allocations.computeIfAbsent(name, ignored -> new LoweringAllocationCounters());
        }

        private long stepCount() {
            long total = 0L;
            for (PhaseCounters counters : steps.values()) {
                total += counters.count.sum();
            }
            return total;
        }

        private long stepNanos() {
            long total = 0L;
            for (PhaseCounters counters : steps.values()) {
                total += counters.totalNanos.sum();
            }
            return total;
        }

        private long totalWeight() {
            long total = stepCount();
            for (LoweringCacheCounters counters : caches.values()) {
                total += counters.lookupCount.sum();
            }
            return total;
        }
    }

    private static final class LoweringCacheCounters {
        private final LongAdder lookupCount = new LongAdder();
        private final LongAdder hitCount = new LongAdder();
        private final LongAdder missCount = new LongAdder();
        private final AtomicLong cacheSize = new AtomicLong();
        private final AtomicLong highWater = new AtomicLong();

        private void record(boolean hit, int size, int highWater) {
            lookupCount.increment();
            if (hit) {
                hitCount.increment();
            } else {
                missCount.increment();
            }
            cacheSize.set(Math.max(0, size));
            updateMax(this.highWater, Math.max(0, highWater));
        }
    }

    private static final class LoweringAllocationCounters {
        private final LongAdder eventCount = new LongAdder();
        private final LongAdder objectCount = new LongAdder();
        private final LongAdder estimatedBytes = new LongAdder();

        private void record(long objects, long bytes) {
            eventCount.increment();
            objectCount.add(Math.max(0L, objects));
            estimatedBytes.add(Math.max(0L, bytes));
        }
    }

    private record FrameStats(int count, long medianNanos, long p95Nanos, long p99Nanos, long worstNanos) {
    }

    private record ProcessMemorySnapshot(long rssKb, long rssAnonKb, long rssFileKb, long rssShmemKb) {
        private static final ProcessMemorySnapshot UNAVAILABLE = new ProcessMemorySnapshot(-1L, -1L, -1L, -1L);
    }
}
