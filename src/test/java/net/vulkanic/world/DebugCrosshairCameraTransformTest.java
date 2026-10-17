package net.vulkanic.world;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Camera;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class DebugCrosshairCameraTransformTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @ParameterizedTest(name = "yaw={0}, pitch={1}, scale={2}, camera effects={3}")
    @CsvSource({"0, 0, 1, false", "90, 0, 1, false", "180, 0, 3, false",
        "-90, 0, 3, false", "37, 60, 2, false", "-125, -60, 2, false",
        "37, 60, 2, true", "-125, -60, 2, true"})
    @SuppressWarnings("unchecked")
    void queuedAxesRetainFrozensCameraSpaceOrientation(float yaw, float pitch, float guiScale,
                                                      boolean cameraEffects) throws Exception {
        Camera camera = new Camera();
        var rotation = Camera.class.getDeclaredMethod("setRotation", float.class, float.class);
        rotation.setAccessible(true);
        rotation.invoke(camera, yaw, pitch);
        Matrix4f view = new Matrix4f();
        if (cameraEffects) view.translate(0.02F, -0.03F, 0.01F).rotateZ(0.04F).rotateX(-0.02F);
        view.rotate(camera.rotation().conjugate(new Quaternionf()));

        Field width = field("pendingViewportWidth"), height = field("pendingViewportHeight");
        float[] pendingView = (float[]) field("PENDING_VIEW").get(null);
        var pending = (List<VulkanicGalBridge.WorldLineSegmentRecord>) field("PENDING_SEGMENTS").get(null);
        int oldWidth = width.getInt(null), oldHeight = height.getInt(null), checkpoint = pending.size();
        float[] oldView = pendingView.clone();
        try {
            width.setInt(null, 1280);
            height.setInt(null, 720);
            view.get(pendingView);
            assertTrue(RustGalWorldPrimitiveRenderer.enqueueThreeDimensionalDebugCrosshair(camera, guiScale));
            List<VulkanicGalBridge.WorldLineSegmentRecord> segments = pending.subList(checkpoint, pending.size());
            assertEquals(6, segments.size());

            // Frozen applies pitch/yaw to signed world axes in camera space.
            // Compute their directions independently of the producer's matrices.
            float scale = 0.01F * guiScale;
            float sinYaw = (float) Math.sin(Math.toRadians(yaw)), cosYaw = (float) Math.cos(Math.toRadians(yaw));
            float sinPitch = (float) Math.sin(Math.toRadians(pitch)), cosPitch = (float) Math.cos(Math.toRadians(pitch));
            Vector3f[] directions = {
                new Vector3f(-scale * cosYaw, -scale * sinPitch * sinYaw, scale * cosPitch * sinYaw),
                new Vector3f(0, scale * cosPitch, scale * sinPitch),
                new Vector3f(-scale * sinYaw, scale * sinPitch * cosYaw, -scale * cosPitch * cosYaw)
            };
            int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF7F7FFF};
            for (int index = 0; index < segments.size(); index++) {
                var segment = segments.get(index);
                Vector3f start = view.transformPosition(segment.startX(), segment.startY(), segment.startZ(), new Vector3f());
                Vector3f end = view.transformPosition(segment.endX(), segment.endY(), segment.endZ(), new Vector3f());
                assertVector(new Vector3f(0, 0, -1), start);
                assertVector(new Vector3f(directions[index % 3]).add(0, 0, -1), end);
                assertTrue(end.z() < 0, "crosshair must remain in front of the camera");
                assertEquals(index < 3 ? 0xFFFFFFFF : colors[index % 3], segment.colorArgb());
                assertEquals(index < 3 ? 2.0F : 4.0F, segment.lineWidth());
                assertEquals(1280, segment.viewportWidth());
                assertEquals(720, segment.viewportHeight());
            }
        } finally {
            pending.subList(checkpoint, pending.size()).clear();
            System.arraycopy(oldView, 0, pendingView, 0, oldView.length);
            width.setInt(null, oldWidth);
            height.setInt(null, oldHeight);
        }
    }

    private static Field field(String name) throws Exception {
        Field field = RustGalWorldPrimitiveRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x(), actual.x(), 0.000001F);
        assertEquals(expected.y(), actual.y(), 0.000001F);
        assertEquals(expected.z(), actual.z(), 0.000001F);
    }
}
