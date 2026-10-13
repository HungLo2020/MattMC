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
