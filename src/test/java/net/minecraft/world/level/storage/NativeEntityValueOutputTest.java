package net.minecraft.world.level.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.nbt.NbtBenchmarkAccess;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.NativeEntityStorage;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeEntityValueOutputTest {
	private static final int CURRENT_DATA_VERSION = 4556;

	@BeforeAll
	static void bootstrapMinecraft() {
		System.setProperty("net.bytebuddy.experimental", "true");
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
	}

	@Test
	void savesLiveEntitiesToNativeTapeWithTagValueOutputParity() throws Exception {
		List<Entity> entities = loadEntities(List.of(entity("minecraft:area_effect_cloud", entity("minecraft:armor_stand")), itemEntity()));

		List<CompoundTag> javaSaved = saveWithTagValueOutput(entities);
		List<byte[]> nativeTapes = saveWithNativeValueOutput(entities);

		assertEquals(javaSaved.size(), nativeTapes.size());
		for (int i = 0; i < javaSaved.size(); i++) {
			CompoundTag nativeSaved = NativeNbtRegionAccess.readTape(nativeTapes.get(i));
			assertEquals(rootlessFingerprint(javaSaved.get(i)), rootlessFingerprint(nativeSaved));
		}
	}

	@Test
	void broadCurrentVersionEntitiesSaveWithNativeOutputParity() throws Exception {
		List<CompoundTag> fixtures = broadEntityFixtures();
		List<Entity> entities = loadEntities(fixtures);
		assertTrue(entities.size() >= fixtures.size());

		List<CompoundTag> javaSaved = saveWithTagValueOutput(entities);
		List<byte[]> nativeTapes = saveWithNativeValueOutput(entities);

		assertEquals(javaSaved.size(), nativeTapes.size());
		for (int i = 0; i < javaSaved.size(); i++) {
			CompoundTag nativeSaved = NativeNbtRegionAccess.readTape(nativeTapes.get(i));
			assertEquals(rootlessFingerprint(javaSaved.get(i)), rootlessFingerprint(nativeSaved), "entity index " + i);
		}
	}

	@Test
	void rustWritesNativeEntityTapesAndDefaultRustReadPathDecodesThem(@TempDir Path tempDir) throws Exception {
		ChunkPos pos = new ChunkPos(2, 3);
		List<Entity> entities = loadEntities(List.of(entity("minecraft:area_effect_cloud", entity("minecraft:armor_stand")), itemEntity()));
		List<byte[]> nativeTapes = saveWithNativeValueOutput(entities);
		Path regionPath = tempDir.resolve("r.0.0.mca");

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			NativeEntityStorage.WriteResult write = storage.writeChunk(pos.x, pos.z, RegionFileVersion.VERSION_DEFLATE.getId(), nativeTapes);
			assertEquals(NativeEntityStorage.OK, write.status());
			assertEquals(nativeTapes.size(), write.entityCount());

			NativeEntityStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);
			assertTrue(decoded.result().present());
			assertFalse(decoded.result().requiresDfu());
			assertEquals(nativeTapes.size(), decoded.entities().size());
			for (int i = 0; i < nativeTapes.size(); i++) {
				assertEquals(rootlessFingerprint(NativeNbtRegionAccess.readTape(nativeTapes.get(i))), rootlessFingerprint(decoded.entities().get(i).readTapeAsTag()));
			}
		}
	}

	private static List<Entity> loadEntities(List<CompoundTag> tags) {
		ValueInput.ValueInputList inputs = TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tags);
		return EntityType.loadEntitiesRecursive(inputs, controlledLevel(), EntitySpawnReason.LOAD).toList();
	}

	private static List<CompoundTag> broadEntityFixtures() {
		return List.of(
			entityWithSeed("minecraft:zombie", 1),
			entityWithSeed("minecraft:armor_stand", 2),
			itemEntity(3),
			entityWithSeed("minecraft:arrow", 4),
			entityWithSeed("minecraft:snowball", 5),
			entityWithSeed("minecraft:block_display", 6),
			itemDisplayEntity(7),
			textDisplayEntity(8),
			entityWithSeed("minecraft:marker", 9, entityWithSeed("minecraft:area_effect_cloud", 10, entityWithSeed("minecraft:armor_stand", 11)))
		);
	}

	private static List<CompoundTag> saveWithTagValueOutput(List<Entity> entities) throws IOException {
		List<CompoundTag> saved = new ArrayList<>(entities.size());
		for (Entity entity : entities) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
			assertTrue(entity.saveAsPassenger(output));
			saved.add(output.buildResult());
		}
		return saved;
	}

	private static List<byte[]> saveWithNativeValueOutput(List<Entity> entities) throws IOException {
		List<byte[]> saved = new ArrayList<>(entities.size());
		for (Entity entity : entities) {
			NativeEntityValueOutput output = NativeEntityValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
			assertTrue(entity.saveAsPassenger(output));
			saved.add(output.buildTape());
		}
		return saved;
	}

	private static CompoundTag entity(String id, CompoundTag... passengers) {
		return entityWithSeed(id, id.hashCode(), passengers);
	}

	private static CompoundTag entityWithSeed(String id, int seed, CompoundTag... passengers) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		UUID uuid = UUID.nameUUIDFromBytes((id + "#" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		tag.putIntArray("UUID", UUIDUtil.uuidToIntArray(uuid));
		tag.put("Pos", doubleList(1.25 + seed * 0.125, 64.0 + seed % 5, -2.5 - seed * 0.0625));
		tag.put("Motion", doubleList(0.0625, 0.0, -0.125));
		tag.put("Rotation", floatList(90.0F + seed, 12.5F));
		tag.putDouble("fall_distance", 1.5);
		tag.putShort("Fire", (short)20);
		tag.putInt("Air", 250);
		tag.putBoolean("OnGround", true);
		tag.putBoolean("Silent", true);
		tag.putBoolean("Glowing", true);
		tag.putInt("TicksFrozen", 3);
		tag.store("CustomName", ComponentSerialization.CODEC, registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), Component.literal("native entity " + seed));
		tag.putBoolean("CustomNameVisible", true);
		CompoundTag custom = new CompoundTag();
		custom.putString("source", "native-entity-write-parity");
		custom.putIntArray("payload", new int[]{seed, seed * 2, seed * 3});
		tag.put("MattMCCustomUnknown", custom);
		if (id.equals("minecraft:area_effect_cloud")) {
			tag.putInt("Age", 12);
			tag.putInt("Duration", 200);
			tag.putFloat("Radius", 2.5F);
		} else if (id.equals("minecraft:armor_stand")) {
			tag.putBoolean("Invisible", true);
			tag.putBoolean("Small", true);
			tag.putBoolean("ShowArms", true);
			EntityEquipment equipment = new EntityEquipment();
			equipment.set(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
			equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			tag.store("equipment", EntityEquipment.CODEC, registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), equipment);
		} else if (id.equals("minecraft:zombie")) {
			tag.putFloat("Health", 18.0F);
			tag.putBoolean("CanPickUpLoot", true);
		} else if (id.equals("minecraft:arrow")) {
			tag.putByte("pickup", (byte)1);
			tag.putInt("life", 7);
		} else if (id.endsWith("_display")) {
			tag.putFloat("view_range", 1.0F);
			tag.putFloat("width", 0.75F);
			tag.putFloat("height", 0.75F);
		}
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf("native-value-output"));
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

	private static CompoundTag itemEntity() {
		return itemEntity(12);
	}

	private static CompoundTag itemEntity(int seed) {
		CompoundTag tag = entityWithSeed("minecraft:item", seed);
		tag.putShort("Health", (short)5);
		tag.putShort("Age", (short)10);
		tag.putShort("PickupDelay", (short)2);
		tag.store("Item", ItemStack.CODEC, registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), namedSword());
		return tag;
	}

	private static CompoundTag itemDisplayEntity(int seed) {
		CompoundTag tag = entityWithSeed("minecraft:item_display", seed);
		tag.store("item", ItemStack.CODEC, registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), namedSword());
		tag.putString("item_display", "fixed");
		return tag;
	}

	private static CompoundTag textDisplayEntity(int seed) {
		CompoundTag tag = entityWithSeed("minecraft:text_display", seed);
		tag.store("text", ComponentSerialization.CODEC, registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), Component.literal("Rust entity write parity"));
		tag.putInt("line_width", 120);
		tag.putByte("text_opacity", (byte)-1);
		return tag;
	}

	private static ItemStack namedSword() {
		ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("MattMC native write parity"));
		return stack;
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
}
