package net.vulkanic.diagnostics;

import net.minecraft.client.dev.DeterministicCameraCapture;
import net.vulkanic.VulkanicDrawStateSnapshot;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;
import net.vulkanic.VulkanicTextureView;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Coordinates development-only render-target content diagnostics.
 *
 * <p>This class owns diagnostic request state, readback budgets, dedupe keys,
 * scoped Iris colortex lifecycle metadata, and canonical hash formatting. It
 * does not execute backend readbacks; callers provide already-open texture views
 * or narrow callbacks that execute readbacks in the existing resource/backend
 * layer.</p>
 */
public final class RenderTargetContentDiagnostics {
    private static final java.util.Set<String> SCOPED_COMPOSITE_COLORTEX0_EMITTED =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentMap<Integer, List<DiagnosticIrisColorAttachment>> IRIS_FRAMEBUFFER_ATTACHMENTS =
        new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, DiagnosticIrisColorAttachment> IRIS_TEXTURE_ATTACHMENTS =
        new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ScopedCompositeColortex0Producer> SCOPED_COMPOSITE_COLORTEX0_PRODUCERS =
        new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, PendingScopedCompositeColortex0SamplerReadback> PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS =
        new ConcurrentHashMap<>();

    @Nullable
    private static volatile ScopedCompositeColortex0Binding scopedCompositeColortex0Binding;
    @Nullable
    private static volatile DiagnosticViewportState lastViewport;

    private RenderTargetContentDiagnostics() {
    }

    public record ScopedCompositeColortex0Binding(
        @Nullable String pipelineHandleDescription,
        String pipelineLocation,
        String vertexShader,
        String fragmentShader,
        String pipelineKey,
        String stableKey,
        String resourceName,
        int resourceSet,
        int resourceBinding,
        String resourceType,
        List<String> stages,
        int samplerUnit,
        @Nullable Object samplerObject,
        @Nullable VulkanicTexture texture,
        int baseMipLevel,
        int mipLevelCount,
        int legacyTextureId,
        String source
    ) {}

    public record DiagnosticIrisColorAttachment(
        int framebuffer,
        int colorAttachment,
        int logicalIndex,
        int textureId,
        String logicalName,
        String pingPong,
        String source
    ) {}

    public record ScopedCompositeColortex0Producer(
        String backend,
        String source,
        String passLabel,
        String customPassName,
        String pipelineLocation,
        String physicalKey,
        int textureId,
        String logicalAttachment,
        int colorAttachment,
        String pingPong,
        String descriptorSignature,
        String attachmentUsage,
        String lifecycleInfo,
        String poseName,
        String deterministicFields,
        DiagnosticTextureContentHash hash
    ) {}

    public interface PendingReadbackAction {
        DiagnosticTextureContentHash read();

        String lifecycleInfo();
    }

    public record PendingScopedCompositeColortex0SamplerReadback(
        String backend,
        String customPassName,
        String pipelineLocation,
        String vertexShader,
        String fragmentShader,
        String pipelineKey,
        String stableKey,
        String resourceName,
        int textureUnit,
        @Nullable Object samplerObject,
        int legacyTextureId,
        String physicalKey,
        String outputLogical,
        String outputPingPong,
        int colorAttachment,
        int outputTextureId,
        String renderTarget,
        String attachmentUsage,
        String draw,
        String vertexInput,
        String pipelineState,
        String viewport,
        String scissor,
        String poseName,
        String deterministicFields,
        PendingReadbackAction readbackAction
    ) {}

    public record DiagnosticProducerAttachment(
        int colorAttachment,
        int textureId,
        String logicalName,
        String pingPong,
        String usage
    ) {}

    public record DiagnosticViewportState(int x, int y, int width, int height) {
        public String describe() {
            return x + "," + y + "," + width + "," + height;
        }
    }

    public record DiagnosticTextureContentHash(
        String logicalResource,
        int width,
        int height,
        @Nullable VulkanicTextureFormat storageFormat,
        String canonicalFormat,
        int mip,
        int layer,
        String originConvention,
        String channelInterpretation,
        String hash,
        String tileHashes
    ) {
        public static DiagnosticTextureContentHash unavailable(
            String logicalResource,
            @Nullable VulkanicTexture texture,
            @Nullable VulkanicTextureView textureView,
            String reason
        ) {
            VulkanicTextureFormat format = texture == null ? null : texture.getVulkanicFormat();
            int width = textureView == null ? -1 : safeTextureViewWidth(textureView);
            int height = textureView == null ? -1 : safeTextureViewHeight(textureView);
            int mip = textureView == null ? 0 : textureView.getBaseMipLevel();
            return unavailable(logicalResource, format, width, height, mip, reason);
        }

        public static DiagnosticTextureContentHash unavailable(
            String logicalResource,
            @Nullable VulkanicTextureFormat format,
            int width,
            int height,
            int mip,
            String reason
        ) {
            return new DiagnosticTextureContentHash(
                VulkanicDiagnostics.sanitizeLabel(logicalResource == null ? "unknown" : logicalResource),
                width,
                height,
                format,
                "unavailable",
                mip,
                0,
                "unavailable",
                "unavailable",
                "unavailable:" + VulkanicDiagnostics.sanitizeLabel(reason),
                ""
            );
        }
    }

    public static void recordViewport(int x, int y, int width, int height) {
        lastViewport = new DiagnosticViewportState(x, y, width, height);
    }

    public static @Nullable DiagnosticViewportState lastViewport() {
        return lastViewport;
    }

    public static VulkanicDrawStateSnapshot.ViewportStateSnapshot viewportSnapshot() {
        DiagnosticViewportState viewport = lastViewport;
        if (viewport == null) {
            return VulkanicDrawStateSnapshot.ViewportStateSnapshot.unknown();
        }
        return new VulkanicDrawStateSnapshot.ViewportStateSnapshot(
            true,
            viewport.x(),
            viewport.y(),
            viewport.width(),
            viewport.height()
        );
    }

    public static void recordScopedCompositeBinding(ScopedCompositeColortex0Binding binding) {
        if (VulkanicDiagnostics.TRACE_RENDER_TARGET_CONTENT_HASHES) {
            scopedCompositeColortex0Binding = binding;
        }
    }

    public static @Nullable ScopedCompositeColortex0Binding scopedCompositeBinding() {
        return scopedCompositeColortex0Binding;
    }

    public static boolean markScopedCompositePoseEmitted(String backend, String poseName, ScopedCompositeColortex0Binding binding, String physicalKey) {
        String emitKey = backend + "|" + poseName + "|"
            + binding.stableKey() + "|"
            + physicalKey
            + "|mip:" + binding.baseMipLevel() + ':' + binding.mipLevelCount();
        return SCOPED_COMPOSITE_COLORTEX0_EMITTED.add(emitKey);
    }

    public static void recordIrisColorAttachment(
        int framebuffer,
        int colorAttachment,
        int logicalIndex,
        int textureId,
        boolean writesMain,
        String source
    ) {
        if (!VulkanicDiagnostics.TRACE_RENDER_TARGET_CONTENT_HASHES || textureId <= 0) {
            return;
        }
        DiagnosticIrisColorAttachment attachment = new DiagnosticIrisColorAttachment(
            framebuffer,
            colorAttachment,
            logicalIndex,
            textureId,
            "colortex" + logicalIndex,
            writesMain ? "main" : "alt",
            source
        );
        IRIS_TEXTURE_ATTACHMENTS.put(textureId, attachment);
        IRIS_FRAMEBUFFER_ATTACHMENTS.compute(framebuffer, (ignored, existing) -> {
            ArrayList<DiagnosticIrisColorAttachment> updated = existing == null
                ? new ArrayList<>()
                : new ArrayList<>(existing);
            updated.removeIf(previous -> previous.colorAttachment() == colorAttachment);
            updated.add(attachment);
            updated.sort(Comparator.comparingInt(DiagnosticIrisColorAttachment::colorAttachment));
            return List.copyOf(updated);
        });
    }

    public static List<DiagnosticProducerAttachment> producerAttachments(
        @Nullable VulkanicRenderTargetDescriptor descriptor,
        int framebuffer
    ) {
        ArrayList<DiagnosticProducerAttachment> attachments = new ArrayList<>();
        if (descriptor != null) {
            for (int colorIndex = 0; colorIndex < descriptor.colorAttachments().size(); colorIndex++) {
                VulkanicRenderTargetDescriptor.ColorAttachment colorAttachment = descriptor.colorAttachments().get(colorIndex);
                DiagnosticIrisColorAttachment irisAttachment = IRIS_TEXTURE_ATTACHMENTS.get(colorAttachment.textureId());
                String logicalName = irisAttachment == null ? "unknown" : irisAttachment.logicalName();
                String pingPong = irisAttachment == null ? "unknown" : irisAttachment.pingPong();
                String usage = "initial=" + colorAttachment.initialUsage()
                    + ",pass=" + colorAttachment.passUsage()
                    + ",final=" + colorAttachment.finalUsage()
                    + ",load=" + colorAttachment.loadOp()
                    + ",store=" + colorAttachment.storeOp();
                attachments.add(new DiagnosticProducerAttachment(
                    colorIndex,
                    colorAttachment.textureId(),
                    logicalName,
                    pingPong,
                    usage
                ));
            }
            return attachments;
        }

        List<DiagnosticIrisColorAttachment> framebufferAttachments =
            IRIS_FRAMEBUFFER_ATTACHMENTS.getOrDefault(framebuffer, List.of());
        for (DiagnosticIrisColorAttachment attachment : framebufferAttachments) {
            attachments.add(new DiagnosticProducerAttachment(
                attachment.colorAttachment(),
                attachment.textureId(),
                attachment.logicalName(),
                attachment.pingPong(),
                "irisFramebufferAttachment=true,source=" + attachment.source()
            ));
        }
        return attachments;
    }

    public static boolean reserveContentReadback(String feature, String readbackKey) {
        return reserveContentReadback(
            feature,
            readbackKey,
            VulkanicDiagnostics.TRACE_RENDER_TARGET_CONTENT_HASHES,
            VulkanicDiagnostics.MAX_RENDER_TARGET_CONTENT_READBACKS
        );
    }

    public static boolean reserveContentReadbackForTests(String feature, String readbackKey, int maxReadbacks) {
        return reserveContentReadback(feature, readbackKey, true, maxReadbacks);
    }

    private static boolean reserveContentReadback(String feature, String readbackKey, boolean enabled, int maxReadbacks) {
        if (!enabled) {
            return false;
        }

        String normalizedKey = feature + '|' + readbackKey;
        if (!VulkanicDiagnostics.RENDER_TARGET_CONTENT_READBACK_KEYS.add(normalizedKey)) {
            return false;
        }

        int count = VulkanicDiagnostics.RENDER_TARGET_CONTENT_READBACK_COUNT.incrementAndGet();
        if (count <= maxReadbacks) {
            return true;
        }
        VulkanicDiagnostics.RENDER_TARGET_CONTENT_READBACK_KEYS.remove(normalizedKey);
        return false;
    }

    public static String contentReadbackUnavailableReason(@Nullable String featureDisabledReason, String feature, String readbackKey) {
        return contentReadbackUnavailableReason(
            featureDisabledReason,
            feature,
            readbackKey,
            VulkanicDiagnostics.TRACE_RENDER_TARGET_CONTENT_HASHES,
            VulkanicDiagnostics.MAX_RENDER_TARGET_CONTENT_READBACKS
        );
    }

    public static String contentReadbackUnavailableReasonForTests(
        @Nullable String featureDisabledReason,
        String feature,
        String readbackKey,
        int maxReadbacks
    ) {
        return contentReadbackUnavailableReason(featureDisabledReason, feature, readbackKey, true, maxReadbacks);
    }

    private static String contentReadbackUnavailableReason(
        @Nullable String featureDisabledReason,
        String feature,
        String readbackKey,
        boolean enabled,
        int maxReadbacks
    ) {
        if (!enabled) {
            return "content-hashes-disabled";
        }
        if (featureDisabledReason != null && !featureDisabledReason.isBlank()) {
            return featureDisabledReason;
        }
        String normalizedKey = feature + '|' + readbackKey;
        boolean alreadyReserved = VulkanicDiagnostics.RENDER_TARGET_CONTENT_READBACK_KEYS.contains(normalizedKey);
        if (alreadyReserved) {
            return "content-readback-duplicate-skipped";
        }
        if (VulkanicDiagnostics.RENDER_TARGET_CONTENT_READBACK_COUNT.get() >= maxReadbacks) {
            return "content-readback-budget-exhausted";
        }
        return "content-readback-unavailable";
    }

    public static boolean isDeterministicCaptureEligiblePose() {
        if (VulkanicDiagnostics.TRACE_RENDER_TARGET_CONTENT_HASHES_INITIAL_POSE_ONLY) {
            return "initial".equals(DeterministicCameraCapture.currentPoseNameForDiagnostics());
        }
        String poseName = DeterministicCameraCapture.currentPoseNameForDiagnostics();
        return "initial".equals(poseName)
            || "right".equals(poseName)
            || "left".equals(poseName)
            || "return".equals(poseName);
    }

    public static boolean shouldTraceScopedCompositeColortex0Pass(String customPassName) {
        return "composite".equals(customPassName) || "composite3".equals(customPassName);
    }

    public static boolean shouldHashScopedCompositeColortex0Producer(
        String customPassName,
        DiagnosticProducerAttachment attachment
    ) {
        if (!isDeterministicCaptureEligiblePose()) {
            return false;
        }
        if ("composite".equals(customPassName)) {
            return "main".equals(attachment.pingPong());
        }
        if (VulkanicDiagnostics.TRACE_RENDER_TARGET_CONTENT_HASHES_INITIAL_POSE_ONLY) {
            return false;
        }
        if ("composite3".equals(customPassName)) {
            return "alt".equals(attachment.pingPong());
        }
        return false;
    }

    public static boolean isScopedCompositeColortex0ProducerOutput(
        String customPassName,
        DiagnosticProducerAttachment attachment
    ) {
        if (!"colortex0".equals(attachment.logicalName())) {
            return false;
        }
        if ("composite".equals(customPassName)) {
            return "main".equals(attachment.pingPong());
        }
        if ("composite3".equals(customPassName)) {
            return "alt".equals(attachment.pingPong());
        }
        return false;
    }

    public static void recordProducer(ScopedCompositeColortex0Producer producer) {
        SCOPED_COMPOSITE_COLORTEX0_PRODUCERS.put(producer.backend() + '|' + producer.physicalKey(), producer);
    }

    public static @Nullable ScopedCompositeColortex0Producer producer(String backend, String physicalKey) {
        return SCOPED_COMPOSITE_COLORTEX0_PRODUCERS.get(backend + '|' + physicalKey);
    }

    public static void recordPendingScopedCompositeSamplerReadback(
        String pendingKey,
        PendingScopedCompositeColortex0SamplerReadback request
    ) {
        PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.putIfAbsent(pendingKey, request);
    }

    public static boolean hasPendingScopedCompositeSamplerReadbacks() {
        return !PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.isEmpty();
    }

    public static List<Map.Entry<String, PendingScopedCompositeColortex0SamplerReadback>> drainPendingScopedCompositeSamplerReadbacks() {
        List<Map.Entry<String, PendingScopedCompositeColortex0SamplerReadback>> pending =
            new ArrayList<>(PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.entrySet());
        pending.sort(Map.Entry.comparingByKey());
        ArrayList<Map.Entry<String, PendingScopedCompositeColortex0SamplerReadback>> drained = new ArrayList<>();
        for (Map.Entry<String, PendingScopedCompositeColortex0SamplerReadback> entry : pending) {
            PendingScopedCompositeColortex0SamplerReadback snapshot = entry.getValue();
            if (PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.remove(entry.getKey(), snapshot)) {
                drained.add(Map.entry(entry.getKey(), snapshot));
            }
        }
        return List.copyOf(drained);
    }

    public static String contentHashFields(DiagnosticTextureContentHash contentHash) {
        return "logicalResource=" + contentHash.logicalResource()
            + ",mip=" + contentHash.mip()
            + ",layer=" + contentHash.layer()
            + ",region=0:0:" + contentHash.width() + ':' + contentHash.height()
            + ",canonicalFormat=" + contentHash.canonicalFormat()
            + ",storageFormat=" + contentHash.storageFormat()
            + ",origin=" + contentHash.originConvention()
            + ",channels=" + contentHash.channelInterpretation()
            + ",hash=" + contentHash.hash()
            + (contentHash.tileHashes().isBlank() ? "" : ",tileHashes=" + contentHash.tileHashes());
    }

    public static String textureString(@Nullable VulkanicTexture texture) {
        if (texture == null) {
            return "missing";
        }
        return "{label=\"" + VulkanicDiagnostics.sanitizeLabel(texture.getLabel()) + '"'
            + ",format=" + texture.getVulkanicFormat()
            + ",width=" + safeTextureWidth(texture, 0)
            + ",height=" + safeTextureHeight(texture, 0)
            + ",layers=" + texture.getDepthOrLayers()
            + ",mips=" + texture.getMipLevels()
            + ",usage=" + texture.usage()
            + ",closed=" + texture.isClosed()
            + "}";
    }

    public static String scopedCompositeColortex0ResourceString(
        ScopedCompositeColortex0Binding binding,
        @Nullable VulkanicTextureView textureView,
        DiagnosticTextureContentHash contentHash,
        String deterministicFields
    ) {
        VulkanicTexture texture = textureView == null ? binding.texture() : textureView.texture();
        String viewDescription = textureView == null
            ? "missing"
            : "{viewClass=" + textureView.getClass().getSimpleName()
            + ",baseMip=" + textureView.getBaseMipLevel()
            + ",mips=" + textureView.getMipLevelCount()
            + ",width=" + safeTextureViewWidth(textureView)
            + ",height=" + safeTextureViewHeight(textureView)
            + ",closed=" + textureView.isClosed()
            + ",texture=" + textureString(texture)
            + "}";
        return binding.resourceName()
            + "{layout=set:" + binding.resourceSet()
            + ",binding:" + binding.resourceBinding()
            + ",type:" + binding.resourceType()
            + ",stages:[" + String.join(", ", binding.stages()) + "]"
            + ",sampler={unit=" + binding.samplerUnit()
            + ",samplerObject=" + (binding.samplerObject() == null ? "none" : binding.samplerObject())
            + ",legacyTextureId=" + binding.legacyTextureId()
            + ",view=" + viewDescription
            + "},contentHash={" + contentHashFields(contentHash)
            + ",poseContext={" + deterministicFields.replace(' ', ',') + "}}}";
    }

    public static boolean shouldTraceRenderTargetContentHash(String resourceName, @Nullable String label) {
        String normalizedName = resourceName == null ? "" : resourceName.toLowerCase(Locale.ROOT);
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return normalizedName.startsWith("colortex")
            || normalizedName.startsWith("depthtex")
            || normalizedName.contains("dhdepth")
            || normalizedName.contains("shadow")
            || normalizedName.contains("floodfill")
            || normalizedName.contains("history")
            || normalizedLabel.startsWith("colortex")
            || normalizedLabel.startsWith("depthtex")
            || normalizedLabel.contains("dhdepth")
            || normalizedLabel.contains("shadow")
            || normalizedLabel.contains("floodfill")
            || normalizedLabel.contains("history");
    }

    public static DiagnosticTextureContentHash contentHashFromCanonical(
        String logicalResource,
        VulkanicTexture texture,
        VulkanicTextureView textureView,
        ByteBuffer canonicalRgba32fTopLeft,
        int width,
        int height
    ) {
        ByteBuffer bytes = canonicalRgba32fTopLeft.duplicate();
        bytes.position(0);
        bytes.limit(bytes.capacity());
        bytes.limit(width * height * 4 * Float.BYTES);
        String hash = VulkanicDiagnostics.hash(bytes, bytes.remaining());
        String tileHashes;
        try {
            tileHashes = tileHashes(bytes, width, height);
        } catch (RuntimeException exception) {
            tileHashes = "unavailable:" + VulkanicDiagnostics.sanitizeLabel(
                exception.getClass().getSimpleName() + '-' + exception.getMessage()
            );
        }
        return new DiagnosticTextureContentHash(
            VulkanicDiagnostics.sanitizeLabel(logicalResource),
            width,
            height,
            texture.getVulkanicFormat(),
            "RGBA32F_LE",
            textureView.getBaseMipLevel(),
            0,
            "top-left-row-major",
            "raw-linear-shader-visible-components-alpha-one-when-source-lacks-alpha",
            hash,
            tileHashes
        );
    }

    public static ByteBuffer canonicalizeFloatComponentsToRgba32fTopLeft(
        ByteBuffer source,
        int width,
        int height,
        int sourceComponents,
        boolean sourceRowsAreBottomToTop
    ) {
        ByteBuffer input = source.duplicate().order(java.nio.ByteOrder.nativeOrder());
        input.position(0);
        java.nio.FloatBuffer floats = input.asFloatBuffer();
        ByteBuffer canonical = org.lwjgl.BufferUtils.createByteBuffer(width * height * 4 * Float.BYTES)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int y = 0; y < height; y++) {
            int sourceY = sourceRowsAreBottomToTop ? height - 1 - y : y;
            for (int x = 0; x < width; x++) {
                int sourceIndex = (sourceY * width + x) * sourceComponents;
                float r = sourceComponents >= 1 ? floats.get(sourceIndex) : 0.0F;
                float g = sourceComponents >= 2 ? floats.get(sourceIndex + 1) : r;
                float b = sourceComponents >= 3 ? floats.get(sourceIndex + 2) : r;
                float a = sourceComponents >= 4 ? floats.get(sourceIndex + 3) : 1.0F;
                canonical.putFloat(r);
                canonical.putFloat(g);
                canonical.putFloat(b);
                canonical.putFloat(a);
            }
        }
        canonical.flip();
        return canonical;
    }

    public static String tileHashes(ByteBuffer canonical, int width, int height) {
        if (width <= 0 || height <= 0) {
            return "";
        }
        int tilesX = 4;
        int tilesY = 4;
        int bytesPerPixel = 4 * Float.BYTES;
        int expectedBytes = width * height * bytesPerPixel;
        if (canonical.capacity() < expectedBytes) {
            return "unavailable:canonical-size-mismatch:expected-" + expectedBytes + ":actual-" + canonical.capacity();
        }
        List<String> hashes = new ArrayList<>(tilesX * tilesY);
        for (int tileY = 0; tileY < tilesY; tileY++) {
            int y0 = tileY * height / tilesY;
            int y1 = (tileY + 1) * height / tilesY;
            for (int tileX = 0; tileX < tilesX; tileX++) {
                int x0 = tileX * width / tilesX;
                int x1 = (tileX + 1) * width / tilesX;
                MessageDigest digest;
                try {
                    digest = MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException exception) {
                    throw new IllegalStateException("SHA-256 digest unavailable", exception);
                }
                ByteBuffer tileSource = canonical.duplicate();
                tileSource.position(0);
                tileSource.limit(tileSource.capacity());
                for (int y = y0; y < y1; y++) {
                    int rowOffset = (y * width + x0) * bytesPerPixel;
                    int rowLength = (x1 - x0) * bytesPerPixel;
                    if (rowOffset < 0 || rowLength < 0 || rowOffset + rowLength > tileSource.capacity()) {
                        return "unavailable:tile-range-mismatch:offset-" + rowOffset + ":length-" + rowLength + ":capacity-" + tileSource.capacity();
                    }
                    tileSource.limit(tileSource.capacity());
                    tileSource.position(rowOffset);
                    tileSource.limit(rowOffset + rowLength);
                    digest.update(tileSource.slice());
                }
                hashes.add(tileX + "x" + tileY + ":" + toHex(digest.digest()).substring(0, 16));
            }
        }
        return String.join("|", hashes);
    }

    public static void resetMutableStateForTests() {
        SCOPED_COMPOSITE_COLORTEX0_EMITTED.clear();
        IRIS_FRAMEBUFFER_ATTACHMENTS.clear();
        IRIS_TEXTURE_ATTACHMENTS.clear();
        SCOPED_COMPOSITE_COLORTEX0_PRODUCERS.clear();
        PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.clear();
        scopedCompositeColortex0Binding = null;
        lastViewport = null;
    }

    public static int pendingReadbackCountForTests() {
        return PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.size();
    }

    public static int producerCountForTests() {
        return SCOPED_COMPOSITE_COLORTEX0_PRODUCERS.size();
    }

    public static int irisFramebufferAttachmentCountForTests(int framebuffer) {
        return IRIS_FRAMEBUFFER_ATTACHMENTS.getOrDefault(framebuffer, List.of()).size();
    }

    private static int safeTextureViewWidth(VulkanicTextureView textureView) {
        try {
            return textureView.getWidth(0);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static int safeTextureViewHeight(VulkanicTextureView textureView) {
        try {
            return textureView.getHeight(0);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static int safeTextureWidth(VulkanicTexture texture, int mip) {
        try {
            return texture.getWidth(mip);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static int safeTextureHeight(VulkanicTexture texture, int mip) {
        try {
            return texture.getHeight(mip);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
