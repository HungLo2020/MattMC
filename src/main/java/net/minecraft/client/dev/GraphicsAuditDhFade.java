package net.minecraft.client.dev;

/**
 * Capture-only receipt for the DH vanilla-transition hooks.  This class does
 * not alter render policy or shader state; it only records that the existing
 * Frozen hook reached each configured fade call during a deterministic audit.
 */
public final class GraphicsAuditDhFade {
    private static int opaqueHookCalls;
    private static int transparentHookCalls;
    private static int opaqueRenderCalls;
    private static int transparentRenderCalls;
    private static int lodOpaquePassCalls;
    private static int lodTransparentPassCalls;
    private static int lodOpaqueMaxColumns;
    private static int lodOpaqueLastColumns;
    private static int lodTransparentMaxColumns;
    private static int levelHookCalls;
    private static int renderLodsCalls;
    private static int renderLayerCalls;
    private static int lodRendererRenderCalls;
    private static int lodRenderPassAttempts;
    private static int lodRenderPassValidationFailures;
    private static int lodRenderObjectSetupFailures;
    private static int lodFramebufferIncomplete;
    private static int deferredTrueCalls;
    private static int renderEventCancelledCalls;
    private static int deferredRenderEventCancelledCalls;
    private static int defaultRendererModeCalls;
    private static int nonDefaultRendererModeCalls;

    private GraphicsAuditDhFade() {}

    public static synchronized void reset() {
        opaqueHookCalls = 0;
        transparentHookCalls = 0;
        opaqueRenderCalls = 0;
        transparentRenderCalls = 0;
        lodOpaquePassCalls = 0;
        lodTransparentPassCalls = 0;
        lodOpaqueMaxColumns = 0;
        lodOpaqueLastColumns = 0;
        lodTransparentMaxColumns = 0;
        levelHookCalls = 0;
        renderLodsCalls = 0;
        renderLayerCalls = 0;
        lodRendererRenderCalls = 0;
        lodRenderPassAttempts = 0;
        lodRenderPassValidationFailures = 0;
        lodRenderObjectSetupFailures = 0;
        lodFramebufferIncomplete = 0;
        deferredTrueCalls = 0;
        renderEventCancelledCalls = 0;
        deferredRenderEventCancelledCalls = 0;
        defaultRendererModeCalls = 0;
        nonDefaultRendererModeCalls = 0;
    }

    public static synchronized void recordOpaqueHook() {
        opaqueHookCalls++;
    }

    public static synchronized void recordTransparentHook() {
        transparentHookCalls++;
    }

    public static synchronized void recordOpaqueRender() {
        opaqueRenderCalls++;
    }

    public static synchronized void recordTransparentRender() {
        transparentRenderCalls++;
    }

    public static synchronized void recordLodPass(boolean opaque, int columns) {
        if (opaque) {
            lodOpaquePassCalls++;
            lodOpaqueMaxColumns = Math.max(lodOpaqueMaxColumns, columns);
            lodOpaqueLastColumns = columns;
        } else {
            lodTransparentPassCalls++;
            lodTransparentMaxColumns = Math.max(lodTransparentMaxColumns, columns);
        }
    }

    public static synchronized void recordLevelHook() { levelHookCalls++; }
    public static synchronized void recordRenderLods() { renderLodsCalls++; }
    public static synchronized void recordRenderLayer() { renderLayerCalls++; }
    public static synchronized void recordLodRendererRender() { lodRendererRenderCalls++; }
    public static synchronized void recordLodRenderPassAttempt() { lodRenderPassAttempts++; }
    public static synchronized void recordLodRenderPassValidationFailure() { lodRenderPassValidationFailures++; }
    public static synchronized void recordLodRenderObjectSetupFailure() { lodRenderObjectSetupFailures++; }
    public static synchronized void recordLodFramebufferIncomplete() { lodFramebufferIncomplete++; }
    public static synchronized void recordDeferTransparent(boolean deferred) { if (deferred) deferredTrueCalls++; }
    public static synchronized void recordRenderEventCancelled(boolean deferredLayer) {
        if (deferredLayer) deferredRenderEventCancelledCalls++; else renderEventCancelledCalls++;
    }
    public static synchronized void recordRendererMode(boolean isDefault) {
        if (isDefault) defaultRendererModeCalls++; else nonDefaultRendererModeCalls++;
    }

    /** Capture-only counters used to stage an equivalent far-only fixture. */
    public static synchronized int lodOpaquePassCalls() { return lodOpaquePassCalls; }
    public static synchronized int lodOpaqueMaxColumns() { return lodOpaqueMaxColumns; }
    public static synchronized int lodOpaqueLastColumns() { return lodOpaqueLastColumns; }

    public static synchronized String json() {
        return "{\"opaqueHookCalls\":" + opaqueHookCalls
            + ",\"transparentHookCalls\":" + transparentHookCalls
            + ",\"opaqueRenderCalls\":" + opaqueRenderCalls
            + ",\"transparentRenderCalls\":" + transparentRenderCalls
            + ",\"lodOpaquePassCalls\":" + lodOpaquePassCalls
            + ",\"lodTransparentPassCalls\":" + lodTransparentPassCalls
            + ",\"lodOpaqueMaxColumns\":" + lodOpaqueMaxColumns
            + ",\"lodOpaqueLastColumns\":" + lodOpaqueLastColumns
            + ",\"lodTransparentMaxColumns\":" + lodTransparentMaxColumns
            + ",\"levelHookCalls\":" + levelHookCalls
            + ",\"renderLodsCalls\":" + renderLodsCalls
            + ",\"renderLayerCalls\":" + renderLayerCalls
            + ",\"lodRendererRenderCalls\":" + lodRendererRenderCalls
            + ",\"lodRenderPassAttempts\":" + lodRenderPassAttempts
            + ",\"lodRenderPassValidationFailures\":" + lodRenderPassValidationFailures
            + ",\"lodRenderObjectSetupFailures\":" + lodRenderObjectSetupFailures
            + ",\"lodFramebufferIncomplete\":" + lodFramebufferIncomplete
            + ",\"deferredTrueCalls\":" + deferredTrueCalls
            + ",\"renderEventCancelledCalls\":" + renderEventCancelledCalls
            + ",\"deferredRenderEventCancelledCalls\":" + deferredRenderEventCancelledCalls
            + ",\"defaultRendererModeCalls\":" + defaultRendererModeCalls
            + ",\"nonDefaultRendererModeCalls\":" + nonDefaultRendererModeCalls + "}";
    }
}
