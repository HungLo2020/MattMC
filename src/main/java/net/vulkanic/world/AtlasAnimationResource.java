package net.vulkanic.world;

import net.minecraft.client.renderer.texture.SemanticAtlasAnimationSource;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resource-incarnation-owned semantic events, independent of native readiness. */
public final class AtlasAnimationResource implements AutoCloseable {
    private final SemanticAtlasAnimationSource source;
    private final int semanticTextureId;
    private final ResourceLocation atlas;
    private final AtlasAnimationVisibility visibility;
    private final AtlasAnimationTickDelivery ticks;
    /**
     * The last accepted semantic tick before this resource incarnation was
     * staged.  Resource replacement changes the atlas generation, but it does
     * not reset the client simulation clock.  Rust uses this value as the
     * replacement clock's starting point so the first queued event remains a
     * consecutive tick without inventing any missing history.
     */
    private final long initialTick;
    private final boolean retainRuntimeEpoch;
    private boolean closed;
    private long lastProducedTick;
    /** Native publication may be retired while this semantic resource lives. */
    private long publicationInitialTick;
    private static final Map<Integer, Long> RUNTIME_TICK_EPOCHS = new ConcurrentHashMap<>();

    static long runtimeEpochForDiagnostics(int textureId) {
        return RUNTIME_TICK_EPOCHS.getOrDefault(textureId, 0L);
    }

    /** Private admission for nonstandard atlas consumers. */
    public static boolean privateTickDeliveryEnabled() {
        return Boolean.getBoolean("mattmc.dev.rustGalAtlasAnimation");
    }

    /** Shield resources and their GUI/held consumers share Vulkan frame ownership. */
    public static boolean shieldLifecycleEnabled() {
        return net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
    }

    /** Capture-only route provenance; resource/native identities have separate receipts. */
    public static String shieldAdmissionReceipt() {
        boolean normal = shieldLifecycleEnabled();
        boolean privateFlags = System.getProperty("mattmc.dev.rustGalShieldAtlas") != null
            || System.getProperty("mattmc.dev.rustGalShieldAtlasAnimation") != null;
        return "{\"schema\":\"rust-owned-shield-atlas-v1\",\"normalRoute\":" + normal
            + ",\"privateFlagsPresent\":" + privateFlags + "}";
    }

    /** Standard atlas clocks follow the resource lifetime, not a draw/admission flag. */
    public boolean tickDeliveryEnabled() {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS.equals(atlas)
            || net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES.equals(atlas)
            || shieldLifecycleEnabled() && net.minecraft.client.renderer.Sheets.SHIELD_SHEET.equals(atlas)
            || privateTickDeliveryEnabled();
    }

    /** The ID names a semantic asset, never a Java or native GPU texture handle. */
    public AtlasAnimationResource(ResourceLocation atlas, int semanticTextureId, SemanticAtlasAnimationSource source) {
        this(atlas, semanticTextureId, source, 0, false);
    }

    /** Resource-incarnation constructor carrying the prior semantic clock epoch. */
    public AtlasAnimationResource(ResourceLocation atlas, int semanticTextureId,
        SemanticAtlasAnimationSource source, long initialTick) {
        this(atlas, semanticTextureId, source, initialTick, false);
    }

    /** Runtime atlas incarnation whose epoch survives fresh TextureAtlas objects. */
    public static AtlasAnimationResource runtime(ResourceLocation atlas, int semanticTextureId,
        SemanticAtlasAnimationSource source, long initialTick) {
        return new AtlasAnimationResource(atlas, semanticTextureId, source, initialTick, true);
    }

    private AtlasAnimationResource(ResourceLocation atlas, int semanticTextureId,
        SemanticAtlasAnimationSource source, long initialTick, boolean retainRuntimeEpoch) {
        // Java carries the nonzero native u32 identity's bit pattern.
        if (semanticTextureId == 0) throw new IllegalArgumentException("Invalid semantic atlas texture identity");
        if (initialTick < 0) throw new IllegalArgumentException("Invalid semantic atlas animation epoch");
        if (retainRuntimeEpoch) {
            initialTick = Math.max(initialTick, RUNTIME_TICK_EPOCHS.getOrDefault(semanticTextureId, 0L));
        }
        this.semanticTextureId = semanticTextureId;
        this.atlas = java.util.Objects.requireNonNull(atlas);
        this.source = java.util.Objects.requireNonNull(source);
        this.initialTick = initialTick;
        this.retainRuntimeEpoch = retainRuntimeEpoch;
        visibility = new AtlasAnimationVisibility(atlas, source);
        ticks = new AtlasAnimationTickDelivery(semanticTextureId, source.generation(), initialTick);
        lastProducedTick = initialTick;
        publicationInitialTick = initialTick;
    }

    public SemanticAtlasAnimationSource source() { return source; }
    public int semanticTextureId() { return semanticTextureId; }
    public ResourceLocation atlas() { return atlas; }

    /** Read-only capture evidence; this is not native acceptance or frame selection. */
    public synchronized long producedTickForDiagnostics() { return lastProducedTick; }
    /** The clock epoch Rust must use when staging this resource incarnation. */
    public long initialTick() { return initialTick; }
    /** Clock epoch for the current native publication incarnation. */
    synchronized long publicationInitialTick() { return publicationInitialTick; }
    public synchronized boolean producedTickNamedSpriteForDiagnostics(int spriteId) {
        return ticks.lastQueuedTickNamedSpriteForDiagnostics(spriteId);
    }
    public synchronized long lastNonemptyTickNamingSpriteForDiagnostics(int spriteId) {
        return ticks.lastNonemptyTickNamingSpriteForDiagnostics(spriteId);
    }

    public synchronized boolean recordUse(ResourceLocation atlas, ResourceLocation name) {
        return !closed && visibility.recordUse(atlas, name);
    }

    /** Tick production is semantic only; frame selection remains entirely in Rust. */
    public synchronized void enqueueTick(long tick, boolean onlyVisible) {
        requireOpen();
        ticks.enqueue(tick, onlyVisible, visibility);
        lastProducedTick = tick;
        if (retainRuntimeEpoch) {
            RUNTIME_TICK_EPOCHS.merge(semanticTextureId, tick, Long::max);
        }
    }

    public synchronized void enqueueNextTick(boolean onlyVisible) {
        enqueueTick(Math.addExact(lastProducedTick, 1), onlyVisible);
    }

    /** Retire native publication state while retaining this semantic resource. */
    synchronized void invalidatePublication() {
        if (closed) return;
        ticks.discard();
        publicationInitialTick = Math.max(publicationInitialTick, lastProducedTick);
    }

    synchronized int pendingTickCount() { return ticks.pendingCount(); }

    synchronized boolean drain(long acceptedTextureGeneration, AtlasAnimationTickDelivery.Submit submit) {
        requireOpen();
        if (acceptedTextureGeneration <= 0) throw new IllegalArgumentException("Native texture is not accepted");
        // Resource and native storage generations are different identity domains.
        // Bind transport at delivery; never reset history when staging is delayed.
        return ticks.drain((texture, resourceGeneration, tick, ids, onlyVisible) ->
            submit.accept(texture, acceptedTextureGeneration, tick, ids, onlyVisible));
    }

    synchronized void requireOpen() {
        if (closed) throw new IllegalStateException("Animation resource incarnation is retired");
    }

    @Override
    public synchronized void close() {
        closed = true;
        visibility.clearUses();
        ticks.discard();
    }
}
