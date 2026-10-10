package net.minecraft.world.level.chunk;

import java.util.List;
import net.minecraft.world.level.block.Blocks;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class LevelChunkSection {
	public static final int SECTION_WIDTH = 16;
	public static final int SECTION_HEIGHT = 16;
	public static final int SECTION_SIZE = 4096;
	public static final int BIOME_CONTAINER_BITS = 2;
	private final NativeSectionCounters counters;
	private final PalettedContainer<BlockState> states;
	private PalettedContainerRO<Holder<Biome>> biomes;

	private LevelChunkSection(LevelChunkSection levelChunkSection) {
		this.counters = new NativeSectionCounters(levelChunkSection.counters.packed());
		this.states = levelChunkSection.states.copy();
		this.installBiomeContainer(levelChunkSection.biomes.copy());
	}

	public LevelChunkSection(PalettedContainer<BlockState> palettedContainer, PalettedContainerRO<Holder<Biome>> palettedContainerRO) {
		this.counters = new NativeSectionCounters(0);
		this.states = palettedContainer;
		this.installBiomeContainer(palettedContainerRO);
		this.recalcBlockCounts();
	}

	public LevelChunkSection(PalettedContainerFactory palettedContainerFactory) {
		this.counters = new NativeSectionCounters(0);
		this.states = palettedContainerFactory.createForBlockStates();
		this.installBiomeContainer(palettedContainerFactory.createForBiomes());
	}

	public BlockState getBlockState(int i, int j, int k) {
		return this.states.get(i, j, k);
	}

	public FluidState getFluidState(int i, int j, int k) {
		return this.states.get(i, j, k).getFluidState();
	}

	public void acquire() {
		this.states.acquire();
	}

	public void release() {
		this.states.release();
	}

	public BlockState setBlockState(int i, int j, int k, BlockState blockState) {
		return this.setBlockState(i, j, k, blockState, true);
	}

	public BlockState setBlockState(int i, int j, int k, BlockState blockState, boolean bl) {
		if (this.getClass() == LevelChunkSection.class) {
			var previous = this.states.trySetWithCounters(i, j, k, blockState, bl, this.counters);
			if (previous != null) return previous;
		}
		BlockState blockState2;
		if (bl) {
			blockState2 = this.states.getAndSet(i, j, k, blockState);
		} else {
			blockState2 = this.states.getAndSetUnchecked(i, j, k, blockState);
		}

		FluidState fluidState = blockState2.getFluidState();
		FluidState fluidState2 = blockState.getFluidState();
		if (!blockState2.isAir()) {
			this.counters.adjust(0, -1, false);
			if (blockState2.isRandomlyTicking()) {
				this.counters.adjust(1, -1, false);
			}
		}

		if (!fluidState.isEmpty()) {
			this.counters.adjust(2, -1, false);
		}

		if (!blockState.isAir()) {
			this.counters.adjust(0, 1, false);
			if (blockState.isRandomlyTicking()) {
				this.counters.adjust(1, 1, false);
			}
		}

		if (!fluidState2.isEmpty()) {
			this.counters.adjust(2, 1, false);
		}

		return blockState2;
	}

	/** A fresh all-air section whose block palette has never grown. */
	public boolean isUntouchedAirForGeneration() {
		return this.counters.packed() == 0
			&& this.states.isUntouched(Blocks.AIR.defaultBlockState());
	}

	public int generatedGlobalPaletteBits() {
		return this.states.globalPaletteBits();
	}

	/** Installs a native NOISE fill result into a fresh section: the block palette
	 * and storage its writes produce and the counters setBlockState would keep. */
	public void installGenerated(int requestedBits, List<BlockState> palette, long[] raw, int nonEmpty, int ticking, int fluid) {
		this.states.installGenerated(requestedBits, palette, raw);
		this.counters.set(nonEmpty, ticking, fluid);
	}

	/** The block container's exact state and this section's counters (non-empty,
	 * ticking blocks, fluids), for a native stage; null for unmodelled containers. */
	@org.jetbrains.annotations.Nullable
	public GeneratedSection exportGenerated() {
		var state = this.states.exportGenerated(net.minecraft.world.level.block.Block::getId);
		return state == null ? null : new GeneratedSection(state.kind(), state.bits(), state.palette(), state.raw(),
			this.counters.get(0), this.counters.get(1), this.counters.get(2));
	}

	/** Native stage pointers are borrowed only while this section is pinned. */
	@org.jetbrains.annotations.Nullable
	NativeLiveBlockSection nativeGenerationInput() {
		if (this.getClass() != LevelChunkSection.class || this.states.getClass() != PalettedContainer.class) return null;
		return this.states.nativeLiveBlocks();
	}
	java.lang.foreign.MemorySegment nativeGenerationCounters() { return this.counters.owner(); }

	/** Coherent signed-short CPU snapshot: nonempty, ticking blocks, fluids in 16-bit lanes. */
	public long packedSectionCounts() { return this.counters.packed(); }

	void installNativeGenerated(NativeLiveBlockSection owner, int nonEmpty, int ticking, int fluid) {
		this.states.installNativeGenerated(owner);
		this.counters.set(nonEmpty, ticking, fluid);
	}

	/** For a native biome fill: the recreated biome container's global palette
	 * bits, or -1 when its container or strategy is not the modelled one. */
	public int generatedBiomeGlobalBits() {
		return this.biomes instanceof PalettedContainer<Holder<Biome>> container ? container.generatedBiomeGlobalBits() : -1;
	}

	/** The registry IDs the biome container's global palette uses. */
	public net.minecraft.core.IdMap<Holder<Biome>> generatedBiomeIds() {
		return ((PalettedContainer<Holder<Biome>>)this.biomes).globalIds();
	}

	/** The value {@code recreate()} starts the new biome container with. */
	public Holder<Biome> generatedBiomeSeed() {
		return this.biomes.recreate().get(0, 0, 0);
	}

	/** Installs a native biome fill: the container {@code fillBiomesFromNoise}'s
	 * 64 writes into {@code recreate()} produce. */
	public void installGeneratedBiomes(int requestedBits, List<Holder<Biome>> palette, long[] raw) {
		PalettedContainer<Holder<Biome>> container = this.biomes.recreate();
		container.installGenerated(requestedBits, palette, raw);
		this.installBiomeContainer(container);
	}

	public record GeneratedSection(int kind, int bits, int[] palette, long[] raw, int nonEmpty, int ticking, int fluid) {}

	public boolean hasOnlyAir() {
		return this.counters.get(0) == 0;
	}

	public boolean isRandomlyTicking() {
		return this.isRandomlyTickingBlocks() || this.isRandomlyTickingFluids();
	}

	public boolean isRandomlyTickingBlocks() {
		return this.counters.get(1) > 0;
	}

	public boolean isRandomlyTickingFluids() {
		return this.counters.get(2) > 0;
	}

	public void recalcBlockCounts() {
		if (this.getClass() == LevelChunkSection.class && this.states.getClass() == PalettedContainer.class) {
			var live = this.states.nativeLiveBlocks();
			if (live != null && live.recount(this.counters)) return;
		}
		class BlockCounter implements PalettedContainer.CountConsumer<BlockState> {
			public int nonEmptyBlockCount;
			public int tickingBlockCount;
			public int tickingFluidCount;

			public void accept(BlockState blockState, int i) {
				FluidState fluidState = blockState.getFluidState();
				if (!blockState.isAir()) {
					this.nonEmptyBlockCount += i;
					if (blockState.isRandomlyTicking()) {
						this.tickingBlockCount += i;
					}
				}

				if (!fluidState.isEmpty()) {
					this.nonEmptyBlockCount += i;
					if (fluidState.isRandomlyTicking()) {
						this.tickingFluidCount += i;
					}
				}
			}
		}

		BlockCounter lv = new BlockCounter();
		this.states.count(lv);
		this.counters.set(lv.nonEmptyBlockCount, lv.tickingBlockCount, lv.tickingFluidCount);
	}

	public PalettedContainer<BlockState> getStates() {
		return this.states;
	}

	private Runnable nativeBiomeListener;

	/** Loaded client index notification; no listener exists during world generation. */
	public void setNativeBiomeListener(Runnable listener) { this.nativeBiomeListener = listener; }

	private void installBiomeContainer(PalettedContainerRO<Holder<Biome>> container) {
		this.biomes = container;
		if (this.nativeBiomeListener != null) this.nativeBiomeListener.run();
	}

	public PalettedContainerRO<Holder<Biome>> getBiomes() {
		return this.biomes;
	}

	public void read(FriendlyByteBuf friendlyByteBuf) {
		this.counters.adjust(0, friendlyByteBuf.readShort(), true);
		this.states.read(friendlyByteBuf);
		PalettedContainer<Holder<Biome>> palettedContainer = this.biomes.recreate();
		palettedContainer.read(friendlyByteBuf);
		this.installBiomeContainer(palettedContainer);
	}

	public void readBiomes(FriendlyByteBuf friendlyByteBuf) {
		PalettedContainer<Holder<Biome>> palettedContainer = this.biomes.recreate();
		palettedContainer.read(friendlyByteBuf);
		this.installBiomeContainer(palettedContainer);
	}

	public void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeShort(this.counters.get(0));
		this.states.write(friendlyByteBuf);
		this.biomes.write(friendlyByteBuf);
	}

	public int getSerializedSize() {
		return 2 + this.states.getSerializedSize() + this.biomes.getSerializedSize();
	}

	public boolean maybeHas(Predicate<BlockState> predicate) {
		return this.states.maybeHas(predicate);
	}

	public Holder<Biome> getNoiseBiome(int i, int j, int k) {
		return this.biomes.get(i, j, k);
	}

	public void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler, int i, int j, int k) {
		PalettedContainer<Holder<Biome>> palettedContainer = this.biomes.recreate();
        if (biomeResolver instanceof net.minecraft.world.level.biome.MultiNoiseBiomeSource source) {
            @SuppressWarnings("unchecked")
            Holder<Biome>[] values = (Holder<Biome>[])new Holder<?>[64];
            if (source.fillBiomeSection(sampler, i, j, k, values)) {
                int index = 0;
                for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++) for (int dz = 0; dz < 4; dz++) {
                    palettedContainer.getAndSetUnchecked(dx, dy, dz, values[index++]);
                }
                this.installBiomeContainer(palettedContainer);
                return;
            }
        }
        int l = 4;

		for (int m = 0; m < 4; m++) {
			for (int n = 0; n < 4; n++) {
				for (int o = 0; o < 4; o++) {
					palettedContainer.getAndSetUnchecked(m, n, o, biomeResolver.getNoiseBiome(i + m, j + n, k + o, sampler));
				}
			}
		}

		this.installBiomeContainer(palettedContainer);
	}

	public LevelChunkSection copy() {
		return new LevelChunkSection(this);
	}
}
