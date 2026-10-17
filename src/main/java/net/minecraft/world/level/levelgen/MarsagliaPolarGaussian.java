package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

public class MarsagliaPolarGaussian {
    public final RandomSource randomSource;
    private final NativeXoroshiroSource sourceCache;
    private final NativeLegacySource legacyCache;
    private NativeGaussian detached;

    public MarsagliaPolarGaussian(RandomSource randomSource) {
        this(randomSource, null, null);
    }

    private MarsagliaPolarGaussian(RandomSource randomSource, NativeXoroshiroSource sourceCache, NativeLegacySource legacyCache) {
        this.randomSource = randomSource;
        this.sourceCache = sourceCache;
        this.legacyCache = legacyCache;
    }

    static MarsagliaPolarGaussian nativeSource(XoroshiroRandomSource source, NativeXoroshiroSource owner) {
        return new MarsagliaPolarGaussian(source, owner, null);
    }

    static MarsagliaPolarGaussian nativeSource(LegacyRandomSource source, NativeLegacySource owner) {
        return new MarsagliaPolarGaussian(source, null, owner);
    }
    final boolean nativeUsesSourceCache(NativeLegacySource source) { return this.legacyCache == source; }

    // This immutable native-backend projection survives replacement of the
    // source Gaussian field or public VM draw target; it stores no cache flag.
    final boolean nativeUsesSourceCache(NativeXoroshiroSource source) {
        return this.sourceCache == source;
    }

    private NativeXoroshiroSource sourceOwner() {
        return this.sourceCache;
    }

    private NativeGaussian detached() {
        if (this.detached == null) this.detached = new NativeGaussian();
        return this.detached;
    }

    /** The actual default cache is embedded in its native source owner. */
    public boolean nativeHas() {
        if (legacyCache != null) return NativeGaussian.has(legacyCache);
        var source = this.sourceOwner();
        return source != null ? NativeGaussian.has(source) : this.detached != null && this.detached.has();
    }

    public double nativeValue() {
        if (legacyCache != null) return NativeGaussian.value(legacyCache);
        var source = this.sourceOwner();
        return source != null ? NativeGaussian.value(source) : this.detached == null ? 0.0 : this.detached.value();
    }

    public void nativeSet(boolean has, double value) {
        if (legacyCache != null) { NativeGaussian.set(legacyCache, has, value); return; }
        var source = this.sourceOwner();
        if (source != null) NativeGaussian.set(source, has, value);
        else this.detached().set(has, value);
    }

    public void reset() {
        if (legacyCache != null) { NativeGaussian.reset(legacyCache); return; }
        var source = this.sourceOwner();
        if (source != null) NativeGaussian.reset(source);
        else if (this.detached != null) this.detached.reset();
    }

    // Keep the original VM field access and actual virtual source dispatch.
    final double nativeGaussianNextDouble() {
        return this.randomSource.nextDouble();
    }

    public double nextGaussian() {
        if (legacyCache != null) return NativeGaussian.draw(legacyCache, this, XoroshiroRandomSource.nativeGaussianProducer(this.randomSource));
        var cache = this.sourceOwner();
        var producer = XoroshiroRandomSource.nativeGaussianProducer(this.randomSource);
        return cache != null ? NativeGaussian.draw(cache, this, producer) : this.detached().draw(this, producer);
    }
}
