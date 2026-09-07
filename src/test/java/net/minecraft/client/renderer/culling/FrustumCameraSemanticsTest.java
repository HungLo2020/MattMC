package net.minecraft.client.renderer.culling;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrustumCameraSemanticsTest {
    @Test void rotatedNonOriginCameraKeepsForwardParticlesAndRejectsBehindCamera() {
        Vec3 origin = new Vec3(150.5, 101.62, 530.5);
        Vec3 direction = Vec3.directionFromRotation(10, 105).normalize();
        Matrix4f view = new Matrix4f().lookAlong(new Vector3f((float)direction.x, (float)direction.y,
            (float)direction.z), new Vector3f(0, 1, 0));
        Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(70), 16F/9F, 0.05F, 256);
        Frustum actual = Frustum.forCamera(view, projection, origin);
        // Frozen's ordinary culling setup: projection * view, then camera origin.
        Frustum baseline = new Frustum(view, projection);
        baseline.prepare(origin.x, origin.y, origin.z);
        for (double distance : new double[]{0.1, 3, 20, 100, -3, 300}) {
            Vec3 point = origin.add(direction.scale(distance));
            assertEquals(baseline.pointInFrustum(point.x, point.y, point.z),
                actual.pointInFrustum(point.x, point.y, point.z));
            assertEquals(distance > 0 && distance < 256, actual.pointInFrustum(point.x, point.y, point.z));
        }
        Vec3 particle = origin.add(direction.scale(3));
        assertFalse(new Frustum(projection, view).pointInFrustum(particle.x, particle.y, particle.z),
            "regression reproducer: reversed matrices and omitted origin cull the visible particle");
    }
}
