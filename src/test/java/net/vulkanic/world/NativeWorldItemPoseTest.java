package net.vulkanic.world;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeWorldItemPoseTest {
    @Test void both_operation_orders_match_original_parent_and_independent_normals() {
        var random=new java.util.Random(2476123);
        for(int i=0;i<800;i++) {
            ItemTransform transform=i%8==0?ItemTransform.NO_TRANSFORM:new ItemTransform(
                new Vector3f(random.nextFloat()*720-360,random.nextFloat()*720-360,random.nextFloat()*720-360),
                new Vector3f(random.nextFloat()*8-4,random.nextFloat()*8-4,random.nextFloat()*8-4),
                new Vector3f(random.nextFloat()+0.01F,random.nextFloat()+0.01F,-random.nextFloat()-0.01F));
            var parent=new PoseStack.Pose();
            if(i%6>0)parent.translate(1,-2,3);
            if(i%6>1)parent.rotate(new Quaternionf().rotationZYX(0.13F,0.72F,-0.51F));
            if(i%6>2)parent.scale(1.5F,-0.7F,2.3F);
            if(i%6==4)parent.pose().assume(2);
            if(i%6==5)parent.normal().set(1,2,3,4,5,6,7,8,9);
            for(boolean left:new boolean[]{false,true})for(boolean apply:new boolean[]{false,true}) {
                var capture=NativeItemLayerTransform.capture(transform,left);
                var nativePose=NativeItemLayerTransform.WorldPose.capture(capture,parent,apply);
                assertNotNull(nativePose);
                var expected=parent.copy();
                if(apply)transform.apply(left,expected);
                else {
                    var local=new PoseStack.Pose();transform.apply(left,local);
                    expected.pose().mul(new Matrix4f().set(local.pose().get(new float[16])));
                    expected.normal().mul(new Matrix3f().set(local.normal().get(new float[9])));
                    expected.trustedNormals=parent.trustedNormals&&local.trustedNormals;
                }
                exact(expected.pose().get(new float[16]),nativePose.modelTransform());
                var foil=nativePose.withDecalFoil().decalProjection();
                exact(expected.normal().get(new float[9]),foil.normalPose());
                assertEquals(expected.trustedNormals,foil.trustedNormals());
                assertEquals(!apply,foil.firstPerson());
            }
        }
    }
    @Test void captures_survive_parent_mutation_and_decline_unusual_inputs_before_lowering() {
        var parent=new PoseStack.Pose();
        var capture=NativeItemLayerTransform.capture(ItemTransform.NO_TRANSFORM,false);
        var pose=NativeItemLayerTransform.WorldPose.capture(capture,parent,true);
        float[] expected=pose.modelTransform();
        parent.translate(25,26,27);parent.normal().zero();parent.trustedNormals=false;
        System.gc();exact(expected,pose.modelTransform());
        parent.pose().identity().assume(0);
        assertNull(NativeItemLayerTransform.WorldPose.capture(capture,parent,true));
        parent.pose().identity().translation(1.0e11F,0,0);
        assertNull(NativeItemLayerTransform.WorldPose.capture(capture,parent,true));
        parent.pose().identity();parent.normal().set(1,0,0,0,Float.NaN,0,0,0,1);
        assertNull(NativeItemLayerTransform.WorldPose.capture(capture,parent,true));
    }
    private static void exact(float[] expected,float[] actual) {
        assertEquals(expected.length,actual.length);
        for(int i=0;i<expected.length;i++)assertEquals(Float.floatToRawIntBits(expected[i]),Float.floatToRawIntBits(actual[i]),"component "+i);
    }
}
