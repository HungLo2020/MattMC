package net.minecraft.server.level;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Actual private callback resource contract; native transition is checked separately. */
public final class VerifyCoordinatorLayerPinLifetime {
    private record Captured(WeakReference<DataLayer> object, MemorySegment reader) {}

    private static int callback(Method callback, int mode, long key, MemorySegment buffer) throws Exception {
        try { return (int)callback.invoke(null, mode, key, buffer); }
        catch (InvocationTargetException error) { throw VerifyThreadedLighting.cause(error); }
    }

    private static Captured capture(Object engine, Method callback, long key, MemorySegment buffer) throws Exception {
        Object storage = VerifyThreadedLighting.field(engine, "storage");
        Object map = VerifyThreadedLighting.field(VerifyThreadedLighting.field(storage, "updatingSectionData"), "nativeMap");
        DataLayer layer = new DataLayer(4);
        MemorySegment reader = layer.nativeLightView();
        VerifyThreadedLighting.invoke(map, "set", new Class<?>[]{long.class, DataLayer.class}, key, layer);
        int status = callback(callback, 0, key, buffer);
        if (status != 1 && status != 2) throw new AssertionError("Actual callback did not capture stored layer");
        VerifyThreadedLighting.invoke(map, "set", new Class<?>[]{long.class, DataLayer.class}, key, null);
        VerifyThreadedLighting.invoke(map, "clearCache", new Class<?>[]{boolean.class}, false);
        VerifyThreadedLighting.invoke(map, "drainForCoordinator", new Class<?>[0]);
        return new Captured(new WeakReference<>(layer), reader);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true); Arena arena = Arena.ofConfined()) {
            Object engine = fixture.engine.getLayerListener(LightLayer.BLOCK);
            Object propagation = VerifyThreadedLighting.field(engine, "nativePropagation");
            Class<?> type = propagation.getClass();
            var currentField = type.getDeclaredField("CURRENT"); currentField.setAccessible(true);
            ThreadLocal<Object> current = (ThreadLocal<Object>)currentField.get(null);
            Method callback = type.getDeclaredMethod("section", int.class, long.class, MemorySegment.class);
            callback.setAccessible(true);
            long key = SectionPos.of(0, 1, 0).asLong();
            MemorySegment buffer = arena.allocate(32768, 8);
            Object previous = current.get(); current.set(propagation);
            try {
                Captured captured = capture(engine, callback, key, buffer);
                for (int i = 0; i < 5; i++) { System.gc(); Thread.sleep(20); }
                if (captured.object.get() == null) throw new AssertionError("Captured callback layer lost its original pin");
                if (callback(callback, 2, key, buffer) != 0) throw new AssertionError("Connected replacement rejected");
                for (int i = 0; i < 150 && captured.object.get() != null; i++) { System.gc(); Thread.sleep(20); }
                if (captured.object.get() != null) throw new AssertionError("Replaced temporary layer pin still retains facade");
                if (captured.reader.get(ValueLayout.JAVA_INT, 0) != 4) throw new AssertionError("Explicit generation reader lost its lifetime");
                Reference.reachabilityFence(captured.reader);
                System.out.println("LAYER_PIN captured=true replaced=false explicit-reader=4");
            } finally { if (previous == null) current.remove(); else current.set(previous); }
        }
    }
}
