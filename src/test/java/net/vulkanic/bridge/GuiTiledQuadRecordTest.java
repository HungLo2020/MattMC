package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiTiledQuadRecordTest {
    @Test
    void fourKBackgroundRemainsOneImmutableSemanticCommand() {
        int[] bounds = {0, 0, 3840, 2160};
        float[] uv = {0, 0, 1, 1};
        float[] pose = {1, 0, 0, 1, 0, 0};
        int[] clip = {2, 3, 3838, 2157};
        var command = new VulkanicGalBridge.GuiTiledQuadRecord(
            5, 12L, bounds, 32, 32, uv, pose, 0, -1, 3840, 2160, 0, 1, clip);
        bounds[2] = 1;
        uv[2] = 0;
        pose[0] = 0;
        clip[0] = 99;
        command.bounds()[2] = 9;
        command.uv()[2] = 9;
        command.pose()[0] = 9;
        command.clip()[0] = 9;
        assertArrayEquals(new int[] {0, 0, 3840, 2160}, command.bounds());
        assertArrayEquals(new float[] {0, 0, 1, 1}, command.uv());
        assertArrayEquals(new float[] {1, 0, 0, 1, 0, 0}, command.pose());
        assertArrayEquals(new int[] {2, 3, 3838, 2157}, command.clip());
        var sequenced = command.withSequence(73);
        assertEquals(0, command.sequence());
        assertEquals(73, sequenced.sequence());
        assertEquals(32, sequenced.tileWidth());
        assertArrayEquals(command.bounds(), sequenced.bounds());
        assertArrayEquals(command.uv(), sequenced.uv());
        assertArrayEquals(command.pose(), sequenced.pose());
        assertArrayEquals(command.clip(), sequenced.clip());
    }
}
