package net.minecraft.client.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditSourceReceiptTest {
    @TempDir Path directory;

    private void write(String filename, long frame, String route, long meshes) throws Exception {
        Files.writeString(directory.resolve(filename), "{\"route\":\"" + route
            + "\",\"frame_id\":" + frame + ",\"mesh_instances\":" + meshes + "}");
    }

    @Test void steadyStateLatestReceiptRequiresTheExactFrameAndVisibleSourceWork() throws Exception {
        write("selected-source-execution-latest.json", 2000, "rust-native-selected-source", 1);
        assertTrue(DeterministicCameraCapture.hasSourceExecutionReceiptForFrame(directory, 2000));
        assertFalse(DeterministicCameraCapture.hasSourceExecutionReceiptForFrame(directory, 2001));
        write("selected-source-execution-latest.json", 2000, "vanilla", 1);
        assertFalse(DeterministicCameraCapture.hasSourceExecutionReceiptForFrame(directory, 2000));
        write("selected-source-execution-latest.json", 2000, "rust-native-selected-source", 0);
        assertFalse(DeterministicCameraCapture.hasSourceExecutionReceiptForFrame(directory, 2000));
    }

    @Test void activationReceiptRemainsUsableWhenLatestHasAdvanced() throws Exception {
        write("selected-source-execution-latest.json", 2001, "rust-native-selected-source", 1);
        write("selected-source-execution-frame-2000.json", 2000, "rust-native-selected-source", 1);
        assertTrue(DeterministicCameraCapture.hasSourceExecutionReceiptForFrame(directory, 2000));
        assertFalse(DeterministicCameraCapture.hasSourceExecutionReceiptForFrame(directory, 1999));
    }
}
