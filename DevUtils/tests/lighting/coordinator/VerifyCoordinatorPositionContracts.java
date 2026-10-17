package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

/** Same public observer on Frozen, preserved pre-change and Current. */
public final class VerifyCoordinatorPositionContracts {
    private static final List<String> calls = new ArrayList<>();
    private static final RuntimeException failure = new RuntimeException("position route failure");

    private static final class Routed extends BlockPos {
        final boolean reject;
        Routed(boolean reject) { super(8, 24, 8); this.reject = reject; }
        @Override public BlockPos immutable() { calls.add("immutable"); return new BlockPos(8, 24, 8); }
        @Override public int getX() { calls.add("x"); if (reject) throw failure; return super.getX(); }
        @Override public int getZ() { calls.add("z"); return super.getZ(); }
    }

    private static final class Packed extends BlockPos {
        boolean reject = true;
        Packed() { super(8, 24, 8); }
        @Override public long asLong() {
            calls.add("pack");
            if (reject) { reject = false; throw failure; }
            return super.asLong();
        }
    }

    private static final class Input extends BlockPos {
        final BlockPos captured;
        Input(BlockPos captured) { super(8, 24, 8); this.captured = captured; }
        @Override public BlockPos immutable() { calls.add("immutable"); return captured; }
    }

    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (boolean reject : new boolean[]{false, true}) {
            calls.clear();
            try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
                try { fixture.engine.checkBlock(new Routed(reject)); if (reject) throw new AssertionError("Missing route failure"); }
                catch (RuntimeException error) { if (!reject || error != failure) throw error; }
                fixture.transport();
                System.out.println("POSITION route-reject=" + reject + " calls=" + calls + " pending=" + fixture.pending());
                if (!reject) VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]);
                System.out.println("POSITION route-after=" + calls + " pending=" + fixture.pending());
            }
        }
        calls.clear();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            fixture.engine.checkBlock(new Input(new Packed()));
            fixture.transport();
            System.out.println("POSITION deferred=" + calls + " pending=" + fixture.pending());
            try { VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]); throw new AssertionError("Missing PRE failure"); }
            catch (RuntimeException error) { if (error != failure) throw error; }
            System.out.println("POSITION failed=" + calls + " pending=" + fixture.pending());
            VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]);
            System.out.println("POSITION retry=" + calls + " pending=" + fixture.pending());
        }
    }
}
