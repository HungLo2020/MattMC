package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Portable original status default/error and genuine repeat/copy/reentry effects. */
public final class VerifyCoordinatorStatusEffects {
    private static final RuntimeException FAILURE = new RuntimeException("original status copy failure");
    private static final class Source extends DataLayer {
        final List<String> calls = new ArrayList<>();
        boolean reject;
        Runnable callback;
        DataLayer copied;
        Source() { super(7); }
        @Override public boolean isDefinitelyHomogenous() { calls.add("homogeneous"); return true; }
        @Override public DataLayer copy() {
            calls.add("copy");
            Runnable effect = callback; callback = null;
            if (effect != null) effect.run();
            if (reject) throw FAILURE;
            return copied = new DataLayer(7);
        }
    }
    private static Long2ByteMap states(VerifyThreadedLighting.Fixture fixture, LightLayer channel) throws Exception {
        Object storage = VerifyThreadedLighting.field(fixture.engine.getLayerListener(channel), "storage");
        return (Long2ByteMap)VerifyThreadedLighting.field(storage, "sectionStates");
    }
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            var key = SectionPos.of(0, 0, 0);
            var block = states(fixture, LightLayer.BLOCK);
            block.defaultReturnValue((byte)31);
            fixture.engine.updateSectionStatus(key, false); fixture.transport();
            try { fixture.update(); throw new AssertionError("Missing original neighbor-count failure"); }
            catch (IllegalArgumentException error) {
                System.out.println("STATUS default-error=" + error.getClass().getSimpleName() + ":" + error.getMessage()
                    + " block=" + block.get(key.asLong()) + " sky=" + states(fixture, LightLayer.SKY).containsKey(key.asLong()) + " pending=" + fixture.pending());
            }
            block.defaultReturnValue((byte)0); fixture.update();
            System.out.println("STATUS default-retry block=" + block.get(key.asLong()) + " sky=" + states(fixture, LightLayer.SKY).get(key.asLong()) + " pending=" + fixture.pending());
        }
        for (String mode : new String[]{"success", "throw", "reentry"}) {
            try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
                fixture.engine.updateSectionStatus(SectionPos.of(0, 2, 0), false); fixture.transport(); fixture.update();
                Source source = new Source();
                fixture.engine.queueSectionData(LightLayer.SKY, SectionPos.of(0, 1, 0), source); fixture.transport(); fixture.update();
                source.calls.clear(); source.reject = mode.equals("throw");
                if (mode.equals("reentry")) source.callback = () -> {
                    try { fixture.add(false, () -> source.calls.add("nested-pre")); fixture.transport(); fixture.update(); }
                    catch (Exception error) { throw new RuntimeException(error); }
                };
                var key = SectionPos.of(0, 0, 0);
                fixture.engine.updateSectionStatus(key, false); fixture.transport();
                boolean failed = false;
                try { fixture.update(); }
                catch (RuntimeException error) { if (error != FAILURE) throw error; failed = true; }
                if (failed != mode.equals("throw")) throw new AssertionError("Original copy outcome changed");
                System.out.println("STATUS repeat=" + mode + " failed=" + failed + " pending=" + fixture.pending() + " calls=" + source.calls
                    + " block=" + states(fixture, LightLayer.BLOCK).get(key.asLong()) + " sky=" + states(fixture, LightLayer.SKY).get(key.asLong()));
                if (failed) { source.reject = false; fixture.update(); }
                DataLayer actual = fixture.engine.getLayerListener(LightLayer.SKY).getDataLayerData(key);
                System.out.println("STATUS result=" + mode + " pending=" + fixture.pending() + " null=" + (actual == null)
                    + " alias=" + (actual == source.copied) + " value=" + (actual == null ? -1 : actual.get(0, 0, 0)) + " calls=" + source.calls);
            }
        }
    }
}
