package net.minecraft.server.packs.metadata.pack;

import java.util.Locale;
import net.minecraft.util.InclusiveRange;

/**
 * A pack format version. Decoding and validation of `pack.mcmeta` format
 * declarations live in Rust ({@link NativePackFormats}).
 */
public record PackFormat(int major, int minor) implements Comparable<PackFormat> {
	public static PackFormat of(int i, int j) {
		return new PackFormat(i, j);
	}

	public static PackFormat of(int i) {
		return new PackFormat(i, 0);
	}

	public InclusiveRange<PackFormat> minorRange() {
		return new InclusiveRange(this, of(this.major, Integer.MAX_VALUE));
	}

	public int compareTo(PackFormat packFormat) {
		int i = Integer.compare(this.major(), packFormat.major());
		return i != 0 ? i : Integer.compare(this.minor(), packFormat.minor());
	}

	public String toString() {
		return this.minor == Integer.MAX_VALUE ? String.format(Locale.ROOT, "%d.*", this.major()) : String.format(Locale.ROOT, "%d.%d", this.major(), this.minor());
	}
}
