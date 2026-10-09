package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** Owns cloud CPU policy in Rust. Coordinates here are API projections only;
 * native rendering reads the requested pose generation directly from its owner. */
public final class NativeDhCloudGroupState {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_cloud_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle PREPARE = handle("prepare", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle COLOR = handle("color", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private final MemorySegment owner;
    private final MemorySegment projection = Arena.ofAuto().allocate(24,8);

    public NativeDhCloudGroupState(int width, int x, int z, long now) {
        try {
            MemorySegment pointer = (MemorySegment) CREATE.invokeExact(width,x,z,now);
            if (pointer.address() == 0) throw new IllegalArgumentException("Invalid native cloud layout");
            try { owner = pointer.asReadOnly().reinterpret(1,Arena.ofAuto(),NativeDhCloudGroupState::release); }
            catch (Throwable failure) { release(pointer); throw failure; }
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot create cloud state",failure); }
    }
    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot release cloud state",failure); }
    }
    /** Disabled groups do not call prepare, preserving their previous clock/origin. */
    public boolean prepare(long now, float speed, double x, double y, double z,
            float lx, float ly, float lz, int radius, int height) {
        try {
            if ((int) PREPARE.invokeExact(owner,now,speed,x,y,z,lx,ly,lz,radius,height,projection) != 0)
                throw new IllegalStateException("Native cloud preparation rejected");
            return projection.get(ValueLayout.JAVA_INT,8) != 0;
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot prepare cloud state",failure); }
        finally { Reference.reachabilityFence(this); }
    }
    public boolean colorChanged(int argb) {
        try { return (int) COLOR.invokeExact(owner,argb) != 0; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot update cloud color",failure); }
        finally { Reference.reachabilityFence(this); }
    }
    public double originX() { return projection.get(ValueLayout.JAVA_FLOAT,12); }
    public double originY() { return projection.get(ValueLayout.JAVA_FLOAT,16); }
    public double originZ() { return projection.get(ValueLayout.JAVA_FLOAT,20); }
    public long poseEpoch() { return projection.get(ValueLayout.JAVA_LONG,0); }
    /** CPU owner only. Caller pins this object until synchronous decode or async join. */
    public long ownerAddress() { return owner.address(); }
    public boolean matches(double x, double y, double z) {
        return poseEpoch() != 0 && Double.doubleToLongBits(x) == Double.doubleToLongBits(originX())
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(originY())
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(originZ());
    }
}
