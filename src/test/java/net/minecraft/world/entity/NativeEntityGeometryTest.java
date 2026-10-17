package net.minecraft.world.entity;

import java.lang.foreign.Arena;
import java.util.Random;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeEntityGeometryTest {
    static AABB prepared(AABB bounds,double x,double y,double z) {
        AABB result=bounds.inflate(0.5);
        return result.hasNaN() || result.getSize()==0.0 ? new AABB(x-2,y-2,z-2,x+2,y+2,z+2) : result;
    }
    static void sameBounds(AABB expected,AABB actual) {
        assertEquals(expected.minX,actual.minX);assertEquals(expected.minY,actual.minY);assertEquals(expected.minZ,actual.minZ);
        assertEquals(expected.maxX,actual.maxX);assertEquals(expected.maxY,actual.maxY);assertEquals(expected.maxZ,actual.maxZ);
    }
    @Test void primaryMovementAndImmutableCapturePreserveJavaNumericEdges() {
        var world=new NativeEntityGeometry();
        try(var scope=Arena.ofConfined()) {
            var body=world.create(scope,0,0,0,new AABB(-1,-1,-1,1,1,1));
            assertEquals(0,body.move(-0.0,0.0,0.0));
            var original=body.cullingBounds();
            double[] edges={-0.1,-16.01,16.0,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,-0.0};
            for(double x:edges) {
                body.move(x,x,x);
                assertEquals(Mth.floor(x),body.blockX());assertEquals(Mth.floor(x)>>4,body.chunkX());
                assertSame(original,body.cullingBounds(),"Position alone must not translate physical bounds");
            }
            for(AABB bounds:new AABB[]{new AABB(1,2,3,-4,-5,-6),new AABB(Double.NaN,0,0,1,1,1),
                new AABB(Double.NEGATIVE_INFINITY,0,0,Double.POSITIVE_INFINITY,1,1)}) {
                body.move(10,20,30);body.bounds(bounds);
                sameBounds(prepared(bounds,10,20,30),body.cullingBounds());
            }
            sameBounds(new AABB(-1.5,-1.5,-1.5,1.5,1.5,1.5),original);
        }
        assertEquals(0,world.count());
    }
    @Test void retainedQueryMatchesOrdinaryJomlAcrossCamerasBoundsAndMutations() {
        var world=new NativeEntityGeometry();var random=new Random(0x4547454f);
        try(var scope=Arena.ofConfined()) {
            var bodies=new NativeEntityGeometry.Body[80];var boxes=new AABB[bodies.length];
            for(int i=0;i<bodies.length;i++) {boxes[i]=new AABB(0,0,0,1,1,1);bodies[i]=world.create(scope,0,0,0,boxes[i]);}
            for(int pass=0;pass<100;pass++) {
                var projection=new Matrix4f().perspective((float)Math.toRadians(35+random.nextInt(90)),1.7F,0.05F,256);
                var view=new Matrix4f().rotateXYZ(random.nextFloat()*6,random.nextFloat()*6,random.nextFloat()*6);
                var frustum=new Frustum(view,projection);double x=random.nextDouble()*6e7-3e7,y=random.nextDouble()*200,z=random.nextDouble()*6e7-3e7;
                frustum.prepare(x,y,z);
                for(int i=0;i<bodies.length;i++) {
                    double a=x+random.nextGaussian()*150,b=y+random.nextGaussian()*150,c=z+random.nextGaussian()*150;
                    boxes[i]=i%17==0 ? new AABB(Double.NaN,b,c,a+2,b+2,c+2) : new AABB(a,b,c,a+random.nextDouble()*4,b+random.nextDouble()*4,c+random.nextDouble()*4);
                    bodies[i].move(a,b,c);bodies[i].bounds(boxes[i]);
                }
                for(int i=0;i<bodies.length;i++) assertEquals(frustum.isVisible(bodies[i].cullingBounds()),frustum.isVisibleEntity(bodies[i]),"pass="+pass+" component="+i);
                // Another camera's query must invalidate cached flags, even without a mutation.
                var other=new Frustum(new Matrix4f(),new Matrix4f());other.prepare(x+20,y,z);
                for(var body:bodies)assertEquals(other.isVisible(body.cullingBounds()),other.isVisibleEntity(body));
                for(var body:bodies)assertEquals(frustum.isVisible(body.cullingBounds()),frustum.isVisibleEntity(body));
                frustum.offset(4);
                for(var body:bodies)assertEquals(frustum.isVisible(body.cullingBounds()),frustum.isVisibleEntity(body));
            }
        }
        assertEquals(0,world.count());
    }
    @Test void transferAndExplicitCloseKeepStableViewsAndReclaimMembership() {
        var a=new NativeEntityGeometry();var b=new NativeEntityGeometry();NativeEntityGeometry.Body body;
        try(var scope=Arena.ofConfined()) {
            body=a.create(scope,1,2,3,new AABB(0,0,0,2,3,4));var capture=body.cullingBounds();
            body.transfer(b);assertSame(capture,body.cullingBounds());assertSame(b,body.world());
            assertEquals(0,a.count());assertEquals(1,b.count());
            body.transfer(a);assertEquals(1,a.count());assertEquals(0,b.count());
        }
        assertEquals(0,a.count());assertThrows(IllegalStateException.class,()->body.move(1,2,3));
    }
}
