import java.util.Arrays;
import net.math.Axis;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.uniforms.transforms.SmoothedVec2f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector4f;

/** Frozen/JOML numerical probe; no Minecraft client, renderer or GPU state. */
public class FrozenBuiltinUniformProbe {
    public static void main(String[] args) {
        Matrix4f identity = new Matrix4f();
        Matrix4f camera = new Matrix4f().translate(10,20,30)
            .rotate(Axis.YP.rotationDegrees(37)).rotate(Axis.XP.rotationDegrees(-11));
        System.out.println("camera=" + Arrays.toString(camera.get(new float[16])));
        for (float time : new float[] {0,0.25f,0.5f,0.75f,0.3f}) {
            celestial("identity",identity,time,-25);
            celestial("camera",camera,time,-25);
        }
        Vector4f end = new Matrix4f(camera).rotate(Axis.YP.rotationDegrees(180-(-47)))
            .rotate(Axis.XP.rotationDegrees(-90-13)).transform(new Vector4f(0,100,0,0));
        print("end-flash",end);
        for (float halfLife : new float[] {10,3,0}) {
            SystemTimeUniforms.TIMER.reset();
            FrameUpdateNotifier notifier = new FrameUpdateNotifier();
            Vector2i raw = new Vector2i(0,240);
            SmoothedVec2f smoother = new SmoothedVec2f(halfLife,halfLife,()->raw,notifier);
            long time=0;
            int[][] values={{0,240},{240,0},{240,0},{240,0},{16,224}};
            long[] millis={0,300,300,0,100};
            for (int i=0;i<values.length;i++) {
                raw.set(values[i][0],values[i][1]);time+=millis[i]*1_000_000;
                SystemTimeUniforms.TIMER.beginFrame(time);notifier.onNewFrame();
                Vector2f smoothed=smoother.get();
                System.out.println("eye\t"+halfLife+"\t"+i+"\t"+(int)smoothed.x+","+(int)smoothed.y);
            }
        }
    }
    private static void celestial(String name,Matrix4f view,float skyAngle,float sunPath) {
        Matrix4f matrix=new Matrix4f(view).rotate(Axis.YP.rotationDegrees(-90))
            .rotate(Axis.ZP.rotationDegrees(sunPath)).rotate(Axis.XP.rotationDegrees(skyAngle*360));
        print("sun-"+name+"-"+skyAngle,matrix.transform(new Vector4f(0,100,0,0)));
    }
    private static void print(String name,Vector4f value) {
        System.out.println(name+"\t"+value.x+","+value.y+","+value.z);
    }
}
