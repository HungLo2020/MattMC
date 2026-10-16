package net.minecraft.server.level;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;

/** Current-image nonvacuity only; the portable Frozen observer is unchanged. */
public final class VerifyCoordinatorNativeInputs {
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            fixture.initialize();
            fixture.assertReady();
            for (LightLayer channel : LightLayer.values()) {
                Object engine = fixture.engine.getLayerListener(channel);
                Object propagation = VerifyThreadedLighting.field(engine, "nativePropagation");
                long reads = ((Number)VerifyThreadedLighting.invoke(propagation,
                    "directLayerReadsForVerification", new Class<?>[0])).longValue();
                if (reads <= 0) throw new AssertionError("No actual native layer inputs in " + channel);
                System.out.println("NATIVE_INPUT " + channel + " direct-layer-reads=" + reads);
            }
        }
    }
}
