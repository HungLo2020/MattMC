package net.sodium.client.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RealChunkMeshingReplayRunnerLightingTest {
    private static final Path RUNNER = Path.of(
            "src/main/java/net/sodium/client/perf/real/RealChunkMeshingReplayRunner.java");

    @Test
    void fixtureSetupSettlesClientQueueAndLightEngineBeforeCloningSection() throws IOException {
        String source = Files.readString(RUNNER);
        int setupCall = source.indexOf("settleFixtureLighting(minecraft);");
        int renderContextCreation = source.indexOf("LevelSlice.prepare(minecraft.level, sectionPos, cache)");

        assertTrue(setupCall >= 0, "Replay fixture setup must explicitly settle lighting after block population");
        assertTrue(renderContextCreation > setupCall,
                "Replay must settle fixture lighting before cloning the section into LevelSlice");
        assertTrue(source.contains("minecraft.level.pollLightUpdates();"),
                "Replay must drain queued client light-update runnables");
        assertTrue(source.contains("minecraft.level.getChunkSource().getLightEngine().runLightUpdates();"),
                "Replay must run the light engine before capturing section-border light");
        assertTrue(source.contains("minecraft.level.getChunkSource().getLightEngine().hasLightWork()"),
                "Replay should wait until light-engine work settles before benchmarking");
        assertTrue(source.contains("int minY = SectionPos.sectionToBlockCoord(SECTION_Y) - 2;"),
                "Replay must clear the lower light-sampling margin used by smooth AO");
        assertTrue(source.contains("int maxY = minecraft.level.getMaxY();"),
                "Replay must clear generated blocks above the fixture so border skylight is deterministic");
        assertTrue(source.contains("WorldPresets::createFlatWorldDimensions"),
                "Replay should use a flat deterministic world so normal terrain generation cannot affect border skylight");
    }
}
