package net.minecraft.client.dev;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GraphicsAuditBlockDisplayFixtureTest {
    @Test void placementUsesNormalizedLookAndVanillaUnitBlockOrigin() {
        assertEquals(new Vec3(9.5, 19.5, 33.5),
            GraphicsAuditBlockDisplayFixture.position(new Vec3(10, 20, 30), new Vec3(0, 0, 5)));
        assertEquals(new Vec3(9.5, 19.5, 33.5),
            GraphicsAuditBlockDisplayFixture.position(new Vec3(10, 20, 30), Vec3.ZERO));
        assertEquals(new Vec3(5.5, 19.5, 29.5),
            GraphicsAuditBlockDisplayFixture.position(new Vec3(10, 20, 30), new Vec3(-1, 0, 0)));
    }
}

