import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Read-only numerical probe against compiled Frozen frustum implementations.
 * The private ShadowRenderer selection recipe is reproduced here with explicit
 * CPU inputs; this does not invoke ShadowRenderer or prove gameplay parity.
 * Run with Frozen's compiled classes and JOML on the classpath.
 */
public class FrozenShadowCullingProbe {
    public static void main(String[] args) {
        System.out.println("# B: relative bounds; F: mode terrainMul entityMul renderBlocks userChunks kind visibility bits (one per B)");
        float[][] bounds = {
            {-0.5f,-0.5f,-0.5f, 0.5f,0.5f,0.5f},
            {-0.5f,2,-0.5f, 0.5f,3,0.5f},
            {-0.5f,-3,-0.5f, 0.5f,-2,0.5f},
            {2,-0.5f,-0.5f, 3,0.5f,0.5f},
            {-0.5f,-0.5f,2, 0.5f,0.5f,3},
            {-0.5f,15,-0.5f, 0.5f,17,0.5f},
            {-0.5f,39,-0.5f, 0.5f,41,0.5f},
            {-0.5f,79,-0.5f, 0.5f,81,0.5f},
            {-0.5f,159,-0.5f, 0.5f,161,0.5f},
            {-0.5f,160.01f,-0.5f, 0.5f,161,0.5f},
            {40,-0.5f,-0.5f, 41,0.5f,0.5f},
            {40.001f,-0.5f,-0.5f, 41,0.5f,0.5f},
            {-41,-0.5f,-0.5f, -40,0.5f,0.5f},
            {-41,-0.5f,-0.5f, -40.001f,0.5f,0.5f},
            {10,10,10, 11,11,11},
            {20,20,20, 21,21,21},
            {-100,-100,-100, 100,100,100},
        };
        for (float[] b : bounds) {
            System.out.print("B");
            for (float value : b) System.out.print("\t"+value);
            System.out.println();
        }
        for (String mode : new String[] {"true","false","reversed"}) {
            for (float terrain : new float[] {-1,0,0.5f,1,2,3}) {
                for (float entity : new float[] {-0.5f,0,0.25f,1,2}) {
                    for (int user : new int[] {0,2,32}) {
                        for (String kind : new String[] {"terrain","entity"}) {
                            float multiplier = terrain;
                            if (kind.equals("entity") && entity >= 0 && entity != 1) multiplier *= entity;
                            Frustum selected = select(mode,multiplier,160,user);
                            selected.prepare(0,0,0);
                            var frustum = (net.sodium.client.render.viewport.frustum.Frustum) selected;
                            System.out.print("F\t"+mode+"\t"+terrain+"\t"+entity+"\t160.0\t"+user+"\t"+kind+"\t");
                            for (float[] b : bounds) {
                                boolean visible = frustum.testAab(b[0],b[1],b[2],b[3],b[4],b[5]);
                                System.out.print(visible ? '1' : '0');
                            }
                            System.out.println();
                        }
                    }
                }
            }
        }
    }

    private static Frustum select(String mode,float multiplier,float normal,int user) {
        if (mode.equals("false")) {
            double distance = 80.0f * multiplier;
            return distance <= 0 || distance > normal ? new NonCullingFrustum()
                : new BoxCullingFrustum(new BoxCuller(distance));
        }
        boolean safe = mode.equals("reversed");
        if (safe && multiplier < 0) multiplier = 1;
        double distance = (safe ? 16.0f : 80.0f) * multiplier;
        if (multiplier < 0) distance = user * 16;
        BoxCuller box = !safe && distance >= normal ? null : new BoxCuller(distance);
        // Identity camera, sky angle zero, sun path zero: light points up.
        return safe ? new SafeZoneCullingFrustum(new Matrix4f(),new Matrix4f(),
                new Vector3f(0,1,0),box,new BoxCuller(80.0f * multiplier))
            : new AdvancedShadowCullingFrustum(new Matrix4f(),new Matrix4f(),new Vector3f(0,1,0),box);
    }
}
