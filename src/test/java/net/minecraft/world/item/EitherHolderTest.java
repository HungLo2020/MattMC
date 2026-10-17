package net.minecraft.world.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EitherHolderTest {
	private static final ResourceKey<Registry<String>> REGISTRY = ResourceKey.createRegistryKey(ResourceLocation.parse("test:values"));
	private static final Holder<String> KNOWN = Holder.direct("known");
	private static final Codec<Holder<String>> HOLDERS = Codec.STRING.comapFlatMap(
		name -> name.equals("known") ? DataResult.success(KNOWN) : DataResult.error(() -> "Unknown holder " + name), Holder::value);
	private static final Codec<EitherHolder<String>> CODEC = EitherHolder.codec(REGISTRY, HOLDERS);
	// Unchanged pre-bootstrap-opt-in reference: parse a key, then reject it.
	private static final Codec<EitherHolder<String>> ORIGINAL = Codec.either(HOLDERS,
		ResourceKey.codec(REGISTRY).comapFlatMap(key -> DataResult.<ResourceKey<String>>error(() -> "Cannot parse as key without registry"), key -> key))
		.xmap(EitherHolder::new, EitherHolder::contents);

	private static RegistryOps.RegistryInfoLookup lookup(boolean bootstrap) {
		return new RegistryOps.RegistryInfoLookup() {
			@Override public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				return Optional.empty();
			}
			@Override public boolean keepsUnresolvedKeys() { return bootstrap; }
		};
	}

	private static <T> void compareOrdinary(DynamicOps<T> ops) {
		for (String text : List.of("null", "true", "7", "{}", "[]", "\"Invalid Key\"", "\"unknown\"", "\"known\"")) {
			T input = JsonOps.INSTANCE.convertTo(ops, JsonParser.parseString(text));
			DataResult<EitherHolder<String>> expected = ORIGINAL.parse(ops, input);
			DataResult<EitherHolder<String>> actual = CODEC.parse(ops, input);
			assertEquals(expected.error().map(DataResult.Error::message), actual.error().map(DataResult.Error::message), text);
			assertEquals(expected.resultOrPartial(), actual.resultOrPartial(), text);
		}
	}

	@Test void ordinaryContextsKeepOriginalKeyValidationErrors() {
		compareOrdinary(JsonOps.INSTANCE);
		compareOrdinary(NbtOps.INSTANCE);
		compareOrdinary(RegistryOps.create(JsonOps.INSTANCE, lookup(false)));
		compareOrdinary(RegistryOps.create(NbtOps.INSTANCE, lookup(false)));
	}

	@Test void onlyBootstrapAcceptsValidUnresolvedKeysAndEncodingStaysTheSame() {
		var ordinary = RegistryOps.create(JsonOps.INSTANCE, lookup(false));
		var bootstrap = RegistryOps.create(JsonOps.INSTANCE, lookup(true));
		JsonElement missing = JsonParser.parseString("\"test:missing\"");
		assertTrue(CODEC.parse(ordinary, missing).isError());
		EitherHolder<String> key = CODEC.parse(bootstrap, missing).getOrThrow();
		assertEquals(ResourceKey.create(REGISTRY, ResourceLocation.parse("test:missing")), key.contents().right().orElseThrow());
		assertEquals(ORIGINAL.encodeStart(ordinary, key).getOrThrow(), CODEC.encodeStart(ordinary, key).getOrThrow());
		assertEquals(missing, CODEC.encodeStart(bootstrap, key).getOrThrow());
		for (String text : List.of("null", "true", "7", "{}", "[]", "\"Invalid Key\"")) {
			var value = JsonParser.parseString(text);
			assertEquals(ORIGINAL.parse(ordinary, value).error().orElseThrow().message(), CODEC.parse(bootstrap, value).error().orElseThrow().message());
		}
		for (var ops : List.of(ordinary, bootstrap)) {
			var resolved = CODEC.parse(ops, JsonParser.parseString("\"known\"")).getOrThrow();
			assertSame(KNOWN, resolved.contents().left().orElseThrow());
		}
	}
}
