package net.vulkanic.world;

import java.lang.foreign.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;
import org.junit.jupiter.api.*;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compares the new production boundary with the existing copied-record encoder. */
class NativeModelFrameTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static ModelPart[] nodes() { return new ModelPart[]{new ModelPart(List.of(), Map.of())}; }
    private static WorldMeshInstance append(NativeModelFrame frame, ModelPart[] nodes, Matrix4f matrix,
        int depth, int flags, int light, StandardItemFoilRecord foil) {
        return frame.append(nodes, -17L, matrix, depth, 2, 0xff123456, 0x77223344, 0xff223344,
            light, 1280, 720, flags, foil);
    }
    private static WorldMeshInstance copied(int first, Matrix4f matrix, int depth, int flags, int light,
        StandardItemFoilRecord foil) {
        WorldMeshInstance value = new WorldMeshInstanceRecord(67, -17L, first + 1L, -1, depth, 2, 1,
            0xff123456, matrix.get(new float[16]), 1280, 720, 0, 0x77223344, 0xff223344,
            flags | WORLD_MESH_INSTANCE_FLAG_MODEL_RIG).withPackedLight(light);
        return foil == null ? value : value.withItemFoil(foil);
    }
    private static void same(WorldMeshInstance expected, WorldMeshInstance actual) {
        assertArrayEquals(expected.transform(), actual.transform());
        assertEquals(expected.itemFoil(), actual.itemFoil());
        assertEquals(expected.entityCulling(), actual.entityCulling());
        assertEquals(expected.modelSubmissionOrder(), actual.modelSubmissionOrder());
        try (Arena arena = Arena.ofConfined()) {
            var layout = Struct.WORLD_MESH_INSTANCE_RECORD;
            var a = arena.allocate(layout.byteSize(), 8); var b = arena.allocate(layout.byteSize(), 8);
            expected.encodeInto(a, null); actual.encodeInto(b, null);
            // Compare every declared ABI field, excluding C padding bytes.
            for (int field = 0; field <= 40; field++) {
                int bytes = switch(field) {
                    case 9,10,21,22,36 -> 8;
                    case 13,26 -> 64;
                    case 18 -> 12;
                    case 19,35 -> 24;
                    case 27,39 -> 36;
                    case 33,34 -> 48;
                    default -> 4;
                };
                assertEquals(-1L, a.asSlice(layout.offset(field), bytes).mismatch(b.asSlice(layout.offset(field), bytes)),
                    "ABI field " + field);
            }
        }
    }
    @Test void ordinaryScopesAndImmutableVariantsMatchCopiedRecords() {
        try (var frame = new NativeModelFrame()) {
            var matrix = new Matrix4f().translationRotateScale(2, -3, 4, .1f, .2f, .3f, .9f, -1, 2, .5f);
            var bounds = new WorldAabbRecord(-1, -2, -3, 4, 5, 6);
            beginSemanticModelOrder(-23);
            beginSemanticEntityCulling(new WorldEntityCullingRecord(17, bounds, bounds, 29_999_999.125, 8, -4));
            try {
                for (int flags : new int[]{0,1,4,8}) {
                    int first = frame.poseCount();
                    var nativeValue = append(frame, nodes(), matrix, 1, flags, 0xf000f0, null);
                    var copy = copied(first, matrix, 1, flags, 0xf000f0, null);
                    same(copy, nativeValue);
                    same(copy.withViewport(640,480), nativeValue.withViewport(640,480));
                    same(copy.withPackedLight(0x100010), nativeValue.withPackedLight(0x100010));
                    same(copy.withModelSubmissionOrder(null), nativeValue.withModelSubmissionOrder(null));
                    if (flags != 1) same(copy.asShadowOnlyEntityCaster(),nativeValue.asShadowOnlyEntityCaster());
                    same(copy,nativeValue);
                }
                beginSemanticBlockEntity(42);
                try {
                    int first = frame.poseCount();
                    same(copied(first,matrix,1,0,0,null),append(frame,nodes(),matrix,1,0,0,null));
                } finally { endSemanticBlockEntity(); }
            } finally { endSemanticEntityCulling(); endSemanticModelOrder(); }
            for (StandardFoilKind kind : StandardFoilKind.values()) {
                var foil = new StandardItemFoilRecord(123456L, .5, .75f, kind);
                int first = frame.poseCount();
                same(copied(first,matrix,1,0,0,foil),append(frame,nodes(),matrix,1,0,0,foil));
                first = frame.poseCount();
                var nativeValue=append(frame,nodes(),matrix,1,0,0,null);
                same(copied(first,matrix,1,0,0,null).withItemFoil(foil),nativeValue.withItemFoil(foil));
            }
            var equal = append(frame,nodes(),matrix,3,0,0,null);
            same(copied(frame.poseCount()-1,matrix,3,0,0,null).asShadowOnlyEntityCaster(),equal.asShadowOnlyEntityCaster());
        }
    }
    @Test void poseStorageIsIndependentOfLaterAnimationAndPassedDirectlyToSubmission() throws Exception {
        try (var frame = new NativeModelFrame(); var arena = Arena.ofConfined()) {
            var nodes = nodes(); var part = nodes[0];
            part.x=-0.0f;part.y=2;part.z=-3;part.xRot=.25f;part.yRot=-.5f;part.zRot=1;
            part.xScale=-1;part.yScale=2;part.zScale=.5f;part.visible=false;part.skipDraw=true;
            append(frame,nodes,new Matrix4f(),1,0,0,null);
            assertThrows(IllegalStateException.class,frame::poseData);
            var poses = ModelRigPoses.nativeFrame(frame);
            part.x=99;part.skipDraw=false;
            var data=frame.poseData();
            assertArrayEquals(new float[]{-0.0f,2,-3,.25f,-.5f,1,-1,2,.5f},data.asSlice(0,36).toArray(ValueLayout.JAVA_FLOAT));
            assertEquals(2,data.get(ValueLayout.JAVA_INT,36));
            assertTrue(data.isReadOnly());
            var request=arena.allocate(Struct.WHOLE_FRAME_SUBMIT.byteSize(),8);
            var write=VulkanicGalBridge.class.getDeclaredMethod("writeModelRigPoses",Arena.class,MemorySegment.class,ModelRigPoses.class);
            write.setAccessible(true);write.invoke(null,arena,request,poses);
            assertEquals(data.address(),request.get(ValueLayout.ADDRESS,Struct.WHOLE_FRAME_SUBMIT.offset(50)).address());
            assertThrows(IllegalArgumentException.class,()->append(frame,nodes,new Matrix4f(),1,0,0,null));
        }
    }
    @Test void rollbackReusedSlotsAndCloseNeverReviveBorrowedInstances() {
        var frame=new NativeModelFrame();
        var first=append(frame,nodes(),new Matrix4f(),1,0,0,null);
        var discarded=append(frame,nodes(),new Matrix4f().translate(4,5,6),1,0,0,null);
        frame.rollback(1,1);
        var replacement=append(frame,nodes(),new Matrix4f().translate(7,8,9),1,0,0,null);
        assertThrows(IllegalStateException.class,discarded::meshKey);
        assertThrows(IllegalStateException.class,()->discarded.withViewport(1,1));
        assertEquals(-17,first.meshKey());assertEquals(7,replacement.transform()[12]);
        frame.seal();var variant=first.withViewport(800,600);
        assertEquals(1280,first.viewportWidth());assertEquals(800,variant.viewportWidth());
        var poseData=frame.poseData();frame.close();frame.close();
        assertFalse(frame.isAlive());
        assertThrows(IllegalStateException.class,first::meshKey);
        assertThrows(IllegalStateException.class,()->poseData.get(ValueLayout.JAVA_FLOAT,0));
    }
    @Test void invalidInstancesKeepCommittedPosePrefixAndInterruptedReservationsAreReplaced() {
        try(var frame=new NativeModelFrame()) {
            assertThrows(IllegalArgumentException.class,()->append(frame,nodes(),new Matrix4f(),1,0,-1,null));
            assertEquals(1,frame.poseCount());assertEquals(0,frame.instanceCount());
            assertThrows(NullPointerException.class,()->append(frame,new ModelPart[]{null},new Matrix4f(),1,0,0,null));
            assertEquals(1,frame.poseCount());
            var next=append(frame,nodes(),new Matrix4f(),1,0,0,null);
            assertEquals(2,next.meshGeneration());assertEquals(2,frame.poseCount());
            for(int flags:new int[]{2,3,12,16,-1}) {
                assertThrows(IllegalArgumentException.class,()->copied(0,new Matrix4f(),1,flags,0,null));
                assertThrows(IllegalArgumentException.class,()->append(frame,nodes(),new Matrix4f(),1,flags,0,null));
            }
        }
    }
    @Test void completionFailureClosesTransferredOwner() throws Exception {
        var owner = new NativeModelFrame();
        var instance = append(owner,nodes(),new Matrix4f(),1,0,0,null);
        var poses = ModelRigPoses.nativeFrame(owner);
        var capturedParticles = new net.vulkanic.bridge.NativeParticleBuffer();
        capturedParticles.append(17,0,0,1,2,3,0,0,0,1,.5F,0,1,0,1,-1,240);
        var particles = capturedParticles.snapshot();
        capturedParticles.close();
        var empty = new RustGalWorldPrimitiveRenderer.PrimitiveFrame(128,128,
            new Matrix4f().get(new float[16]),new Matrix4f().get(new float[16]),
            new WorldBackgroundRecord(false,0,0,0,0,128,128),List.of(),List.of(),List.of(),List.of(),List.of(instance));
        var components = empty.getClass().getRecordComponents();
        Object[] values = new Object[components.length]; Class<?>[] types = new Class<?>[components.length];
        for(int i=0;i<components.length;i++) {
            types[i]=components[i].getType();
            values[i]=components[i].getName().equals("modelRigPoses") ? poses
                : components[i].getName().equals("particleQuads") ? particles : components[i].getAccessor().invoke(empty);
        }
        var frame = empty.getClass().getDeclaredConstructor(types).newInstance(values);
        var coordinator = net.vulkanic.gui.RustGalFrameCoordinator.class;
        var submitted = Class.forName(coordinator.getName()+"$SubmittedFrame");
        components=submitted.getRecordComponents();values=new Object[components.length];types=new Class<?>[components.length];
        for(int i=0;i<components.length;i++) {
            types[i]=components[i].getType();
            values[i]=types[i]==long.class ? Long.valueOf(0) : types[i]==int.class ? Integer.valueOf(0)
                : types[i]==boolean.class ? Boolean.FALSE : components[i].getName().equals("primitiveFrame") ? frame : null;
        }
        var constructor=submitted.getDeclaredConstructor(types);constructor.setAccessible(true);
        var value=constructor.newInstance(values);
        var complete=coordinator.getDeclaredMethod("completeSubmittedFrame",submitted,WholeFrameSubmitResult.class,
            GuiFrameSubmitResult.class,PresentedFrame.class);complete.setAccessible(true);
        try {
            assertThrows(java.lang.reflect.InvocationTargetException.class,()->complete.invoke(null,value,null,null,null));
            assertFalse(owner.isAlive());
            assertThrows(IllegalStateException.class,instance::meshKey);
            assertThrows(IllegalStateException.class,particles::size);
            assertThrows(IllegalStateException.class,()->particles.get(0));
        } finally { owner.close(); particles.close(); }
    }

}
