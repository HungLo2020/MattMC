package net.minecraft.resources;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeItemComponentValuesTest {
	@BeforeAll static void load() { NativeDataTest.load(); }

	/** Both public lookup factories must remain untrusted, including standard adapters. */
	private static RegistryOps<JsonElement> mutatingLookup(boolean provider, Runnable mutation, List<String> calls) {
		return mutatingLookup(provider, JsonOps.INSTANCE, mutation, calls);
	}

	private static RegistryOps<JsonElement> mutatingLookup(boolean provider, com.mojang.serialization.DynamicOps<JsonElement> base, Runnable mutation, List<String> calls) {
		HolderLookup.Provider source = NativeDataTest.registries;
		HolderLookup.Provider changed = new HolderLookup.Provider() {
			@Override public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
				throw new AssertionError("Admission must not enumerate an unknown provider");
			}
			@Override public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				return source.lookup(key).map(parent -> new HolderLookup.RegistryLookup.Delegate<T>() {
					@Override public HolderLookup.RegistryLookup<T> parent() { return parent; }
					@Override public Optional<Holder.Reference<T>> get(ResourceKey<T> value) {
						calls.add(value.location().toString());
						if (key.equals(Registries.TRIM_MATERIAL) || key.equals(Registries.BANNER_PATTERN)) mutation.run();
						return parent.get(value);
					}
				});
			}
		};
		if (provider) return RegistryOps.create(base, changed);
		return RegistryOps.create(base, new RegistryOps.RegistryInfoLookup() {
			@Override public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				return changed.lookup(key).map(RegistryOps.RegistryInfo::fromRegistryLookup);
			}
		});
	}

	@Test void unknownLookupFactoriesKeepLiveMapReadsAndExactCallbackOrder() {
		for (boolean provider : List.of(false, true)) for (String route : List.of("direct", "patch", "stack")) {
			var trim = JsonParser.parseString("{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:coast\"}").getAsJsonObject();
			List<String> calls = new ArrayList<>();
			var ops = mutatingLookup(provider, () -> trim.addProperty("pattern", "minecraft:spire"), calls);
			assertFalse(NativeData.nativeComponentInput(ops, trim));
			assertTrue(calls.isEmpty(), "admission must not probe getters");
			net.minecraft.world.item.equipment.trim.ArmorTrim result;
			if (route.equals("direct")) result = net.minecraft.world.item.equipment.trim.ArmorTrim.CODEC.parse(ops, trim).getOrThrow();
			else {
				var patch = new com.google.gson.JsonObject(); patch.add("minecraft:trim", trim);
				if (route.equals("patch")) result = DataComponentPatch.CODEC.parse(ops, patch).getOrThrow().get(DataComponents.TRIM).orElseThrow();
				else {
					var stack = new com.google.gson.JsonObject(); stack.addProperty("id", "minecraft:stick"); stack.add("components", patch);
					result = ItemStack.CODEC.parse(ops, stack).getOrThrow().get(DataComponents.TRIM);
				}
			}
			assertEquals(ResourceLocation.parse("minecraft:spire"), result.pattern().unwrapKey().orElseThrow().location(), provider + " " + route);
			assertEquals(List.of("minecraft:iron", "minecraft:spire"), calls);
		}
	}

	@Test void unknownLookupFactoriesKeepLiveListMutationAndFailurePrefix() {
		for (boolean provider : List.of(false, true)) {
			var layers = JsonParser.parseString("[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"red\"},{\"pattern\":\"minecraft:cross\",\"color\":\"blue\"}]").getAsJsonArray();
			List<String> calls = new ArrayList<>();
			var ops = mutatingLookup(provider, () -> layers.get(1).getAsJsonObject().addProperty("color", "green"), calls);
			var patch = new com.google.gson.JsonObject(); patch.add("minecraft:banner_patterns", layers);
			var value = DataComponentPatch.CODEC.parse(ops, patch).getOrThrow().get(DataComponents.BANNER_PATTERNS).orElseThrow();
			assertEquals(net.minecraft.world.item.DyeColor.GREEN, value.layers().get(1).color());
			assertEquals(List.of("minecraft:stripe_bottom", "minecraft:cross"), calls);

			calls.clear();
			var failing = mutatingLookup(provider, () -> layers.add(layers.get(0).deepCopy()), calls);
			assertThrows(java.util.ConcurrentModificationException.class, () -> DataComponentPatch.CODEC.parse(failing, patch));
			assertEquals(List.of("minecraft:stripe_bottom"), calls, "must fail before another list element callback");
		}
	}

	@Test void customNumbersDeclineBeforeConversionAndKeepLiveFieldReads() {
		for (String route : List.of("direct", "patch", "stack")) {
			var food = JsonParser.parseString("{\"saturation\":1}").getAsJsonObject();
			int[] intCalls = {0}, stringCalls = {0};
			food.add("nutrition", new com.google.gson.JsonPrimitive(new Number() {
				@Override public int intValue() { intCalls[0]++; food.addProperty("saturation", 7); return 4; }
				@Override public long longValue() { throw new AssertionError("Unexpected conversion"); }
				@Override public float floatValue() { throw new AssertionError("Unexpected conversion"); }
				@Override public double doubleValue() { throw new AssertionError("Unexpected conversion"); }
				@Override public String toString() { stringCalls[0]++; return "4"; }
			}));
			assertFalse(NativeData.nativeComponentInput(JsonOps.INSTANCE, food));
			assertEquals(0, intCalls[0]); assertEquals(0, stringCalls[0], "admission must not convert");
			FoodProperties result;
			if (route.equals("direct")) result = FoodProperties.DIRECT_CODEC.parse(JsonOps.INSTANCE, food).getOrThrow();
			else {
				var patch = new com.google.gson.JsonObject(); patch.add("minecraft:food", food);
				if (route.equals("patch")) result = DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, patch).getOrThrow().get(DataComponents.FOOD).orElseThrow();
				else { var stack = new com.google.gson.JsonObject(); stack.addProperty("id", "minecraft:stick"); stack.add("components", patch); result = ItemStack.CODEC.parse(JsonOps.INSTANCE, stack).getOrThrow().get(DataComponents.FOOD); }
			}
			assertEquals(7, result.saturation()); assertEquals(1, intCalls[0]);
		}
	}

	@Test void onlyControlledFinalizationGrantsLookupProvenance() {
		assertTrue(NativeDataTest.registries.createSerializationContext(JsonOps.INSTANCE).hasControlledFinalizedLookups(), "real loaded standard context must be admitted");
		var unfinalized = new RegistryAccess.ImmutableRegistryAccess(List.of());
		assertFalse(unfinalized.hasControlledFinalizedLookups());
		assertTrue(((RegistryAccess.ImmutableRegistryAccess)unfinalized.freeze()).hasControlledFinalizedLookups());
		var subclass = new RegistryAccess.ImmutableRegistryAccess(List.of()) {};
		assertFalse(subclass.hasControlledFinalizedLookups());
		var custom = new net.minecraft.core.MappedRegistry<Object>(ResourceKey.createRegistryKey(ResourceLocation.parse("test:custom")), Lifecycle.stable()) {};
		assertFalse(((RegistryAccess.ImmutableRegistryAccess)new RegistryAccess.ImmutableRegistryAccess(List.of(custom)).freeze()).hasControlledFinalizedLookups());
	}

	@Test void unknownWrappersAndExtensibleNumericClassesCannotGrantAdmission() {
		var original = NativeDataTest.registries;
		var wrapper = new HolderLookup.Provider() {
			@Override public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() { throw new AssertionError("unexpected enumeration"); }
			@Override public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				throw new AssertionError("unexpected lookup");
			}
		};
		assertFalse(NativeData.nativeComponentInput(RegistryOps.create(JsonOps.INSTANCE, wrapper), JsonParser.parseString("{}")));
		var infoWrapper = new RegistryOps.RegistryInfoLookup() {
			@Override public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				throw new AssertionError("unexpected info lookup");
			}
		};
		assertFalse(NativeData.nativeComponentInput(RegistryOps.create(JsonOps.INSTANCE, infoWrapper), JsonParser.parseString("{}")));
		assertTrue(NativeData.nativeComponentInput(original.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString("{\"nutrition\":4}")));
		var number = new java.math.BigInteger("4") {
			@Override public String toString() { throw new AssertionError("unexpected conversion"); }
			@Override public int intValue() { throw new AssertionError("unexpected conversion"); }
		};
		assertFalse(NativeData.nativeComponentInput(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(number)));
		var customAccess = new RegistryAccess.ImmutableRegistryAccess(List.of()) {
			@Override public <T> Optional<Registry<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				throw new AssertionError("unexpected access lookup");
			}
		};
		assertFalse(NativeData.nativeComponentInput(RegistryOps.create(JsonOps.INSTANCE, customAccess), JsonParser.parseString("{}")));
	}

	@Test void parallelFinalizationCannotLoseAnUnknownRegistry() {
		List<RegistryAccess.RegistryEntry<?>> entries = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			var key = ResourceKey.<Object>createRegistryKey(ResourceLocation.parse("test:parallel_" + i));
			var registry = i == 31 ? new net.minecraft.core.MappedRegistry<Object>(key, Lifecycle.stable()) {}
				: new net.minecraft.core.MappedRegistry<Object>(key, Lifecycle.stable());
			entries.add(new RegistryAccess.RegistryEntry<>(key, registry));
		}
		var access = new RegistryAccess() {
			@Override public <T> Optional<Registry<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) { throw new AssertionError("unexpected lookup"); }
			@Override public Stream<RegistryAccess.RegistryEntry<?>> registries() { return entries.parallelStream(); }
		};
		assertFalse(((RegistryAccess.ImmutableRegistryAccess)access.freeze()).hasControlledFinalizedLookups());
	}

	@Test void finalizationKeepsTheOriginalInvalidEntryFailure() {
		var key = ResourceKey.<Object>createRegistryKey(ResourceLocation.parse("test:null_registry"));
		var access = new RegistryAccess() {
			@Override public <T> Optional<Registry<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) { throw new AssertionError("unexpected lookup"); }
			@Override public Stream<RegistryAccess.RegistryEntry<?>> registries() { return Stream.of(new RegistryAccess.RegistryEntry<>(key, null)); }
		};
		var failure = assertThrows(NullPointerException.class, access::freeze);
		assertEquals("Cannot invoke \"net.minecraft.core.Registry.freeze()\" because \"this.value\" is null", failure.getMessage());
	}

	@Test void actualFileLoaderStillUsesNativeComponentsWithItsRegistrationContext() throws Exception {
		var codec = net.minecraft.world.entity.decoration.PaintingVariant.DIRECT_CODEC;
		var data = new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, codec);
		var loader = data.create(Lifecycle.stable(), new java.util.HashMap<>());
		var createContext = RegistryDataLoader.class.getDeclaredMethod("createContext", List.class, List.class);
		createContext.setAccessible(true);
		var lookup = (RegistryOps.RegistryInfoLookup)createContext.invoke(null, List.of(), List.of(loader));
		var ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
		assertFalse(ops.hasControlledFinalizedLookups(), "registration context is not finalized tree provenance");
		assertTrue(NativeData.nativeComponentOps(ops), "Rust-owned file input remains admissible");
		var loadElement = RegistryDataLoader.class.getDeclaredMethod("loadElementFromResource", net.minecraft.core.WritableRegistry.class,
			com.mojang.serialization.Decoder.class, RegistryOps.class, ResourceKey.class,
			net.minecraft.server.packs.resources.Resource.class, net.minecraft.core.RegistrationInfo.class);
		loadElement.setAccessible(true);
		byte[] bytes = """
			{"width":2,"height":3,"asset_id":"minecraft:a","title":{"text":"a",
			 "hover_event":{"action":"show_item","id":"minecraft:stick","components":{
			 "minecraft:food":{"nutrition":4,"saturation":1}}}}}
			""".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		var key = ResourceKey.create(Registries.PAINTING_VARIANT, ResourceLocation.parse("test:native_component_loader"));
		try (var pack = net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()) {
			var resource = new net.minecraft.server.packs.resources.Resource(pack, () -> new java.io.ByteArrayInputStream(bytes));
			NativeValuesTest.withDelegates(original -> NativeValuesTest.watched(original, () -> {
				throw new AssertionError("file loader must parse food natively: " + original);
			}), () -> assertDoesNotThrow(() -> loadElement.invoke(null, loader.registry(), codec, ops, key, resource, net.minecraft.core.RegistrationInfo.BUILT_IN)));
		}
		loader.registry().freeze();
		assertEquals(1, loader.registry().size());
		var encoded = codec.encodeStart(ops, loader.registry().getValueOrThrow(key)).getOrThrow();
		assertEquals(4, encoded.getAsJsonObject().getAsJsonObject("title").getAsJsonObject("hover_event").getAsJsonObject("components")
			.getAsJsonObject("minecraft:food").get("nutrition").getAsInt());
	}

	@Test void nestedDefinitionFileAndTreeKeepLiveMapsListsReentryAndExceptionPrefix() {
		var codec = (NativeData.DataCodec<Object>)(Object)NativeDataTest.codecs(NativeData.Kind.PAINTING_VARIANT)[0];
		for (boolean provider : List.of(false, true)) for (boolean file : List.of(false, true)) for (boolean list : List.of(false, true)) {
			String component = list ? "\"minecraft:banner_patterns\":[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"red\"},{\"pattern\":\"minecraft:cross\",\"color\":\"blue\"}]"
				: "\"minecraft:trim\":{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:coast\"}";
			String source = "{\"width\":2,\"height\":3,\"asset_id\":\"minecraft:a\",\"title\":{\"text\":\"a\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:stick\",\"components\":{" + component + "}}}}";
			JsonElement[] live = {null};
			var base = new DelegatingOps<JsonElement>(JsonOps.INSTANCE) {
				@Override public DataResult<MapLike<JsonElement>> getMap(JsonElement node) {
					if (node.isJsonObject() && node.getAsJsonObject().has("material")) live[0] = node;
					return super.getMap(node);
				}
				@Override public DataResult<java.util.function.Consumer<java.util.function.Consumer<JsonElement>>> getList(JsonElement node) {
					if (node.isJsonArray()) live[0] = node;
					return super.getList(node);
				}
			};
			List<String> calls = new ArrayList<>();
			Throwable failure = new AssertionError("original getter failure");
			boolean[] fail = {false}, reentered = {false};
			var ops = mutatingLookup(provider, base, () -> {
				if (fail[0]) throw NativeValuesTest.sneaky(failure);
				assertNotNull(live[0], "original component ops must expose its live node before the getter");
				if (list) live[0].getAsJsonArray().get(1).getAsJsonObject().addProperty("color", "green");
				else live[0].getAsJsonObject().addProperty("pattern", "minecraft:spire");
				assertEquals(3, ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:food\":{\"nutrition\":3,\"saturation\":1}}}"))
					.getOrThrow().get(DataComponents.FOOD).nutrition());
				reentered[0] = true;
			}, calls);
			java.util.function.Supplier<DataResult<Object>> decode = () -> file
				? codec.decodeFile(ops, source.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : codec.parse(ops, JsonParser.parseString(source));
			var value = decode.get().getOrThrow();
			assertTrue(reentered[0]);
			assertEquals(list ? List.of("minecraft:stripe_bottom", "minecraft:cross") : List.of("minecraft:iron", "minecraft:spire"), calls);
			var components = codec.encodeStart(NativeDataTest.registries.createSerializationContext(JsonOps.INSTANCE), value).getOrThrow()
				.getAsJsonObject().getAsJsonObject("title").getAsJsonObject("hover_event").getAsJsonObject("components");
			if (list) assertEquals("green", components.getAsJsonArray("minecraft:banner_patterns").get(1).getAsJsonObject().get("color").getAsString());
			else assertEquals("minecraft:spire", components.getAsJsonObject("minecraft:trim").get("pattern").getAsString());
			calls.clear(); fail[0] = true;
			assertSame(failure, assertThrows(Throwable.class, decode::get));
			assertEquals(list ? List.of("minecraft:stripe_bottom") : List.of("minecraft:iron"), calls);
			fail[0] = false; calls.clear();
			assertTrue(decode.get().isSuccess(), "scratch state must recover after the getter throws");
		}
	}

	@Test void plainRecordsDecodeNativelyWithoutComponentCallbacks() {
		assertFalse(FoodProperties.DIRECT_CODEC instanceof NativeValues.ValueCodec<?>);
		NativeValuesTest.withDelegates(codec -> NativeValuesTest.watched(codec, () -> {
			throw new AssertionError("Unexpected delegated component: " + codec);
		}), () -> {
			var result = ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
				{"id":"minecraft:stick","components":{
				 "minecraft:food":{"nutrition":4,"saturation":2.4},
				 "minecraft:enchantable":{"value":9},
				 "minecraft:damage_resistant":{"types":"#minecraft:is_fire"}}}
				""")).getOrThrow();
			assertEquals(4, result.get(DataComponents.FOOD).nutrition());
			assertEquals(9, result.get(DataComponents.ENCHANTABLE).value());
			assertEquals(ResourceLocation.parse("minecraft:is_fire"), result.get(DataComponents.DAMAGE_RESISTANT).types().location());
			var painting = (NativeData.DataCodec<Object>)(Object)NativeDataTest.codecs(NativeData.Kind.PAINTING_VARIANT)[0];
			assertTrue(painting.decodeFile(NativeDataTest.registries.createSerializationContext(JsonOps.INSTANCE), """
				{"width":2,"height":3,"asset_id":"minecraft:a","title":{"text":"a",
				 "hover_event":{"action":"show_item","id":"minecraft:stick","components":{
				 "minecraft:food":{"nutrition":4,"saturation":1}}}}}
				""".getBytes(java.nio.charset.StandardCharsets.UTF_8)).isSuccess());
		});
	}

	@Test void leavesRunOnceInOrderAcrossRetryReentryAndThrow() {
		var ops = NativeDataTest.registries.createSerializationContext(JsonOps.INSTANCE);
		var input = JsonParser.parseString("{\"minecraft:trim\":{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:coast\"}}");
		Codec<?> material = NativeValues.JavaCodec.TRIM_MATERIAL.codec();
		Codec<?> pattern = NativeValues.JavaCodec.TRIM_PATTERN.codec();
		List<Codec<?>> calls = new ArrayList<>();
		String large = "leaf error ".repeat(600);
		NativeValuesTest.withDelegates(codec -> NativeValuesTest.watched(codec, () -> {
			calls.add(codec);
			if (codec == material) {
				assertEquals(3, ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
					"{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:food\":{\"nutrition\":3,\"saturation\":1}}}"))
					.getOrThrow().get(DataComponents.FOOD).nutrition());
				return DataResult.error(() -> large);
			}
			return null;
		}), () -> assertTrue(DataComponentPatch.CODEC.parse(ops, input).error().orElseThrow().message().contains(large)));
		assertEquals(List.of(material, pattern), calls);

		calls.clear();
		Throwable failure = new AssertionError("original leaf throwable");
		NativeValuesTest.withDelegates(codec -> NativeValuesTest.watched(codec, () -> {
			calls.add(codec);
			throw NativeValuesTest.sneaky(failure);
		}), () -> assertSame(failure, assertThrows(Throwable.class, () -> DataComponentPatch.CODEC.parse(ops, input))));
		assertEquals(List.of(material), calls);
		assertTrue(DataComponentPatch.CODEC.parse(ops, input).isSuccess(), "scratch state must survive a thrown callback");
	}

	@Test void standaloneCodecsKeepFullDecodeAndEncoderContracts() {
		var input = JsonParser.parseString("{\"nutrition\":4,\"saturation\":1,\"can_always_eat\":\"bad\"}");
		var decoded = FoodProperties.DIRECT_CODEC.decode(JsonOps.INSTANCE, input);
		assertTrue(decoded.isError());
		assertSame(input, decoded.resultOrPartial().orElseThrow().getSecond());
		assertEquals(4, decoded.resultOrPartial().orElseThrow().getFirst().nutrition());
		assertSame(Lifecycle.stable(), net.minecraft.world.item.component.Bees.CODEC.decode(JsonOps.INSTANCE,
			JsonParser.parseString("[]")).lifecycle());
		var prefix = JsonParser.parseString("{\"keep\":true}");
		var encoded = FoodProperties.DIRECT_CODEC.encode(new FoodProperties(4, 1, false), JsonOps.INSTANCE, prefix).getOrThrow().getAsJsonObject();
		assertTrue(encoded.get("keep").getAsBoolean());
		assertEquals(4, encoded.get("nutrition").getAsInt());
	}

	@Test void customOpsKeepOriginalNodeAndSingleCompleteComponentCallback() {
		var component = JsonParser.parseString("{\"nutrition\":4,\"saturation\":1}");
		var patch = new com.google.gson.JsonObject();
		patch.add("minecraft:food", component);
		int[] maps = {0};
		var ops = new DelegatingOps<JsonElement>(JsonOps.INSTANCE) {
			@Override public DataResult<MapLike<JsonElement>> getMap(JsonElement input) {
				assertSame(component, input);
				maps[0]++;
				return super.getMap(input).flatMap(map -> DataResult.error(() -> "custom map error", map, Lifecycle.deprecated(1)));
			}
		};
		List<Codec<?>> calls = new ArrayList<>();
		NativeValuesTest.withDelegates(codec -> NativeValuesTest.watched(codec, () -> {
			calls.add(codec);
			return null;
		}), () -> {
			var result = DataComponentPatch.CODEC.parse(ops, patch);
			assertTrue(result.error().orElseThrow().message().startsWith("custom map error missed input:"));
			assertEquals(4, result.resultOrPartial().orElseThrow().get(DataComponents.FOOD).orElseThrow().nutrition());
		});
		assertEquals(List.of(DataComponents.FOOD.codecOrThrow()), calls);
		assertEquals(1, maps[0]);
	}

	@Test void registeredEncoderCacheRetainsJsonIdentityAndCopiesNbt() {
		FoodProperties first = new FoodProperties(12345, 6.75F, true);
		FoodProperties equal = new FoodProperties(12345, 6.75F, true);
		Codec<FoodProperties> codec = DataComponents.FOOD.codecOrThrow();
		var json = codec.encodeStart(JsonOps.INSTANCE, first).getOrThrow();
		assertSame(json, codec.encodeStart(JsonOps.INSTANCE, equal).getOrThrow());
		ItemStack stack = new ItemStack(Items.STICK);
		stack.set(DataComponents.FOOD, equal);
		var encoded = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).getOrThrow().getAsJsonObject();
		assertSame(json, encoded.getAsJsonObject("components").get("minecraft:food"), "stack encoding must use the registered cache");
		CompoundTag a = (CompoundTag)codec.encodeStart(NbtOps.INSTANCE, first).getOrThrow();
		CompoundTag b = (CompoundTag)codec.encodeStart(NbtOps.INSTANCE, equal).getOrThrow();
		assertNotSame(a, b);
		a.putInt("nutrition", -1);
		assertEquals(b, codec.encodeStart(NbtOps.INSTANCE, equal).getOrThrow());
	}

	@Test void customOpsDefinitionKeepsDelegatedComponentThroughCollection() {
		var ops = new DelegatingOps<JsonElement>(JsonOps.INSTANCE) {};
		var registryOps = NativeDataTest.registries.createSerializationContext(ops);
		var codec = (NativeData.DataCodec<Object>)(Object)NativeDataTest.codecs(NativeData.Kind.PAINTING_VARIANT)[0];
		var input = JsonParser.parseString("""
			{"width":2,"height":3,"asset_id":"minecraft:a","title":{"text":"a",
			 "hover_event":{"action":"show_item","id":"minecraft:stick","components":{
			 "minecraft:food":{"nutrition":4,"saturation":1}}}}}
			""");
		for (Object value : List.of(codec.parse(registryOps, input).getOrThrow(),
			codec.decodeFile(registryOps, input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).getOrThrow())) {
			long handle = codec.nativeHandle(value);
			assertNotEquals(0, handle);
			var first = codec.encodeStart(registryOps, value).getOrThrow();
			System.gc();
			assertTrue(NativeData.stored(handle));
			assertEquals(first, codec.encodeStart(registryOps, value).getOrThrow());
			assertEquals(4, first.getAsJsonObject().getAsJsonObject("title").getAsJsonObject("hover_event")
				.getAsJsonObject("components").getAsJsonObject("minecraft:food").get("nutrition").getAsInt());
			java.lang.ref.Reference.reachabilityFence(value);
		}
	}

	@Test void customOpsFileAndTreeKeepPartialErrorsOnceAcrossRetryAndReentry() {
		var input = JsonParser.parseString("""
			{"width":2,"height":3,"asset_id":"minecraft:a","title":{"text":"a",
			 "hover_event":{"action":"show_item","id":"minecraft:stick","components":{
			 "minecraft:food":{"nutrition":4,"saturation":1}}}}}
			""");
		var codec = (NativeData.DataCodec<Object>)(Object)NativeDataTest.codecs(NativeData.Kind.PAINTING_VARIANT)[0];
		int[] calls = {0};
		String error = "custom food map error ".repeat(300);
		var custom = new DelegatingOps<JsonElement>(JsonOps.INSTANCE) {
			@Override public DataResult<MapLike<JsonElement>> getMap(JsonElement node) {
				var result = super.getMap(node);
				if (!node.isJsonObject() || !node.getAsJsonObject().has("nutrition")) return result;
				calls[0]++;
				var nested = ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
					"{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:food\":{\"nutrition\":3,\"saturation\":1}}}"));
				assertEquals(3, nested.getOrThrow().get(DataComponents.FOOD).nutrition());
				return result.flatMap(map -> DataResult.error(() -> error, map, Lifecycle.deprecated(1)));
			}
		};
		var ops = NativeDataTest.registries.createSerializationContext(custom);
		var tree = codec.parse(ops, input);
		assertEquals(1, calls[0]);
		var file = codec.decodeFile(ops, input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		assertEquals(2, calls[0]);
		assertEquals(tree.error().orElseThrow().message(), file.error().orElseThrow().message());
		assertTrue(file.error().orElseThrow().message().contains(error));
		assertEquals(codec.encodeStart(ops, tree.resultOrPartial().orElseThrow()).getOrThrow(),
			codec.encodeStart(ops, file.resultOrPartial().orElseThrow()).getOrThrow());

		Throwable failure = new AssertionError("original file component error");
		var throwing = new DelegatingOps<JsonElement>(JsonOps.INSTANCE) {
			@Override public DataResult<MapLike<JsonElement>> getMap(JsonElement node) {
				if (node.isJsonObject() && node.getAsJsonObject().has("nutrition")) throw NativeValuesTest.sneaky(failure);
				return super.getMap(node);
			}
		};
		var throwingOps = NativeDataTest.registries.createSerializationContext(throwing);
		assertSame(failure, assertThrows(Throwable.class, () -> codec.decodeFile(throwingOps,
			input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		assertTrue(codec.decodeFile(NativeDataTest.registries.createSerializationContext(JsonOps.INSTANCE),
			input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).isSuccess());
	}
}
