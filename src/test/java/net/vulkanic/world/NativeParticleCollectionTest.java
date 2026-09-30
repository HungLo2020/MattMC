package net.vulkanic.world;

import java.util.List;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeParticleCollectionTest {
    @Test void terrainCountersDistinguishTypedTerrainFromOrdinaryParticles() {
        var quads=new java.util.ArrayList<VulkanicGalBridge.WorldParticleQuadRecord>();
        for(var surface : VulkanicGalBridge.ParticleSurface.values()) {
            quads.add(new VulkanicGalBridge.WorldParticleQuadRecord(
                RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS,surface,0,
                new float[]{0,0,0},new float[]{0,0,0,1},.35F,new float[]{0,1,0,1},-1,240));
        }
        String counters=RustGalWorldPrimitiveRenderer.materialMarkerSummary(List.of(),quads);
        assertTrue(counters.contains("terrain_particle_semantic_quads=3 "));
        assertTrue(counters.contains("material_terrain_particle_quads=3 "));
    }
    @Test @SuppressWarnings("unchecked")
    void terrainAdmissionCountsAlreadyQueuedOrdinaryParticleSemantics() throws Exception {
        var particleField=RustGalWorldPrimitiveRenderer.class.getDeclaredField("PENDING_PARTICLE_QUADS");
        var materialField=RustGalWorldPrimitiveRenderer.class.getDeclaredField("PENDING_MATERIAL_QUADS");
        var deviceField=net.vulkanic.VulkanicAPI.class.getDeclaredField("device");
        particleField.setAccessible(true); materialField.setAccessible(true); deviceField.setAccessible(true);
        var particles=(List<VulkanicGalBridge.WorldParticleQuadRecord>)particleField.get(null);
        int index=((List<?>)materialField.get(null)).size();
        int checkpoint=RustGalWorldPrimitiveRenderer.markMaterialQuadBatch();
        var oldDevice=deviceField.get(null);
        String flag="mattmc.dev.rustGalVulkanWholeFrame", old=System.getProperty(flag);
        try {
            System.setProperty(flag,"true");
            deviceField.set(null,new net.vulkanic.bridge.RustSemanticGpuDevice());
            var p=new VulkanicGalBridge.WorldParticleQuadRecord(17,false,index,
                new float[]{0,0,0},new float[]{0,0,0,1},.35F,new float[]{0,1,0,1},-1,240);
            while (RustGalWorldPrimitiveRenderer.hasMaterialQuadCapacity(1)) particles.add(p);
            int full=RustGalWorldPrimitiveRenderer.markMaterialQuadBatch();
            var error=assertThrows(IllegalStateException.class,()->RustGalWorldPrimitiveRenderer.enqueueTerrainParticle(
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),null,null,
                new net.minecraft.client.Camera(),0,0,0,0,0,0,new org.joml.Quaternionf(),
                1,.35F,0,1,0,1,-1,240,VulkanicGalBridge.ParticleSurface.TERRAIN_OPAQUE));
            assertTrue(error.getMessage().contains("capacity exceeded"),error.getMessage());
            assertEquals(full,RustGalWorldPrimitiveRenderer.markMaterialQuadBatch(),
                "reject before texture publication or any additional geometry");
        } finally {
            RustGalWorldPrimitiveRenderer.rollbackMaterialQuadBatch(checkpoint);
            deviceField.set(null,oldDevice);
            if(old==null)System.clearProperty(flag);else System.setProperty(flag,old);
        }
    }
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
}
