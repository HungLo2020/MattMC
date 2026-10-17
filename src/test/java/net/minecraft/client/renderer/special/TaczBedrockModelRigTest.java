package net.minecraft.client.renderer.special;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every TaCZ gun and attachment geometry, randomly animated, must emit the same
 * quads through its retained ModelPart rig as the per-node Java walk it replaced.
 */
class TaczBedrockModelRigTest {
    private static final Path GEO_MODELS = Path.of("src/main/resources/assets/minecraft/geo_models");

    @Test
    void retainedRigMatchesBedrockNodeWalkForEveryGunAndAttachment() throws IOException {
        List<Path> geometries;
        try (Stream<Path> gun = Files.list(GEO_MODELS.resolve("gun"));
             Stream<Path> attachment = Files.list(GEO_MODELS.resolve("attachment"))) {
            geometries = Stream.concat(gun, attachment).filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(geometries.size() > 60, "expected the bundled TaCZ geometries");
        for (Path path : geometries) {
            for (long seed = 1; seed <= 3; seed++) {
                double deviation;
                try (Reader reader = Files.newBufferedReader(path)) {
                    deviation = TaczGlock17SpecialRenderer.maxBedrockModelDeviationForTest(reader, seed);
                }
                assertTrue(deviation >= 0.0, path.getFileName() + " emitted no quads");
                assertTrue(deviation < 1.0e-4, path.getFileName() + " seed " + seed + " deviates by " + deviation);
            }
        }
    }
}
