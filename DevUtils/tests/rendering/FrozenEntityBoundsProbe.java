import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual compiled Frozen world-AABB frustum methods; numerical evidence only. */
public class FrozenEntityBoundsProbe {
    public static void main(String[] args) {
        System.out.println("# mode camera xyz absolute min xyz max xyz visible; identity camera/upward light; distance 40, voxel 8");
        for (String mode : new String[]{"true","false","reversed"}) {
            for (double camera : new double[]{0,1000000.125,29999999.375}) {
                Frustum selected = switch (mode) {
                    case "false" -> new BoxCullingFrustum(new BoxCuller(40));
                    case "reversed" -> new SafeZoneCullingFrustum(new Matrix4f(),new Matrix4f(),
                        new Vector3f(0,1,0),new BoxCuller(8),new BoxCuller(40));
                    default -> new AdvancedShadowCullingFrustum(new Matrix4f(),new Matrix4f(),
                        new Vector3f(0,1,0),new BoxCuller(40));
                };
                selected.prepare(camera,camera,camera);
                for (double[] b : new double[][]{
                    {-0.5,-0.5,-0.5,0.5,0.5,0.5},
                    {40,-0.5,-0.5,41,0.5,0.5},
                    {40+1e-10,-0.5,-0.5,41,0.5,0.5},
                    {40+1e-7,-0.5,-0.5,41,0.5,0.5},
                    {-41,-0.5,-0.5,-40,0.5,0.5},
                    {-41,-0.5,-0.5,-40-1e-7,0.5,0.5},
                    {-0.5,40,-0.5,0.5,41,0.5},
                    {-0.5,40+1e-10,-0.5,0.5,41,0.5},
                    {-0.5,40+1e-7,-0.5,0.5,41,0.5},
                    {7.5,7.5,7.5,8.5,8.5,8.5},
                    {8.5,8.5,8.5,9.5,9.5,9.5},
                    {-50,-50,-50,50,50,50},
                }) {
                    AABB world = new AABB(camera+b[0],camera+b[1],camera+b[2],
                        camera+b[3],camera+b[4],camera+b[5]);
                    System.out.print(mode+"\t"+camera+"\t"+camera+"\t"+camera);
                    for (double value : new double[]{world.minX,world.minY,world.minZ,world.maxX,world.maxY,world.maxZ})
                        System.out.print("\t"+value);
                    System.out.println("\t"+selected.isVisible(world));
                }
            }
        }
    }
}
