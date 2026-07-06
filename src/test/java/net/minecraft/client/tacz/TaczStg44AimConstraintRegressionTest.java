package net.minecraft.client.tacz;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczStg44AimConstraintRegressionTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    private static String readSource(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    void firstPersonAdsAppliesAnimationConstraintAfterSightPositioning() throws IOException {
        String renderer = readSource(PROJECT_ROOT.resolve(
            "src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"
        ));

        int positioningCall = renderer.indexOf("this.geometry.applyFirstPersonPositioning(");
        int constraintCall = renderer.indexOf("this.geometry.applyAnimationConstraintTransform(");
        int implementation = renderer.indexOf("void applyAnimationConstraintTransform(PoseStack poseStack, AnimationPose animationPose, float weight)");

        assertTrue(positioningCall >= 0, "First-person TaCZ rendering must still apply sight positioning");
        assertTrue(constraintCall > positioningCall,
            "Animation constraint compensation must run after ADS sight positioning, matching upstream TaCZ");
        assertTrue(implementation >= 0,
            "The port must keep TaCZ's animation constraint compensation implementation");
        assertTrue(renderer.contains("aimProgress * (1.0F - refitProgress)"),
            "Constraint compensation should fade in only with ADS and fade out while the refit screen opens");
    }

    @Test
    void stg44IdleAnimationKeepsConstraintCoefficientsForMovedGunBody() throws IOException {
        JsonObject root = JsonParser.parseString(readSource(PROJECT_ROOT.resolve(
            "src/main/resources/assets/minecraft/animations/stg44.animation.json"
        ))).getAsJsonObject();

        JsonObject bones = root.getAsJsonObject("animations")
            .getAsJsonObject("static_idle")
            .getAsJsonObject("bones");

        assertVector(bones.getAsJsonObject("stg44").getAsJsonArray("position"), 0.0F, -2.0F, 2.0F);
        assertVector(bones.getAsJsonObject("constraint").getAsJsonArray("position"), 0.2F, 0.2F, 0.4F);
        assertVector(bones.getAsJsonObject("constraint").getAsJsonArray("rotation"), 0.2F, 0.1F, 0.2F);
    }

    private static void assertVector(JsonArray actual, float x, float y, float z) {
        assertEquals(x, actual.get(0).getAsFloat(), 0.0001F);
        assertEquals(y, actual.get(1).getAsFloat(), 0.0001F);
        assertEquals(z, actual.get(2).getAsFloat(), 0.0001F);
    }
}
