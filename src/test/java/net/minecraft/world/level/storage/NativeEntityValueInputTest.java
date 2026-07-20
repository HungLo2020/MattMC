package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.nbt.NbtBenchmarkAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.NativeEntityStorage;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeEntityValueInputTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		System.setProperty("net.bytebuddy.experimental", "true");
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
	}

	@Test
	void readsEntityLoadFieldsLikeTagValueInput() throws IOException {
		CompoundTag entity = entity("minecraft:pig", passenger("minecraft:chicken"));
		byte[] tape = NativeNbtRegionAccess.writeTape(entity);

		ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), entity);
		ValueInput nativeInput = NativeEntityValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tape);

		assertMatchingEntityLoadFields(tagInput, nativeInput);
	}

	@Test
	void readsCodecBackedSubtreesWithoutWholeDocumentFallback() throws IOException {
		CompoundTag entity = entity("minecraft:item");
		CompoundTag customData = new CompoundTag();
		customData.putString("owner", "MattMC");
		customData.putInt("level", 7);
		entity.put("data", customData);
		byte[] tape = NativeNbtRegionAccess.writeTape(entity);

		ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), entity);
		ValueInput nativeInput = NativeEntityValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tape);

		assertEquals(tagInput.read("id", EntityType.CODEC), nativeInput.read("id", EntityType.CODEC));
		assertEquals(tagInput.read("data", CustomData.CODEC), nativeInput.read("data", CustomData.CODEC));
		assertEquals(tagInput.read("Pos", Vec3.CODEC), nativeInput.read("Pos", Vec3.CODEC));
		assertMatchingVec2(tagInput.read("Rotation", Vec2.CODEC), nativeInput.read("Rotation", Vec2.CODEC));
	}

	@Test
	void rejectsMalformedNativeTape() {
		assertThrows(IOException.class, () -> NativeEntityValueInput.create(ProblemReporter.DISCARDING, registryAccess(), new byte[]{1, 2, 3, 4}));
	}

	@Test
	void copiedRealWorldEntityTapeMatchesTagValueInputWhenAvailable(@TempDir Path tempDir) throws IOException {
		Path entitiesDir = Path.of("run", "saves", "Origin", "entities");
		assumeTrue(Files.isDirectory(entitiesDir), "Origin entity directory is not available");
		RegionChunk sourceChunk = firstPresentOriginEntityChunk(entitiesDir);
		assumeTrue(sourceChunk != null, "Origin entity regions have no populated chunks");
		Path copy = tempDir.resolve(sourceChunk.regionPath().getFileName());
		Files.copy(sourceChunk.regionPath(), copy);
		ChunkPos pos = new ChunkPos(sourceChunk.regionX() * 32 + sourceChunk.localX(), sourceChunk.regionZ() * 32 + sourceChunk.localZ());

		try (NativeEntityStorage storage = NativeEntityStorage.open(copy)) {
			NativeEntityStorage.DecodeResult result = storage.decodeChunk(pos.x, pos.z);
			assumeTrue(!result.entities().isEmpty(), "Origin entity chunk has no root entities");
			assumeTrue(!result.result().requiresDfu(), "Origin entity chunk remains Java DFU-owned");
			NativeEntityStorage.EntityRecord record = result.entities().getFirst();
			CompoundTag tag = record.readTapeAsTag();

			ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tag);
			ValueInput nativeInput = NativeEntityValueInput.create(ProblemReporter.DISCARDING, registryAccess(), record.nbtTape());
			assertMatchingEntityLoadFields(tagInput, nativeInput);
			assertEquals(0L, record.fingerprint());
		}
	}

	@Test
	void warmBenchmarkSeparatesTapeIndexingConstructionReadsAndCodecMaterialization() throws Exception {
		CompoundTag entity = entity("minecraft:area_effect_cloud", itemEntity("minecraft:item"));
		CompoundTag customData = new CompoundTag();
		customData.putString("owner", "MattMC");
		customData.putInt("level", 7);
		entity.put("data", customData);
		byte[] tape = NativeNbtRegionAccess.writeTape(entity);
		int warmup = 64;
		int samples = 128;

		SampleSet tapeIndexing = sample(warmup, samples, () -> NativeEntityValueInput.indexTapeForTesting(tape));
		SampleSet compoundConstruction = sample(warmup, samples, () -> NativeNbtRegionAccess.readTape(tape));
		NativeEntityValueInput.TapeIndex indexed = NativeEntityValueInput.indexTapeForTesting(tape);
		SampleSet nativeConstruction = sample(warmup, samples, () -> NativeEntityValueInput.createForTesting(ProblemReporter.DISCARDING, registryAccess(), indexed));
		CompoundTag materialized = NativeNbtRegionAccess.readTape(tape);
		ValueInput tagInput = TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), materialized);
		ValueInput nativeInput = NativeEntityValueInput.createForTesting(ProblemReporter.DISCARDING, registryAccess(), indexed);
		SampleSet tagReads = sample(warmup, samples, () -> readRepresentativeEntityFields(tagInput));
		SampleSet nativeReads = sample(warmup, samples, () -> readRepresentativeEntityFields(nativeInput));
		SampleSet codecSubtree = sample(warmup, samples, () -> nativeInput.read("data", CustomData.CODEC));

		assertEquals(rootlessFingerprint(entity), rootlessFingerprint(materialized));
		System.out.println("NativeEntityValueInput warm samples: tapeIndexing=" + tapeIndexing);
		System.out.println("NativeEntityValueInput warm samples: compoundConstruction=" + compoundConstruction);
		System.out.println("NativeEntityValueInput warm samples: nativeConstructionFromIndex=" + nativeConstruction);
		System.out.println("NativeEntityValueInput warm samples: tagRepresentativeReads=" + tagReads);
		System.out.println("NativeEntityValueInput warm samples: nativeRepresentativeReads=" + nativeReads);
		System.out.println("NativeEntityValueInput warm samples: nativeCodecSubtreeMaterialization=" + codecSubtree);
	}

	@Test
	void fullRecursiveEntityLoadParityMatchesTagValueInput() throws Exception {
		List<CompoundTag> entities = List.of(entity("minecraft:area_effect_cloud", entity("minecraft:armor_stand")), entity("minecraft:armor_stand"));

		LoadedEntities tagLoaded = loadThroughTagValueInput(entities);
		LoadedEntities nativeLoaded = loadThroughNativeValueInput(entities);

		assertRecursiveParity(tagLoaded, nativeLoaded);
		assertEquals(3, tagLoaded.entities().size());
		assertFalse(tagLoaded.entities().get(0).getPassengers().isEmpty());
	}

	@Test
	void fullRecursiveParitySkipsUnknownAndMalformedEntitiesLikeTagValueInput() throws Exception {
		CompoundTag malformed = entity("minecraft:armor_stand");
		malformed.putInt("id", 7);
		List<CompoundTag> entities = List.of(malformed, entity("mattmc:not_registered"), entity("minecraft:armor_stand"));

		LoadedEntities tagLoaded = loadThroughTagValueInput(entities);
		LoadedEntities nativeLoaded = loadThroughNativeValueInput(entities);

		assertRecursiveParity(tagLoaded, nativeLoaded);
		assertEquals(1, tagLoaded.entities().size());
	}

	@Test
	void copiedRealWorldEntityChunkRecursiveLoadParityWhenAvailable(@TempDir Path tempDir) throws Exception {
		Path entitiesDir = Path.of("run", "saves", "Origin", "entities");
		assumeTrue(Files.isDirectory(entitiesDir), "Origin entity directory is not available");
		RegionChunk sourceChunk = firstPresentOriginEntityChunk(entitiesDir);
		assumeTrue(sourceChunk != null, "Origin entity regions have no populated chunks");
		Path copy = tempDir.resolve(sourceChunk.regionPath().getFileName());
		Files.copy(sourceChunk.regionPath(), copy);
		ChunkPos pos = new ChunkPos(sourceChunk.regionX() * 32 + sourceChunk.localX(), sourceChunk.regionZ() * 32 + sourceChunk.localZ());

		try (NativeEntityStorage storage = NativeEntityStorage.open(copy)) {
			NativeEntityStorage.DecodeResult result = storage.decodeChunk(pos.x, pos.z);
			assumeTrue(!result.result().requiresDfu(), "Origin entity chunk remains Java DFU-owned");
			assumeTrue(!result.entities().isEmpty(), "Origin entity chunk has no root entities");
			List<CompoundTag> tags = new ArrayList<>();
			for (NativeEntityStorage.EntityRecord record : result.entities()) {
				tags.add(record.readTapeAsTag());
			}

			LoadedEntities tagLoaded = loadThroughTagValueInput(tags);
			LoadedEntities nativeLoaded = loadThroughNativeValueInput(tags);
			assumeTrue(!tagLoaded.entities().isEmpty(), "Mocked test level could not construct any real Origin entities");
			assertRecursiveParity(tagLoaded, nativeLoaded);
		}
	}

	private static void assertMatchingEntityLoadFields(ValueInput expected, ValueInput actual) {
		assertEquals(expected.read("id", EntityType.CODEC), actual.read("id", EntityType.CODEC));
		assertEquals(expected.read("UUID", UUIDUtil.CODEC), actual.read("UUID", UUIDUtil.CODEC));
		assertEquals(expected.read("Pos", Vec3.CODEC), actual.read("Pos", Vec3.CODEC));
		assertEquals(expected.read("Motion", Vec3.CODEC), actual.read("Motion", Vec3.CODEC));
		assertMatchingVec2(expected.read("Rotation", Vec2.CODEC), actual.read("Rotation", Vec2.CODEC));
		assertEquals(expected.getDoubleOr("fall_distance", 0.0), actual.getDoubleOr("fall_distance", 0.0));
		assertEquals(expected.getShortOr("Fire", (short)0), actual.getShortOr("Fire", (short)0));
		assertEquals(expected.getIntOr("Air", 300), actual.getIntOr("Air", 300));
		assertEquals(expected.getBooleanOr("OnGround", false), actual.getBooleanOr("OnGround", false));
		assertEquals(expected.getBooleanOr("Invulnerable", false), actual.getBooleanOr("Invulnerable", false));
		assertEquals(expected.getBooleanOr("Silent", false), actual.getBooleanOr("Silent", false));
		assertEquals(expected.getBooleanOr("NoGravity", false), actual.getBooleanOr("NoGravity", false));
		assertEquals(expected.getBooleanOr("Glowing", false), actual.getBooleanOr("Glowing", false));
		assertEquals(expected.getIntOr("TicksFrozen", 0), actual.getIntOr("TicksFrozen", 0));
		assertEquals(expected.getBooleanOr("HasVisualFire", false), actual.getBooleanOr("HasVisualFire", false));
		assertArrayEquals(expected.getIntArray("UUID").orElseThrow(), actual.getIntArray("UUID").orElseThrow());
		assertEquals(expected.read("Tags", Codec.STRING.sizeLimitedListOf(1024)), actual.read("Tags", Codec.STRING.sizeLimitedListOf(1024)));
		assertPassengerShape(expected.childrenListOrEmpty("Passengers"), actual.childrenListOrEmpty("Passengers"));
	}

	private static void readRepresentativeEntityFields(ValueInput input) {
		assertTrue(input.read("id", EntityType.CODEC).isPresent());
		assertTrue(input.read("UUID", UUIDUtil.CODEC).isPresent());
		assertTrue(input.read("Pos", Vec3.CODEC).isPresent());
		assertTrue(input.read("Motion", Vec3.CODEC).isPresent());
		assertTrue(input.read("Rotation", Vec2.CODEC).isPresent());
		assertFalse(input.childrenListOrEmpty("Passengers").isEmpty());
	}

	private static LoadedEntities loadThroughTagValueInput(List<CompoundTag> entities) throws IOException {
		Level level = controlledLevel();
		ValueInput.ValueInputList list = TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), entities);
		List<Entity> loaded = EntityType.loadEntitiesRecursive(list, level, EntitySpawnReason.LOAD).toList();
		return new LoadedEntities(loaded, saveAll(loaded));
	}

	private static LoadedEntities loadThroughNativeValueInput(List<CompoundTag> entities) throws IOException {
		Level level = controlledLevel();
		List<byte[]> tapes = new ArrayList<>(entities.size());
		for (CompoundTag entity : entities) {
			tapes.add(NativeNbtRegionAccess.writeTape(entity));
		}
		ValueInput.ValueInputList list = NativeEntityValueInput.createListForTesting(ProblemReporter.DISCARDING, registryAccess(), tapes);
		List<Entity> loaded = EntityType.loadEntitiesRecursive(list, level, EntitySpawnReason.LOAD).toList();
		return new LoadedEntities(loaded, saveAll(loaded));
	}

	private static List<CompoundTag> saveAll(List<Entity> entities) {
		List<CompoundTag> saved = new ArrayList<>(entities.size());
		for (Entity entity : entities) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
			assertTrue(entity.saveAsPassenger(output));
			saved.add(output.buildResult());
		}
		return List.copyOf(saved);
	}

	private static void assertRecursiveParity(LoadedEntities expected, LoadedEntities actual) throws IOException {
		assertEquals(expected.entities().size(), actual.entities().size());
		assertEquals(expected.saved().size(), actual.saved().size());
		for (int i = 0; i < expected.entities().size(); i++) {
			assertEntityShape(expected.entities().get(i), actual.entities().get(i));
			assertEquals(rootlessFingerprint(expected.saved().get(i)), rootlessFingerprint(actual.saved().get(i)));
		}
	}

	private static void assertEntityShape(Entity expected, Entity actual) {
		assertEquals(EntityType.getKey(expected.getType()), EntityType.getKey(actual.getType()));
		assertEquals(expected.getUUID(), actual.getUUID());
		assertEquals(expected.getX(), actual.getX());
		assertEquals(expected.getY(), actual.getY());
		assertEquals(expected.getZ(), actual.getZ());
		assertEquals(expected.getYRot(), actual.getYRot());
		assertEquals(expected.getXRot(), actual.getXRot());
		assertEquals(expected.getPassengers().size(), actual.getPassengers().size());
		for (int i = 0; i < expected.getPassengers().size(); i++) {
			assertEntityShape(expected.getPassengers().get(i), actual.getPassengers().get(i));
		}
	}

	private static void assertPassengerShape(ValueInput.ValueInputList expected, ValueInput.ValueInputList actual) {
		List<ValueInput> expectedPassengers = expected.stream().toList();
		List<ValueInput> actualPassengers = actual.stream().toList();
		assertEquals(expectedPassengers.size(), actualPassengers.size());
		for (int i = 0; i < expectedPassengers.size(); i++) {
			assertEquals(expectedPassengers.get(i).read("id", EntityType.CODEC), actualPassengers.get(i).read("id", EntityType.CODEC));
			assertPassengerShape(expectedPassengers.get(i).childrenListOrEmpty("Passengers"), actualPassengers.get(i).childrenListOrEmpty("Passengers"));
		}
	}

	private static void assertMatchingVec2(Optional<Vec2> expected, Optional<Vec2> actual) {
		assertEquals(expected.isPresent(), actual.isPresent());
		if (expected.isPresent()) {
			assertEquals(expected.orElseThrow().x, actual.orElseThrow().x);
			assertEquals(expected.orElseThrow().y, actual.orElseThrow().y);
		}
	}

	private static CompoundTag entity(String id, CompoundTag... passengers) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		UUID uuid = new UUID(0x12345678FFFFFFFFL, 0x0102030400000005L);
		tag.putIntArray("UUID", UUIDUtil.uuidToIntArray(uuid));
		tag.put("Pos", doubleList(1.25, 64.0, -2.5));
		tag.put("Motion", doubleList(0.0625, 0.0, -0.125));
		tag.put("Rotation", floatList(90.0F, 12.5F));
		tag.putDouble("fall_distance", 1.5);
		tag.putShort("Fire", (short)20);
		tag.putInt("Air", 250);
		tag.putBoolean("OnGround", true);
		tag.putBoolean("Invulnerable", false);
		tag.putInt("PortalCooldown", 5);
		tag.putBoolean("Silent", true);
		tag.putBoolean("NoGravity", false);
		tag.putBoolean("Glowing", true);
		tag.putInt("TicksFrozen", 3);
		tag.putBoolean("HasVisualFire", true);
		if (id.equals("minecraft:area_effect_cloud")) {
			tag.putInt("Age", 12);
			tag.putInt("Duration", 200);
			tag.putInt("WaitTime", 5);
			tag.putFloat("Radius", 2.5F);
			tag.putFloat("RadiusOnUse", -0.25F);
			tag.putFloat("RadiusPerTick", -0.01F);
		} else if (id.equals("minecraft:armor_stand")) {
			tag.putBoolean("Invisible", true);
			tag.putBoolean("Small", true);
			tag.putBoolean("ShowArms", true);
			tag.putBoolean("NoBasePlate", true);
			tag.putInt("DisabledSlots", 7);
		}
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf("native-value-input"));
		tags.add(StringTag.valueOf("parity"));
		tag.put("Tags", tags);
		if (passengers.length > 0) {
			ListTag list = new ListTag();
			for (CompoundTag passenger : passengers) {
				list.add(passenger);
			}
			tag.put("Passengers", list);
		}
		return tag;
	}

	private static CompoundTag itemEntity(String id) {
		CompoundTag tag = entity(id);
		tag.putShort("Health", (short)5);
		tag.putShort("Age", (short)10);
		tag.putShort("PickupDelay", (short)2);
		DynamicOps<Tag> ops = registryAccess().createSerializationContext(NbtOps.INSTANCE);
		tag.store("Item", ItemStack.CODEC, ops, new ItemStack(Items.DIAMOND_SWORD));
		return tag;
	}

	private static CompoundTag passenger(String id) {
		return entity(id);
	}

	private static ListTag doubleList(double... values) {
		ListTag list = new ListTag();
		for (double value : values) {
			list.add(DoubleTag.valueOf(value));
		}
		return list;
	}

	private static ListTag floatList(float... values) {
		ListTag list = new ListTag();
		for (float value : values) {
			list.add(FloatTag.valueOf(value));
		}
		return list;
	}

	private static long rootlessFingerprint(CompoundTag tag) throws IOException {
		return NbtBenchmarkAccess.implementationFingerprint(NbtBenchmarkAccess.writeObject(tag, NbtBenchmarkAccess.FORMAT_RAW), NbtBenchmarkAccess.FORMAT_RAW);
	}

	private static SampleSet sample(int warmup, int samples, TimedOperation operation) throws Exception {
		for (int i = 0; i < warmup; i++) {
			operation.run();
		}
		long[] values = new long[samples];
		for (int i = 0; i < samples; i++) {
			long start = System.nanoTime();
			operation.run();
			values[i] = System.nanoTime() - start;
		}
		return new SampleSet(values);
	}

	private static RegistryAccess.Frozen registryAccess() {
		return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}

	private static Level controlledLevel() {
		Level level = mock(Level.class);
		when(level.registryAccess()).thenReturn(registryAccess());
		when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
		when(level.isClientSide()).thenReturn(false);
		return level;
	}

	private static RegionChunk firstPresentOriginEntityChunk(Path entitiesDir) throws IOException {
		try (Stream<Path> paths = Files.list(entitiesDir)) {
			for (Path path : paths.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted().toList()) {
				ChunkPos pos = firstPresentChunk(path);
				if (pos != null) {
					String[] parts = path.getFileName().toString().split("\\.");
					int regionX = Integer.parseInt(parts[1]);
					int regionZ = Integer.parseInt(parts[2]);
					return new RegionChunk(path, regionX, regionZ, pos.x, pos.z);
				}
			}
		}
		return null;
	}

	private static ChunkPos firstPresentChunk(Path regionPath) throws IOException {
		byte[] bytes = Files.readAllBytes(regionPath);
		for (int i = 0; i < 1024 && i * 4 + 3 < bytes.length; i++) {
			if (readInt(bytes, i * 4) != 0) {
				int localX = i & 31;
				int localZ = i >> 5;
				return new ChunkPos(localX, localZ);
			}
		}
		return null;
	}

	private static int readInt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xFF) << 24
			| (bytes[offset + 1] & 0xFF) << 16
			| (bytes[offset + 2] & 0xFF) << 8
			| bytes[offset + 3] & 0xFF;
	}

	private record RegionChunk(Path regionPath, int regionX, int regionZ, int localX, int localZ) {
	}

	@FunctionalInterface
	private interface TimedOperation {
		void run() throws Exception;
	}

	private record LoadedEntities(List<Entity> entities, List<CompoundTag> saved) {
	}

	private record SampleSet(long[] values) {
		long median() {
			long[] copy = this.values.clone();
			Arrays.sort(copy);
			return copy[copy.length / 2];
		}

		@Override
		public String toString() {
			return "medianNanos=" + this.median() + ", samples=" + Arrays.toString(this.values);
		}
	}
}
