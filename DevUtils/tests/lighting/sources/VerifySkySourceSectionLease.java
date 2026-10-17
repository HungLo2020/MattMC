package net.minecraft.world.level.lighting;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.NativeReleaseScope;
import net.minecraft.world.level.chunk.ProtoChunk;

/** Current-only actual scoped section retirement; no Frozen scope API exists. */
public final class VerifySkySourceSectionLease {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var source = new ChunkSkyLightSources(LevelHeightAccessor.create(-64, 64));
        ProtoChunk chunk;
        long[] expected;
        long closed = NativeReleaseScope.closed();
        try (var scope = NativeReleaseScope.open()) {
            if (scope == null) throw new AssertionError("Actual native release scope is required");
            chunk = SkyLightSourcesFixtures.chunk("shapes", -64, 64, 1977);
            var oracle = new JavaSkyLightSources(chunk);
            JavaSkyLightSources.fill(oracle, chunk);
            source.fillFrom(chunk);
            expected = oracle.nativeRaw().clone();
            if (!source.nativeMatches(expected)) throw new AssertionError("Scoped source scan differs from independent Java oracle");
        }
        if (NativeReleaseScope.closed() != closed + 1) throw new AssertionError("Actual section arena did not close after source scan");
        if (!source.nativeMatches(expected)) throw new AssertionError("Source retained a borrowed section payload");
        long[] after = source.nativeSnapshot();
        System.out.println("SOURCE_SECTION_LEASE actual-section-close source-independent=true");
        try {
            source.fillFrom(chunk);
            throw new AssertionError("Closed section arena was read by source reconstruction");
        } catch (IllegalStateException expectedClosed) {
            if (!Arrays.equals(after, source.nativeSnapshot())) throw new AssertionError("Closed section failure changed source payload", expectedClosed);
        }
        System.out.println("SOURCE_SECTION_LEASE closed-section-rejected source-unchanged=true");
    }
}
