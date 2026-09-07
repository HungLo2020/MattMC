package net.minecraft.client.particle;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditTerrainParticleFixtureTest {
    @Test void frozenTickFixtureRequiresRealEngineMembershipWithoutAdvancingSimulation() {
        var engine = new ParticleEngine(null, null);
        var value = new Particle(null, 1, 2, 3) {
            @Override public ParticleRenderType getGroup() { return ParticleRenderType.NO_RENDER; }
            @Override public void tick() { throw new AssertionError("fixture must not tick simulation"); }
        };
        String key = "mattmc.dev.graphicsAuditMagmaParticle";
        String previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertThrows(IllegalStateException.class, () -> engine.installGraphicsAuditParticle(value));
            engine.add(value);
            assertTrue(value.isAlive());
            assertFalse(engine.containsGraphicsAuditParticle(value), "alive in pending queue is not rendered membership");
            engine.clearParticles();
            System.setProperty(key, "true");
            engine.installGraphicsAuditParticle(value);
            assertTrue(engine.containsGraphicsAuditParticle(value));
            assertEquals("1", engine.countParticles());
            engine.clearParticles();
            assertFalse(engine.containsGraphicsAuditParticle(value));
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
    @Test void ordinaryConstructionRetainsItsInputs() {
        assertEquals(0.37F, GraphicsAuditTerrainParticleFixture.offset(0.37F));
        GraphicsAuditTerrainParticleFixture.configure(null); // ordinary path does not touch particles
    }
    @Test void fixtureScopeRestoresOrdinaryInputsAfterSuccessAndFailure() {
        assertEquals(1.0F, GraphicsAuditTerrainParticleFixture.construct(
            () -> GraphicsAuditTerrainParticleFixture.offset(0.37F)));
        assertEquals(0.37F, GraphicsAuditTerrainParticleFixture.offset(0.37F));
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditTerrainParticleFixture.construct(
            () -> { throw new IllegalArgumentException("fixture construction failed"); }));
        assertEquals(0.37F, GraphicsAuditTerrainParticleFixture.offset(0.37F));
        assertThrows(IllegalStateException.class, () -> GraphicsAuditTerrainParticleFixture.construct(
            () -> GraphicsAuditTerrainParticleFixture.construct(() -> 1)));
        assertEquals(0.37F, GraphicsAuditTerrainParticleFixture.offset(0.37F));
    }
    @Test void positionIsThreeBlocksAlongNormalizedCameraDirection() {
        assertEquals(new Vec3(1,2,6), GraphicsAuditTerrainParticleFixture.position(new Vec3(1,2,3), new Vec3(0,0,8)));
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditTerrainParticleFixture.position(Vec3.ZERO, Vec3.ZERO));
    }
}
