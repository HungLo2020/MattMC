package net.vulkanic.gui;

import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RustGalPanoramaGeometryTest {
    @Test
    void fullscreenTriangleHasNoItemGuardOffsetAndPreservesFractionalExtent() {
        var projection = new VulkanicGalBridge.GuiProjectionRecord(1280.0F / 3, 720.0F / 3);
        var vertices = RustGalPanoramaRenderer.panoramaVertices(10, 0, projection);
        assertEquals(0, vertices.get(0).position()[0]);
        assertEquals(projection.height(), vertices.get(0).position()[1]);
        assertEquals(2 * projection.width(), vertices.get(1).position()[0]);
        assertEquals(0, vertices.get(2).position()[0]);
        assertEquals(-projection.height(), vertices.get(2).position()[1]);
    }

    @Test
    void viewRaysMatchFrozenPerspectiveAtEveryGuiScale() {
        for (int scale : new int[] {1, 2, 3, 4}) {
            float width = 1280.0F / scale, height = 719.0F / scale;
            var vertices = RustGalPanoramaRenderer.panoramaVertices(10, 17,
                new VulkanicGalBridge.GuiProjectionRecord(width, height));
            var inverseProjection = new Matrix4f().perspective(
                (float)Math.toRadians(85), 1280.0F / 719, 0.05F, 10.0F).invert();
            var rotation = new Matrix3f(new Matrix4f().rotationX((float)Math.PI)
                .rotateX((float)Math.toRadians(10)).rotateY((float)Math.toRadians(17))).transpose();
            float[][] clips = {{-1, -1}, {3, -1}, {-1, 3}};
            for (int i = 0; i < 3; i++) {
                var view = new Vector4f(clips[i][0], clips[i][1], 1, 1).mul(inverseProjection);
                var expected = new Vector3f(view.x, view.y, view.z).normalize().mul(rotation).normalize();
                var vertex = vertices.get(i);
                var actual = new Vector3f(vertex.position()[2], vertex.localUv()[0], vertex.localUv()[1]).normalize();
                assertEquals(expected.x, actual.x, 0.000001);
                assertEquals(expected.y, actual.y, 0.000001);
                assertEquals(expected.z, actual.z, 0.000001);
            }
        }
    }
}
