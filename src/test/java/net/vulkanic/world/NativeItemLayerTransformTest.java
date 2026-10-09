package net.vulkanic.world;

import java.util.Random;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeItemLayerTransformTest {
    @Test void nativePosesMatchOriginalJomlForMirroringScaleAndAngles() {
        Random random=new Random(734829);
        for (int i=0;i<1500;i++) {
            Vector3f scale=i%4==0 ? new Vector3f(i%8==0?-2:2) : new Vector3f(component(random),component(random),component(random));
            ItemTransform transform=new ItemTransform(new Vector3f(angle(random),angle(random),angle(random)),
                new Vector3f(component(random),component(random),component(random)),scale);
            for (boolean left:new boolean[]{false,true}) {
                var expected=new PoseStack.Pose();transform.apply(left,expected);
                var actual=NativeItemLayerTransform.capture(transform,left);
                assertNotNull(actual);
                assertArrayEquals(expected.pose().get(new float[16]),actual.modelTransform(),"model case "+i+" left="+left);
                assertArrayEquals(expected.normal().get(new float[9]),actual.normalTransform(),"normal case "+i+" left="+left);
                assertEquals(expected.trustedNormals,actual.trustedNormals());
            }
        }
    }
    @Test void mutableVectorsCreateIndependentSnapshotsAndCapturedOwnersSurviveEviction() {
        var translation=new Vector3f(1,2,3);
        var transform=new ItemTransform(new Vector3f(),translation,new Vector3f(1));
        var old=NativeItemLayerTransform.capture(transform,false);var expected=old.modelTransform();
        assertSame(old,NativeItemLayerTransform.capture(transform,false));
        translation.x=17;
        var fresh=NativeItemLayerTransform.capture(transform,false);
        assertNotEquals(old.ownerAddress(),fresh.ownerAddress());
        for(int i=0;i<300;i++) NativeItemLayerTransform.capture(new ItemTransform(new Vector3f(),new Vector3f(i),new Vector3f(1)),false);
        System.gc();assertArrayEquals(expected,old.modelTransform());
        assertEquals(16.5F,fresh.modelTransform()[12]);
        old.modelTransform()[12]=123;assertArrayEquals(expected,old.modelTransform());
    }
    @Test void customVectorsDeclineBeforeCallbacksAndNoTransformIgnoresItsVectors() {
        var custom=new Vector3f() {@Override public float x(){throw new AssertionError("callback");}};
        assertNull(NativeItemLayerTransform.capture(new ItemTransform(custom,new Vector3f(),new Vector3f(1)),false));
        var no=NativeItemLayerTransform.capture(ItemTransform.NO_TRANSFORM,true);
        var expected=new PoseStack.Pose();ItemTransform.NO_TRANSFORM.apply(true,expected);
        assertArrayEquals(expected.pose().get(new float[16]),no.modelTransform());
        assertNull(NativeItemLayerTransform.capture(new ItemTransform(new Vector3f(),new Vector3f(),new Vector3f(0,1,1)),false));
    }
    private static float angle(Random r){return r.nextFloat()*720-360;}
    private static float component(Random r){return (r.nextFloat()*8-4)+0.0001F;}
}
