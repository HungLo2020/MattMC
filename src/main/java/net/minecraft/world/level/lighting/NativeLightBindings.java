package net.minecraft.world.level.lighting;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.chunk.NativeReclaimer;

/** Actual JVM/native resource handoffs; contains no light policy or membership. */
final class NativeLightBindings {
    private static final MethodHandle REGISTER;
    private static final byte MAP_KIND;
    static {
        try {
            var lookup = MethodHandles.privateLookupIn(NativeReclaimer.class, MethodHandles.lookup());
            REGISTER = lookup.findStatic(NativeReclaimer.class, "register",
                MethodType.methodType(void.class, Object.class, MemorySegment.class, byte.class));
            MAP_KIND = (byte)lookup.findStaticGetter(NativeReclaimer.class, "LIGHT_MAP", byte.class).invokeExact();
        } catch (Throwable failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private final NativeLightingMap.IdentityPins pins;

    NativeLightBindings() { this.pins = NativeLightingMap.createPins(); }

    NativeLightingMap newMap() { return new NativeLightingMap(this.pins); }

    static void registerMapOwner(Object referent, MemorySegment owner) {
        try { REGISTER.invokeExact(referent, owner, MAP_KIND); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native light-map registration failed", failure); }
    }
}
