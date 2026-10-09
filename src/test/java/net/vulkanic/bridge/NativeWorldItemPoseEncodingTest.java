package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.vulkanic.world.NativeItemLayerTransform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.vulkanic.bridge.VulkanicGalBridge.*;

class NativeWorldItemPoseEncodingTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void native_copies_keep_parent_owner_and_encode_every_lane_in_dirty_storage() throws Exception {
        var parent=new PoseStack.Pose();parent.translate(10,20,30);
        var pose=NativeItemLayerTransform.WorldPose.capture(NativeItemLayerTransform.capture(ItemTransform.NO_TRANSFORM,false),parent,true);
        var instance=WorldMeshInstanceRecord.nativeItem(67,1,1,-1,1,0,0,0xffffffff,320,180,0,0,0,pose);
        var shadow=instance.asShadowOnlyEntityCaster();
        assertSame(pose,shadow.nativeItemPose());
        var copied=instance.withPackedLight(0x00f000f0).withViewport(640,360).withModelSubmissionOrder(7)
            .withItemFoil(new StandardItemFoilRecord(42,1,1)).withNativeDecalFoil();
        assertEquals(pose.ownerAddress(),copied.nativeItemPose().ownerAddress());
        var raw=WorldMeshInstanceRecord.class.getDeclaredField("transform");raw.setAccessible(true);
        assertNull(raw.get(copied));
        var encode=VulkanicGalBridge.class.getDeclaredMethod("encodeWorldMeshInstance",MemorySegment.class,WorldMeshInstanceRecord.class);encode.setAccessible(true);
        var struct=Struct.WORLD_MESH_INSTANCE_RECORD;
        try(Arena arena=Arena.ofConfined()) {
            var wire=struct.allocate(arena);wire.fill((byte)0x5a);encode.invoke(null,wire,copied);
            assertEquals(pose.ownerAddress(),wire.get(ValueLayout.JAVA_LONG,struct.offset(36)));
            assertEquals(1,wire.get(ValueLayout.JAVA_INT,struct.offset(37)));
            assertEquals(parent.pose().properties(),wire.get(ValueLayout.JAVA_INT,struct.offset(38)));
            assertEquals(10,wire.get(ValueLayout.JAVA_FLOAT,struct.offset(13)+48));
            assertEquals(1,wire.get(ValueLayout.JAVA_INT,struct.offset(24)));
            for(int i=0;i<16;i++)assertEquals(0,wire.get(ValueLayout.JAVA_FLOAT,struct.offset(26)+i*4L));
            for(int i=0;i<9;i++)assertEquals(0,wire.get(ValueLayout.JAVA_FLOAT,struct.offset(27)+i*4L));
            var inline=new WorldMeshInstanceRecord(67,1,1,-1,1,0,0,0xffffffff,parent.pose().get(new float[16]),320,180);
            encode.invoke(null,wire,inline);
            assertEquals(0,wire.get(ValueLayout.JAVA_LONG,struct.offset(36)));
            for(int field:new int[]{37,38,40})assertEquals(0,wire.get(ValueLayout.JAVA_INT,struct.offset(field)));
            for(int i=0;i<9;i++)assertEquals(0,wire.get(ValueLayout.JAVA_FLOAT,struct.offset(39)+i*4L));
        }
        assertArrayEquals(pose.modelTransform(),copied.transform());
        assertNotNull(copied.decalFoil());
    }
    @Test void pipelined_pins_are_independent_of_caller_lists_and_clear_unused_slots() throws Exception {
        var pose=NativeItemLayerTransform.WorldPose.capture(NativeItemLayerTransform.capture(ItemTransform.NO_TRANSFORM,false),new PoseStack.Pose(),false);
        var instance=WorldMeshInstanceRecord.nativeItem(67,1,1,-1,1,0,0,0xffffffff,320,180,0,0,0,pose);
        var caller=new ArrayList<>(List.of(instance));
        var pin=VulkanicGalBridge.class.getDeclaredMethod("pinWorldItemPoses",NativeItemLayerTransform.WorldPose[].class,List.class,List.class);pin.setAccessible(true);
        var held=(NativeItemLayerTransform.WorldPose[])pin.invoke(null,new NativeItemLayerTransform.WorldPose[0],List.of(),caller);
        caller.clear();System.gc();assertSame(pose,held[0]);
        var cleared=(NativeItemLayerTransform.WorldPose[])pin.invoke(null,held,List.of(),List.of());
        assertSame(held,cleared);assertNull(cleared[0]);
    }
}
