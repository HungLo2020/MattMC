package net.minecraft.world.level.biome;

import com.google.common.hash.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;

public class BiomeManager {
	public static final int CHUNK_CENTER_QUART = QuartPos.fromBlock(8);
	private final BiomeManager.NoiseBiomeSource noiseBiomeSource;
	public final long biomeZoomSeed;

	public BiomeManager(BiomeManager.NoiseBiomeSource noiseBiomeSource, long l) {
		this.noiseBiomeSource = noiseBiomeSource;
		this.biomeZoomSeed = l;
	}

	public static long obfuscateSeed(long l) {
		return Hashing.sha256().hashLong(l).asLong();
	}

	public BiomeManager withDifferentSource(BiomeManager.NoiseBiomeSource noiseBiomeSource) {
		return new BiomeManager(noiseBiomeSource, this.biomeZoomSeed);
	}

	public Holder<Biome> getBiome(BlockPos blockPos) {
		return this.noiseBiomeSource.getBiomeAtBlock(this.biomeZoomSeed, blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	public Holder<Biome> getNoiseBiomeAtPosition(double d, double e, double f) {
		int i = QuartPos.fromBlock(Mth.floor(d));
		int j = QuartPos.fromBlock(Mth.floor(e));
		int k = QuartPos.fromBlock(Mth.floor(f));
		return this.getNoiseBiomeAtQuart(i, j, k);
	}

	public Holder<Biome> getNoiseBiomeAtPosition(BlockPos blockPos) {
		int i = QuartPos.fromBlock(blockPos.getX());
		int j = QuartPos.fromBlock(blockPos.getY());
		int k = QuartPos.fromBlock(blockPos.getZ());
		return this.getNoiseBiomeAtQuart(i, j, k);
	}

	/** Admit direct world consumers only when this immutable source is their world. */
	public boolean usesNoiseBiomeSource(BiomeManager.NoiseBiomeSource source) {
		return this.noiseBiomeSource == source;
	}

	public Holder<Biome> getNoiseBiomeAtQuart(int i, int j, int k) {
		return this.noiseBiomeSource.getNoiseBiome(i, j, k);
	}

	public interface NoiseBiomeSource {
		Holder<Biome> getNoiseBiome(int i, int j, int k);

		/** Rust selects the source coordinate; custom providers keep their one ordered callback. */
		default Holder<Biome> getBiomeAtBlock(long seed, int x, int y, int z) {
			int corner = NativeBiomeQuery.corner(seed, x, y, z);
			return this.getNoiseBiome(((x - 2) >> 2) + (corner >> 2),
				((y - 2) >> 2) + ((corner >> 1) & 1), ((z - 2) >> 2) + (corner & 1));
		}
	}
}
