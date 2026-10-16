package net.minecraft.server.level;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Fault the actual VM binding publication once; verify its original PRE prefix. */
public final class VerifyCoordinatorImportBindingFailure {
    private static final RuntimeException FAULT = new RuntimeException("actual VM binding publication failure");
    private static final class FailOnce extends IdentityHashMap<Object, Object> {
        int calls;
        FailOnce(Map<?, ?> original) { original.forEach(super::put); }
        @Override public Object put(Object key, Object value) {
            if (++calls == 1) throw FAULT;
            return super.put(key, value);
        }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            Object storage = VerifyThreadedLighting.field(fixture.engine.getLayerListener(LightLayer.BLOCK), "storage");
            Object map = VerifyThreadedLighting.field(VerifyThreadedLighting.field(storage, "updatingSectionData"), "nativeMap");
            Object pins = VerifyThreadedLighting.field(map, "pins");
            var field = pins.getClass().getDeclaredField("identities"); field.setAccessible(true);
            IdentityHashMap<Object, Object> original = (IdentityHashMap<Object, Object>)field.get(pins);
            FailOnce injected = new FailOnce(original); field.set(pins, injected);
            try {
                var section = SectionPos.of(0, 1, 0);
                var input = new DataLayer(3);
                fixture.engine.queueSectionData(LightLayer.BLOCK, section, input);
                boolean earlyError = false;
                try { fixture.transport(); }
                catch (RuntimeException error) { if (error != FAULT) throw error; earlyError = true; }
                System.out.println("IMPORT_BINDING transport-calls=" + injected.calls + " pending=" + fixture.pending() + " early-error=" + earlyError);
                if (injected.calls != 0 || fixture.pending() != 1 || earlyError) throw new AssertionError("Fallible VM binding moved before PRE");
                try { VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]); throw new AssertionError("Missing binding failure"); }
                catch (RuntimeException error) { if (error != FAULT) throw error; }
                if (fixture.pending() != 1) throw new AssertionError("PRE binding failure removed admitted task");
                System.out.println("IMPORT_BINDING failed-calls=" + injected.calls + " pending=1 same-error=true");
                VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]);
                Map<?, ?> queued = (Map<?, ?>)VerifyThreadedLighting.field(storage, "queuedSections");
                if (fixture.pending() != 0 || queued.get(section.asLong()) != input) throw new AssertionError("Binding retry changed identity/retirement");
                System.out.println("IMPORT_BINDING retry-calls=" + injected.calls + " pending=0 alias=true");
            } finally {
                original.clear(); original.putAll(injected); field.set(pins, original);
            }
        }
    }
}
