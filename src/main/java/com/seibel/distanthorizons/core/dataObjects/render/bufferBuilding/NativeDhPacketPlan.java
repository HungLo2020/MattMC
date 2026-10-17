package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.util.NativeLibraryLoader;
import net.vulkanic.world.DistantHorizonsSemanticCollector;
import net.vulkanic.world.NativeRenderLaunchConfiguration;

/** Scoped immutable CPU plan; Java supplies ordered contextual scalar results only. */
final class NativeDhPacketPlan implements AutoCloseable {
    private static MethodHandle bind(String name, FunctionDescriptor descriptor, boolean heap) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", name, descriptor, Linker.Option.critical(heap));
    }
    private static final MethodHandle CREATE = bind("mattmc_dh_packet_plan", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), false);
    private static final MethodHandle RELEASE = bind("mattmc_dh_packet_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), false);
    private static final MethodHandle COUNT = bind("mattmc_dh_packet_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), false);
    private static final MethodHandle EVENTS = bind("mattmc_dh_packet_events", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), true);
    private static final EDhDirection[] DIRECTIONS = EDhDirection.values();
    private MemorySegment owner;
    private NativeDhPacketPlan(MemorySegment owner) { this.owner = owner; }
    static NativeDhPacketPlan create(MemorySegment builder, int route, int grass) {
        MemorySegment pointer = MemorySegment.NULL;
        try {
            int audit = NativeRenderLaunchConfiguration.graphicsAuditEnabled() ? 1 : 0;
            pointer = (MemorySegment)CREATE.invokeExact(builder, route, grass, audit);
            if (pointer.address() == 0) throw new IllegalStateException("Cannot prepare DH packet plan");
            return new NativeDhPacketPlan(pointer);
        } catch (Throwable e) {
            if (pointer.address() != 0) release(pointer);
            throw failure(e);
        }
    }
    private static RuntimeException failure(Throwable e) {
        if (e instanceof Error error) throw error;
        return e instanceof RuntimeException error ? error : new IllegalStateException("Native DH packet operation failed", e);
    }
    private int count(int field) {
        try {
            if (owner.address() == 0) throw new IllegalStateException("DH packet plan closed");
            int count = (int)COUNT.invokeExact(owner, field);
            if (count < 0) throw new IllegalStateException("Invalid DH packet plan extent");
            return count;
        } catch (Throwable e) { throw failure(e); }
    }
    private record Callbacks(int[] colors, float[] shades) {}
    private Callbacks callbacks(IClientLevelWrapper level, IMinecraftRenderWrapper render) {
        int events = count(1), callbacks = count(2);
        int[] colors = new int[callbacks];
        float[] shades = new float[callbacks];
        if (events != 0) {
            byte[] data = new byte[Math.multiplyExact(events, 16)];
            try {
                if ((int)EVENTS.invokeExact(owner, MemorySegment.ofArray(data), events) != 1)
                    throw new IllegalStateException("Cannot project DH packet callbacks");
            } catch (Throwable e) { throw failure(e); }
            ByteBuffer rows = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            for (int i = 0; i < events; i++) {
                int p = i * 16;
                if (rows.get(p+4) == 1) {
                    int callback = rows.getInt(p+12);
                    colors[callback] = level.getDirtBlockColor();
                    shades[callback] = render.getShade(DIRECTIONS[rows.get(p+5)]);
                } else {
                    int color = rows.getInt(p+8);
                    DistantHorizonsSemanticCollector.recordPackedWaterVertexForAudit(
                        color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24);
                }
            }
        }
        return new Callbacks(colors, shades);
    }
    void appendTo(NativeDhBuiltColumn column, int route, IClientLevelWrapper level, IMinecraftRenderWrapper render) {
        var values = callbacks(level, render);
        column.append(owner, route, values.colors(), values.shades());
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw failure(e); }
    }
    public void close() {
        if (owner.address() == 0) return;
        MemorySegment pointer = owner; owner = MemorySegment.NULL; release(pointer);
    }
}
