package net.caffeinemc.mods.sodium.client.hooks;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.SimpleFrustum;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Vector3d;

import java.lang.reflect.Field;

/**
 * Sodium's hook for Frustum viewport creation.
 * Accesses Frustum fields using reflection.
 */
public class SodiumFrustumHook {
    private static final SodiumFrustumHook INSTANCE = new SodiumFrustumHook();
    
    private static Field camXField;
    private static Field camYField;
    private static Field camZField;
    private static Field intersectionField;
    
    static {
        try {
            camXField = Frustum.class.getDeclaredField("camX");
            camYField = Frustum.class.getDeclaredField("camY");
            camZField = Frustum.class.getDeclaredField("camZ");
            intersectionField = Frustum.class.getDeclaredField("intersection");
            
            camXField.setAccessible(true);
            camYField.setAccessible(true);
            camZField.setAccessible(true);
            intersectionField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to initialize SodiumFrustumHook", e);
        }
    }

    private SodiumFrustumHook() {
    }

    public static SodiumFrustumHook getInstance() {
        return INSTANCE;
    }

    /**
     * Create a viewport from a Frustum instance.
     */
    public static Viewport createViewport(Frustum frustum) {
        try {
            double camX = camXField.getDouble(frustum);
            double camY = camYField.getDouble(frustum);
            double camZ = camZField.getDouble(frustum);
            org.joml.FrustumIntersection intersection = (org.joml.FrustumIntersection) intersectionField.get(frustum);
            
            return new Viewport(new SimpleFrustum(intersection), new Vector3d(camX, camY, camZ));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to create viewport from frustum", e);
        }
    }
}
