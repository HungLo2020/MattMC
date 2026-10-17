package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptedCameraSweepTest {
    @Test void sweepStartsAtBaseAndReachesBothExtremes() {
        assertEquals(0.0f, ScriptedCameraSweep.yawOffset(0.0), 1e-4f);
        assertEquals(0.0f, ScriptedCameraSweep.pitch(0.0), 1e-4f);
        // Defaults: 150 degrees over 2.4 s, 60 degrees over 1.6 s.
        assertEquals(150.0f, ScriptedCameraSweep.yawOffset(0.6), 1e-3f);
        assertEquals(-150.0f, ScriptedCameraSweep.yawOffset(1.8), 1e-3f);
        assertEquals(60.0f, ScriptedCameraSweep.pitch(0.4), 1e-3f);
        assertEquals(-60.0f, ScriptedCameraSweep.pitch(1.2), 1e-3f);
    }

    @Test void pitchStaysInVanillaRange() {
        for (double t = 0.0; t < 10.0; t += 0.01) {
            float pitch = ScriptedCameraSweep.pitch(t);
            assertTrue(pitch >= -90.0f && pitch <= 90.0f);
        }
    }
}
