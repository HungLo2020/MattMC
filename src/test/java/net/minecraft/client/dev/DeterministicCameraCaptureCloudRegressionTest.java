package net.minecraft.client.dev;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Regression coverage for cloud capture promotion ordering. */
class DeterministicCameraCaptureCloudRegressionTest {
    @Test
    void cloudPromotionRequiresThePresentedSubmissionIdentity() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java")
        );

        int cloudGuard = source.indexOf("if (!requiredProducerReceipt && !CLOUD_SCENARIO.isEmpty())");
        assertTrue(cloudGuard >= 0, "cloud captures must have a dedicated producer guard");
        String guard = source.substring(cloudGuard, Math.min(source.length(), cloudGuard + 1_000));
        assertTrue(guard.contains("diagnostic.gameplayFrameId() == gameplayFrameId"));
        assertTrue(guard.contains("diagnostic.submissionId() == submissionId"));
        assertTrue(guard.contains("diagnostic.quads() > 0"));
        assertTrue(
            source.indexOf("if (!requiredProducerReceipt)", cloudGuard) > cloudGuard,
            "cloud identity validation must precede generic producer rejection"
        );
    }
}
