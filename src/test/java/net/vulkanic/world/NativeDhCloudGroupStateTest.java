package net.vulkanic.world;

import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhCloudGroupStateTest {
    // Frozen CloudRenderHandler arithmetic and its actual Vec3 normalizer. This
    // object model intentionally allocates corners, unlike the native producer.
    private static final class Oracle {
        final int width, ox, oz;
        long last;
        float delta;
        float x,y,z;
        Oracle(int width,int ox,int oz,long now) { this.width=width;this.ox=ox;this.oz=oz;last=now; }
        boolean prepare(long now,float speed,double cx,double cy,double cz,
                float lx,float ly,float lz,int radius,int height) {
            float dt=(now-last)/1000.0f;last=now;
            delta-=speed*dt;delta%=width;
            int ix=(int)cx,iz=(int)cz;
            if(ix<0)ix-=width;if(iz<0)iz-=width;
            float offx=(ix/width)*width,offz=(iz/width)*width;
            x=delta+ox*width+offx+width/2;
            y=height+200;
            z=0.0f+oz*width+offz+width/2;
            if(ox>=-1&&ox<=1&&oz>=-1&&oz<=1)return true;
            var corners=new Vec3d[]{new Vec3d(x,y,z),new Vec3d(x,y,z+width),
                    new Vec3d(x+width,y,z),new Vec3d(x+width,y,z+width)};
            var camera=new Vec3d(cx,cy,cz);
            var look=new Vec3f(lx,ly,lz);look.normalize();
            double distance=radius*16*1.5;
            boolean outside=true,behind=true;
            for(var corner:corners) {
                var c=new Vec3d(corner);c.y=0;
                var p=new Vec3d(camera);p.y=0;
                if(c.getDistance(p)<=distance)outside=false;
                var to=new Vec3f((float)(corner.x-cx),(float)(corner.y-cy),(float)(corner.z-cz));
                to.normalize();if(look.dotProduct(to)>0)behind=false;
            }
            return !(outside||behind);
        }
    }

    @Test void matchesFrozenMotionAndCullingAcrossHistoriesAndNumericBoundaries() {
        var random=new Random(0xd4c10dL);
        double[] edge={-2048,-0.5,0,2048,-30_000_000,30_000_000,Double.NaN,
                Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Integer.MIN_VALUE,Integer.MAX_VALUE};
        for(int width:new int[]{128,2048,4096,2_147_483_520}) {
            for(int ox=-5;ox<=5;ox++)for(int oz=-5;oz<=5;oz++) {
                long now=Long.MAX_VALUE-1000;
                var nativeState=new NativeDhCloudGroupState(width,ox,oz,now);
                var reference=new Oracle(width,ox,oz,now);
                for(int frame=0;frame<32;frame++) {
                    now+=frame%7==0?-137:837;
                    // A skipped disabled interval must not advance either clock.
                    if(frame%5==0)continue;
                    double x=frame<edge.length?edge[frame]:random.nextDouble()*60_000_000-30_000_000;
                    double z=frame<edge.length?edge[edge.length-1-frame]:random.nextDouble()*60_000_000-30_000_000;
                    double y=random.nextDouble()*4096-2048;
                    float lx=random.nextFloat()*2-1,ly=random.nextFloat()*2-1,lz=random.nextFloat()*2-1;
                    if(frame==12){lx=0;ly=0;lz=0;}
                    if(frame==13)lx=Float.NaN;
                    int radius=frame==14?Integer.MAX_VALUE:random.nextInt(1025);
                    int height=frame==15?Integer.MAX_VALUE:random.nextInt(1024)-512;
                    boolean expected=reference.prepare(now,6,x,y,z,lx,ly,lz,radius,height);
                    assertEquals(expected,nativeState.prepare(now,6,x,y,z,lx,ly,lz,radius,height),
                            "admission width="+width+" offset="+ox+","+oz+" frame="+frame);
                    assertEquals(Float.floatToIntBits(reference.x),Float.floatToIntBits((float)nativeState.originX()));
                    assertEquals(Float.floatToIntBits(reference.y),Float.floatToIntBits((float)nativeState.originY()));
                    assertEquals(Float.floatToIntBits(reference.z),Float.floatToIntBits((float)nativeState.originZ()));
                }
            }
        }
    }

    @Test void preservesColorHistoryAndRejectsLayoutsOutsideTheBuiltinGrid() {
        var state=new NativeDhCloudGroupState(2048,0,0,0);
        assertFalse(state.colorChanged(0xffffffff));
        assertTrue(state.colorChanged(0xff123456));
        assertFalse(state.colorChanged(0xff123456));
        assertTrue(state.colorChanged(0xffffffff));
        assertThrows(IllegalArgumentException.class,()->new NativeDhCloudGroupState(0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new NativeDhCloudGroupState(2048,6,0,0));
    }
}
