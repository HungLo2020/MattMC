package net.vulkanic.gui;

import java.util.HashMap;
import java.util.Map;

/** Bounded names for source model identities. No pixels, GPU objects, or redraw decisions. */
final class GuiItemSemanticIdentities {
    // Leave one native target available for the shared uncached raster. Once
    // these names are exhausted callers render normally without pixel reuse.
    private static final int LIMIT = 63;
    private static final Map<Object, Long> IDENTITIES = new HashMap<>();
    private static long nextIdentity = 1;

    private GuiItemSemanticIdentities() {}

    static synchronized long identityOrZero(Object immutableModelIdentity) {
        if (immutableModelIdentity == null) return 0;
        Long existing = IDENTITIES.get(immutableModelIdentity);
        if (existing != null) return existing;
        if (IDENTITIES.size() >= LIMIT || nextIdentity == Long.MAX_VALUE) return 0;
        long identity = nextIdentity++;
        IDENTITIES.put(immutableModelIdentity, identity);
        return identity;
    }

    static synchronized void clear() {
        IDENTITIES.clear(); // Never reuse names while old submissions might exist.
    }
}
