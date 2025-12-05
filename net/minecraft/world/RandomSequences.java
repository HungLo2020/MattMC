package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class RandomSequences extends SavedData {
	public static final SavedDataType<RandomSequences> TYPE = new SavedDataType<>(
		"random_sequences", context -> new RandomSequences(context.worldSeed()), context -> codec(context.worldSeed()), DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
	);
	private final long worldSeed;
	private int salt;
	private boolean includeWorldSeed = true;
	private boolean includeSequenceId = true;
	private final Map<ResourceLocation, RandomSequence> sequences = new Object2ObjectOpenHashMap<>();

	public RandomSequences(long l) {
		this.worldSeed = l;
	}

	private RandomSequences(long l, int i, boolean bl, boolean bl2, Map<ResourceLocation, RandomSequence> map) {
		this.worldSeed = l;
		this.salt = i;
		this.includeWorldSeed = bl;
		this.includeSequenceId = bl2;
		this.sequences.putAll(map);
	}

	public static Codec<RandomSequences> codec(long l) {
		return RecordCodecBuilder.create(
			instance -> instance.group(
					RecordCodecBuilder.point(l),
					Codec.INT.fieldOf("salt").forGetter(randomSequences -> randomSequences.salt),
					Codec.BOOL.optionalFieldOf("include_world_seed", true).forGetter(randomSequences -> randomSequences.includeWorldSeed),
					Codec.BOOL.optionalFieldOf("include_sequence_id", true).forGetter(randomSequences -> randomSequences.includeSequenceId),
					Codec.unboundedMap(ResourceLocation.CODEC, RandomSequence.CODEC).fieldOf("sequences").forGetter(randomSequences -> randomSequences.sequences)
				)
				.apply(instance, RandomSequences::new)
		);
	}

	public RandomSource get(ResourceLocation resourceLocation) {
		RandomSource randomSource = ((RandomSequence)this.sequences.computeIfAbsent(resourceLocation, this::createSequence)).random();
		return new RandomSequences.DirtyMarkingRandomSource(randomSource);
	}

	private RandomSequence createSequence(ResourceLocation resourceLocation) {
		return this.createSequence(resourceLocation, this.salt, this.includeWorldSeed, this.includeSequenceId);
	}

	private RandomSequence createSequence(ResourceLocation resourceLocation, int i, boolean bl, boolean bl2) {
		long l = (bl ? this.worldSeed : 0L) ^ i;
		return new RandomSequence(l, bl2 ? Optional.of(resourceLocation) : Optional.empty());
	}

	public void forAllSequences(BiConsumer<ResourceLocation, RandomSequence> biConsumer) {
		this.sequences.forEach(biConsumer);
	}

	public void setSeedDefaults(int i, boolean bl, boolean bl2) {
		this.salt = i;
		this.includeWorldSeed = bl;
		this.includeSequenceId = bl2;
	}

	public int clear() {
		int i = this.sequences.size();
		this.sequences.clear();
		return i;
	}

	public void reset(ResourceLocation resourceLocation) {
		this.sequences.put(resourceLocation, this.createSequence(resourceLocation));
	}

	public void reset(ResourceLocation resourceLocation, int i, boolean bl, boolean bl2) {
		this.sequences.put(resourceLocation, this.createSequence(resourceLocation, i, bl, bl2));
	}

	class DirtyMarkingRandomSource implements RandomSource {
		private final RandomSource random;

		DirtyMarkingRandomSource(final RandomSource randomSource) {
			this.random = randomSource;
		}

		@Override
		public RandomSource fork() {
			RandomSequences.this.setDirty();
			return this.random.fork();
		}

		@Override
		public PositionalRandomFactory forkPositional() {
			RandomSequences.this.setDirty();
			return this.random.forkPositional();
		}

		@Override
		public void setSeed(long l) {
			RandomSequences.this.setDirty();
			this.random.setSeed(l);
		}

		@Override
		public int nextInt() {
			RandomSequences.this.setDirty();
			return this.random.nextInt();
		}

		@Override
		public int nextInt(int i) {
			RandomSequences.this.setDirty();
			return this.random.nextInt(i);
		}

		@Override
		public long nextLong() {
			RandomSequences.this.setDirty();
			return this.random.nextLong();
		}

		@Override
		public boolean nextBoolean() {
			RandomSequences.this.setDirty();
			return this.random.nextBoolean();
		}

		@Override
		public float nextFloat() {
			RandomSequences.this.setDirty();
			return this.random.nextFloat();
		}

		@Override
		public double nextDouble() {
			RandomSequences.this.setDirty();
			return this.random.nextDouble();
		}

		@Override
		public double nextGaussian() {
			RandomSequences.this.setDirty();
			return this.random.nextGaussian();
		}

		public boolean equals(Object object) {
			if (this == object) {
				return true;
			} else {
				return object instanceof RandomSequences.DirtyMarkingRandomSource dirtyMarkingRandomSource ? this.random.equals(dirtyMarkingRandomSource.random) : false;
			}
		}
	}
}
