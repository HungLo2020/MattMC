package net.minecraft.world.level.levelgen;

import com.mojang.serialization.JsonOps;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine VM field replacement, first verified on original Frozen and pre-f5. */
final class GaussianFieldContractTest {
    static java.lang.reflect.Field field() throws Exception {
        var field = XoroshiroRandomSource.class.getDeclaredField("gaussianSource");
        field.setAccessible(true);
        return field;
    }
    static MarsagliaPolarGaussian get(XoroshiroRandomSource source) throws Exception {
        return (MarsagliaPolarGaussian) field().get(source);
    }
    static Object state(XoroshiroRandomSource source) {
        return XoroshiroRandomSource.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
    }
    static void same(double a, double b) {
        assertEquals(Double.doubleToRawLongBits(a), Double.doubleToRawLongBits(b));
    }
    @Test void replacementResetsItsOwnCacheAndKeepsTheEscapedOriginalIndependent() throws Exception {
        var source = new XoroshiroRandomSource(42);
        var escaped = get(source);
        var original = new XoroshiroRandomSource(42);
        escaped.nextGaussian(); get(original).nextGaussian();
        var replacement = new MarsagliaPolarGaussian(source);
        field().set(source, replacement);
        replacement.nextGaussian(); source.setSeed(19);
        same(get(original).nextGaussian(), escaped.nextGaussian());
        same(get(new XoroshiroRandomSource(19)).nextGaussian(), replacement.nextGaussian());
    }
    @Test void nullReplacementFailsAfterSeedCommitAtItsOriginalResetEffect() throws Exception {
        var source = new XoroshiroRandomSource(42);
        field().set(source, null);
        assertThrows(NullPointerException.class, () -> source.setSeed(19));
        assertEquals(state(new XoroshiroRandomSource(19)), state(source));
    }
    @SuppressWarnings("unchecked") static <T, E extends Throwable> T raise(Throwable failure) throws E { throw (E) failure; }
    static final class Custom extends MarsagliaPolarGaussian {
        final IOException sentinel = new IOException("original");
        int calls;
        Custom(XoroshiroRandomSource source) { super(source); }
        @Override public void reset() { calls++; GaussianFieldContractTest.<Void, RuntimeException>raise(sentinel); }
    }
    @Test void actualVirtualResetPreservesOriginalCheckedThrowableAndSeedPrefix() throws Exception {
        var source = new XoroshiroRandomSource(42);
        var custom = new Custom(source);
        field().set(source, custom);
        assertSame(custom.sentinel, assertThrows(IOException.class, () -> source.setSeed(19)));
        assertEquals(1, custom.calls);
        assertEquals(state(new XoroshiroRandomSource(19)), state(source));
    }
}
