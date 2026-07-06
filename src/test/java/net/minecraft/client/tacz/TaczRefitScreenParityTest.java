package net.minecraft.client.tacz;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczRefitScreenParityTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath)).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    void refitScreenDoesNotUseVanillaMenuBackgroundOrBlur() throws IOException {
        String screen = readSource("src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java");

        assertTrue(screen.contains("public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {\n\t}"),
            "TaCZ refit should be an overlay screen and must not trigger vanilla menu blur/background rendering");
        assertTrue(screen.contains("public boolean isInGameUi() {\n\t\treturn true;\n\t}"),
            "TaCZ refit should identify itself as in-game UI");
        assertFalse(screen.contains("renderBlurredBackground("),
            "TaCZ refit should not request the vanilla blurred background");
        assertFalse(screen.contains("renderTransparentBackground("),
            "TaCZ refit should not dim the world like a vanilla in-game menu");
    }

    @Test
    void firstPersonArmsAreSuppressedWhileRefitIsVisible() throws IOException {
        String renderer = readSource("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java");

        int guard = renderer.indexOf("if (this.shouldRenderFirstPersonArms(itemDisplayContext))");
        int submit = renderer.indexOf("this.submitFirstPersonArms(itemDisplayContext, poseStack, submitNodeCollector, i, animationPose);");
        int screenCheck = renderer.indexOf("minecraft.screen instanceof TaczGunRefitScreen");
        int progressCheck = renderer.indexOf("TaczRefitTransform.openingProgress() <= 0.0F");

        assertTrue(guard >= 0, "First-person arm submission should be gated");
        assertTrue(submit > guard, "The gated branch should be the only first-person arm submission path");
        assertTrue(screenCheck >= 0, "Arms should be hidden while the refit screen is currently open");
        assertTrue(progressCheck > screenCheck, "Arms should stay hidden while the refit open/close transform is still visible");
    }
}
