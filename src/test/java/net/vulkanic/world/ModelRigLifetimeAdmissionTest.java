package net.vulkanic.world;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.vulkanic.bridge.VulkanicGalBridge.WorldMeshAssetRecord;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual producer's partial-admission and registration failures. */
class ModelRigLifetimeAdmissionTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static Object field(String name) throws Exception {
        var field = RustGalWorldPrimitiveRenderer.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(null);
    }
    @Test void failedSecondAdmissionPreservesOnlySuccessfulPrefix() throws Exception { failure(false); }
    @Test void failedRegistrationPreservesAllPreviouslyAdmittedParts() throws Exception { failure(true); }

    @Test @SuppressWarnings("unchecked")
    void retainedAdmissionReusesOnlyUnchangedDependenciesAcrossFrames() throws Exception {
        synchronized(field("LOCK")) {
            var assets=(Map<Long,WorldMeshAssetRecord>)field("WORLD_MESH_ASSETS");
            var textures=(Map<Integer,net.vulkanic.bridge.VulkanicGalBridge.WorldMeshTextureAssetRecord>)field("WORLD_MESH_TEXTURES");
            var lifetime=(DynamicWorldMeshLifetime)field("DYNAMIC_WORLD_MESH_LIFETIME");
            var frameField=RustGalWorldPrimitiveRenderer.class.getDeclaredField("semanticFrameSequence");frameField.setAccessible(true);
            long originalFrame=frameField.getLong(null), frame=Math.max(1L,originalFrame+1), key=0x7d030101L, rig=0;
            int textureId=0x7d030102;
            var mark=lifetime.checkpoint(frame);
            var asset=new WorldMeshAssetRecord(key,101,2,1,List.of(),new byte[0],List.of());
            var texture=new net.vulkanic.bridge.VulkanicGalBridge.WorldMeshTextureAssetRecord(textureId,new byte[]{1,2});
            assertFalse(assets.containsKey(key));assertFalse(textures.containsKey(textureId));
            assets.put(key,asset);textures.put(textureId,texture);
            try {
                Class<?> extraction=Class.forName(RustGalWorldPrimitiveRenderer.class.getName()+"$BlockMeshExtraction");
                var ec=extraction.getDeclaredConstructors()[0];ec.setAccessible(true);
                Object meshes=Array.newInstance(extraction,1);Array.set(meshes,0,ec.newInstance(key,101L,asset,List.of(texture)));
                Class<?> topology=Class.forName(RustGalWorldPrimitiveRenderer.class.getName()+"$CachedModelTopology");
                var tc=topology.getDeclaredConstructors()[0];tc.setAccessible(true);
                Object model=tc.newInstance(new ModelPart[1],new int[]{-1},meshes,Map.of());
                var prepare=RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("prepareModelRigLocked",topology);prepare.setAccessible(true);
                var proof=topology.getDeclaredField("rigAdmission");proof.setAccessible(true);
                frameField.setLong(null,frame);rig=(long)prepare.invoke(null,model);
                Object firstProof=proof.get(model);assertNotNull(firstProof);
                frameField.setLong(null,++frame);assertEquals(rig,(long)prepare.invoke(null,model));assertSame(firstProof,proof.get(model));
                // Equal content in a new wrapper still invalidates identity-dependent admission.
                textures.put(textureId,new net.vulkanic.bridge.VulkanicGalBridge.WorldMeshTextureAssetRecord(textureId,new byte[]{1,2}));
                assertFalse(((WorldAssetRegistry.Proof)firstProof).valid());
                frameField.setLong(null,++frame);assertEquals(rig,(long)prepare.invoke(null,model));
                assertSame(firstProof,proof.get(model));assertTrue(((WorldAssetRegistry.Proof)firstProof).valid());
                // Removing the pre-admitted fixture must resume full validation;
                // its deliberately empty geometry cannot be re-admitted.
                assets.remove(key);frameField.setLong(null,++frame);
                assertInstanceOf(IllegalArgumentException.class,assertThrows(InvocationTargetException.class,()->prepare.invoke(null,model)).getCause());
            } finally {
                lifetime.rollbackTo(mark);assets.remove(key);textures.remove(textureId);frameField.setLong(null,originalFrame);
                if(rig!=0) {
                    ((Map<Long,?>)field("MODEL_RIGS")).remove(rig);
                    net.vulkanic.bridge.VulkanicGalBridge.releaseModelRig(rig);
                }
            }
        }
    }

    @Test @SuppressWarnings("unchecked")
    void productionEnqueueAndBatchRollbackUseNativeInstanceAndPoseOwnership() throws Exception {
        synchronized(field("LOCK")) {
            var renderer=RustGalWorldPrimitiveRenderer.class;
            var width=renderer.getDeclaredField("pendingViewportWidth");width.setAccessible(true);
            var height=renderer.getDeclaredField("pendingViewportHeight");height.setAccessible(true);
            int oldWidth=width.getInt(null),oldHeight=height.getInt(null);
            var pending=(List<net.vulkanic.bridge.VulkanicGalBridge.WorldMeshInstance>)field("PENDING_MESH_INSTANCES");
            assertNull(field("pendingNativeModelFrame"));
            var initial=RustGalWorldPrimitiveRenderer.markModelMeshBatch();
            var assets=(Map<Long,WorldMeshAssetRecord>)field("WORLD_MESH_ASSETS");
            long key=0x7d031111L,rig=0;
            var asset=new WorldMeshAssetRecord(key,101,2,1,List.of(),new byte[0],List.of());
            assertFalse(assets.containsKey(key));assets.put(key,asset);
            try {
                width.setInt(null,320);height.setInt(null,200);
                var root=net.minecraft.client.model.CowModel.createBodyLayer().bakeRoot();
                var cow=new net.minecraft.client.model.CowModel(root);
                Class<?> extraction=Class.forName(renderer.getName()+"$BlockMeshExtraction");
                var ec=extraction.getDeclaredConstructors()[0];ec.setAccessible(true);
                Object mesh=ec.newInstance(key,101L,asset,List.of());
                Object meshes=Array.newInstance(extraction,1);Array.set(meshes,0,mesh);
                Class<?> topology=Class.forName(renderer.getName()+"$CachedModelTopology");
                var tc=topology.getDeclaredConstructors()[0];tc.setAccessible(true);
                Object model=tc.newInstance(new ModelPart[]{root},new int[]{-1},meshes,Map.of("",mesh));
                var enqueue=Arrays.stream(renderer.getDeclaredMethods()).filter(m->m.getName().equals("enqueueModelRig")).findFirst().orElseThrow();
                enqueue.setAccessible(true);
                Object[] args={cow,model,new net.blaze3d.vertex.PoseStack().last(),
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/cow/temperate_cow.png"),
                    0xf000f0,1,2,-1,0,0,0,null,"test"};
                rig=(long)enqueue.invoke(null,args);
                var owner=(NativeModelFrame)field("pendingNativeModelFrame");
                var first=pending.getLast();assertEquals(rig,first.meshKey());
                assertFalse(first instanceof net.vulkanic.bridge.VulkanicGalBridge.WorldMeshInstanceRecord);
                assertEquals(1,owner.poseCount());assertEquals(1,owner.instanceCount());
                var mark=RustGalWorldPrimitiveRenderer.markModelMeshBatch();
                enqueue.invoke(null,args);var second=pending.getLast();
                RustGalWorldPrimitiveRenderer.rollbackModelMeshBatch(mark);
                assertEquals(1,owner.poseCount());assertEquals(1,owner.instanceCount());
                assertEquals(rig,first.meshKey());assertThrows(IllegalStateException.class,second::meshKey);
                enqueue.invoke(null,args);assertThrows(IllegalStateException.class,second::meshKey);
                assertEquals(2,pending.getLast().meshGeneration());
            } finally {
                RustGalWorldPrimitiveRenderer.rollbackModelMeshBatch(initial);
                var discard=renderer.getDeclaredMethod("discardModelRigPosesLocked");discard.setAccessible(true);discard.invoke(null);
                assets.remove(key);width.setInt(null,oldWidth);height.setInt(null,oldHeight);
                if(rig!=0) {
                    ((Map<Long,?>)field("MODEL_RIGS")).remove(rig);
                    net.vulkanic.bridge.VulkanicGalBridge.releaseModelRig(rig);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void failure(boolean registration) throws Exception {
        synchronized(field("LOCK")) {
            var assets=(Map<Long,WorldMeshAssetRecord>)field("WORLD_MESH_ASSETS");
            var lifetime=(DynamicWorldMeshLifetime)field("DYNAMIC_WORLD_MESH_LIFETIME");
            long first=0x7d03_0001L, second=first+1;
            assertFalse(assets.containsKey(first));assertFalse(assets.containsKey(second));
            long frame=Math.max(1L,(long)field("semanticFrameSequence"));
            int before=lifetime.size();var mark=lifetime.checkpoint(frame);
            var good=new WorldMeshAssetRecord(first,101,2,1,List.of(),new byte[0],List.of());
            // First is pre-admitted. The second either fails ordinary validation,
            // or is admitted and then rejected by Rust's malformed-parent check.
            var bad=new WorldMeshAssetRecord(second,102,999,1,List.of(),new byte[0],List.of());
            assets.put(first,good);if(registration)assets.put(second,bad);
            try {
                Class<?> extraction=Class.forName(RustGalWorldPrimitiveRenderer.class.getName()+"$BlockMeshExtraction");
                var constructor=extraction.getDeclaredConstructors()[0];constructor.setAccessible(true);
                Object meshes=Array.newInstance(extraction,2);
                Array.set(meshes,0,constructor.newInstance(first,101L,good,List.of()));
                Array.set(meshes,1,constructor.newInstance(second,102L,bad,List.of()));
                Class<?> topology=Class.forName(RustGalWorldPrimitiveRenderer.class.getName()+"$CachedModelTopology");
                var tc=topology.getDeclaredConstructors()[0];tc.setAccessible(true);
                Object model=tc.newInstance(new ModelPart[2],new int[]{-1,9},meshes,Map.of());
                var prepare=RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("prepareModelRigLocked",topology);prepare.setAccessible(true);
                var failure=assertThrows(InvocationTargetException.class,()->prepare.invoke(null,model));
                assertInstanceOf(RuntimeException.class,failure.getCause());
                assertEquals(before+(registration?2:1),lifetime.size());
                lifetime.rollbackTo(mark);
                assertEquals(before,lifetime.size());
            } finally {
                lifetime.rollbackTo(mark);assets.remove(first);assets.remove(second);
            }
        }
    }
}
