package net.minecraft.resources;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ClampedInt;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviderType;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Value types Rust decodes, validates and encodes (`content/data/values.rs`):
 * projections round-trip, errors and partial results follow DFU. Frozen
 * parity: DevUtils/tests/content/ValueFuzzReference.java.
 */
class NativeValuesTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static String json(com.mojang.serialization.Codec<?> codec, String text) {
        var result = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(text));
        return result.isError() ? "ERR " + result.error().orElseThrow().message() : "OK";
    }

    @Test void intProvidersProjectAndEncode() {
        IntProvider clamped = IntProvider.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
            "{\"type\":\"clamped\",\"source\":{\"type\":\"uniform\",\"min_inclusive\":-4,\"max_inclusive\":9},\"min_inclusive\":0,\"max_inclusive\":5}")).getOrThrow();
        assertInstanceOf(ClampedInt.class, clamped);
        assertEquals(0, clamped.getMinValue());
        assertEquals(5, clamped.getMaxValue());
        assertEquals("{\"source\":{\"min_inclusive\":-4,\"max_inclusive\":9,\"type\":\"minecraft:uniform\"},\"min_inclusive\":0,\"max_inclusive\":5,\"type\":\"minecraft:clamped\"}",
            IntProvider.CODEC.encodeStart(JsonOps.INSTANCE, clamped).getOrThrow().toString());
        // A typed constant decodes to the plain form.
        assertEquals("4", IntProvider.CODEC.encodeStart(JsonOps.INSTANCE,
            IntProvider.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"constant\",\"value\":4}")).getOrThrow()).getOrThrow().toString());
        var weighted = new WeightedListInt(WeightedList.of(List.of(new Weighted<>(ConstantInt.of(2), 3), new Weighted<>(UniformInt.of(1, 9), 1))));
        var nbt = IntProvider.CODEC.encodeStart(NbtOps.INSTANCE, weighted).getOrThrow();
        IntProvider back = IntProvider.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(1, back.getMinValue());
        assertEquals(9, back.getMaxValue());
    }

    @Test void validationAndBoundsFollowJava() {
        assertEquals("ERR Value provider too high: 4 [7-7]", json(IntProvider.codec(0, 4), "7"));
        assertEquals("Max must be at least min, min_inclusive: 5, max_inclusive: 1",
            IntProvider.CODEC.encodeStart(JsonOps.INSTANCE, UniformInt.of(5, 1)).error().orElseThrow().message());
        assertEquals("ERR Failed to parse either. First: Not a number: {\"type\":\"trapezoid\",\"min\":0,\"max\":4,\"plateau\":5}; Second: Plateau can at most be the full span: [0.0, 4.0]",
            json(FloatProvider.CODEC, "{\"type\":\"trapezoid\",\"min\":0,\"max\":4,\"plateau\":5}"));
        assertEquals(TrapezoidFloat.class, FloatProvider.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"trapezoid\",\"min\":0,\"max\":4,\"plateau\":2}")).getOrThrow().getClass());
        assertThrows(IllegalArgumentException.class, () -> IntProvider.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
            "{\"type\":\"weighted_list\",\"distribution\":[{\"data\":1,\"weight\":2147483647},{\"data\":2,\"weight\":1}]}")));
    }

    @Test void partialResultsSurviveAsDfuKeepsThem() {
        var result = IntProvider.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
            "{\"type\":\"weighted_list\",\"distribution\":[{\"data\":\"q\",\"weight\":1},{\"data\":3,\"weight\":2}]}"));
        assertTrue(result.isError());
        IntProvider partial = result.resultOrPartial().orElseThrow();
        assertEquals(3, partial.getMinValue());
    }

    @Test void levelBasedValuesAndTypeRegistryCases() {
        LevelBasedValue value = LevelBasedValue.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
            "{\"type\":\"lookup\",\"values\":[1,2.5],\"fallback\":{\"type\":\"linear\",\"base\":4,\"per_level_above_first\":1}}")).getOrThrow();
        assertEquals(2.5F, value.calculate(2));
        assertEquals(6.0F, value.calculate(3));
        assertEquals("3.0", LevelBasedValue.CODEC.encodeStart(JsonOps.INSTANCE, LevelBasedValue.constant(3.0F)).getOrThrow().toString());
        // The type registries' entries read a case's fields without the type.
        var uniform = IntProviderType.UNIFORM.codec().codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min_inclusive\":1,\"max_inclusive\":3}")).getOrThrow();
        assertEquals(3, uniform.getMaxValue());
        assertEquals("{\"min_inclusive\":1,\"max_inclusive\":3}", IntProviderType.UNIFORM.codec().codec().encodeStart(JsonOps.INSTANCE, uniform).getOrThrow().toString());
    }

    /** Runs {@code body} with each callback's codec replaced by {@code wrap} of it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void withDelegates(Function<Codec<Object>, Codec<Object>> wrap, Runnable body) {
        var previous = NativeValues.testDelegates;
        NativeValues.testDelegates = codec -> wrap.apply((Codec)codec);
        try {
            body.run();
        } finally {
            NativeValues.testDelegates = previous;
        }
    }

    /** {@code codec}, but {@code before} runs first on each decode and may answer instead (non-null). */
    static Codec<Object> watched(Codec<Object> codec, java.util.function.Supplier<DataResult<?>> before) {
        return new Codec<>() {
            @SuppressWarnings("unchecked")
            @Override public <T> DataResult<Pair<Object, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<?> answer = before.get();
                return answer != null ? (DataResult<Pair<Object, T>>)answer : codec.decode(ops, input);
            }

            @Override public <T> DataResult<T> encode(Object input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }

    static DataResult<ItemStack> stack(String components) {
        return ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"minecraft:bundle\",\"count\":1,\"components\":{" + components + "}}"));
    }

    /** Runs {@code body} on a new thread: fresh native scratch, so first-call buffer sizes apply. */
    static void onFreshThread(Runnable body) throws InterruptedException {
        Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure[0] = e;
            }
        });
        thread.start();
        thread.join();
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    // A Java codec's exception leaves the decode as itself, as when the
    // original codec threw it: any class, including Errors and subclasses.
    @Test void callbackExceptionsPropagateUnchanged() {
        for (Throwable thrown : List.of(new IllegalStateException("boom"), new AssertionError("error"),
                new java.util.ConcurrentModificationException(), new NumberFormatException("narrowed"), new NullPointerException("npe"))) {
            withDelegates(codec -> watched(codec, () -> { throw sneaky(thrown); }), () -> {
                assertSame(thrown, assertThrows(Throwable.class, () -> stack("\"minecraft:bundle_contents\":[]")));
                assertSame(thrown, assertThrows(Throwable.class, () -> net.minecraft.network.chat.ComponentSerialization.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"text\":\"a\",\"click_event\":{\"action\":\"show_dialog\",\"dialog\":\"minecraft:x\"}}"))));
            });
        }
    }

    @SuppressWarnings("unchecked")
    static <X extends Throwable> X sneaky(Throwable e) throws X {
        throw (X)e;
    }

    static final String JSON_NULL = "Cannot invoke \"com.google.gson.JsonElement.getAsJsonPrimitive()\" because \"input\" is null";

    @Test void nestedDecodesKeepTheirOwnExceptions() {
        AtomicInteger calls = new AtomicInteger();
        // The inner stack's callback throws inside the outer one's re-entrant decode.
        IllegalStateException inner = new IllegalStateException("inner");
        withDelegates(codec -> watched(codec, () -> {
            if (calls.incrementAndGet() == 2) {
                throw inner;
            }
            return null;
        }), () -> assertSame(inner, assertThrows(IllegalStateException.class, () -> stack(
            "\"minecraft:bundle_contents\":[{\"id\":\"minecraft:bundle\",\"count\":1,\"components\":{\"minecraft:bundle_contents\":[]}}]"))));
        assertEquals(2, calls.get());
        // An exception already pending ends the decode: later callbacks do not run.
        calls.set(0);
        withDelegates(codec -> watched(codec, () -> {
            calls.incrementAndGet();
            return null;
        }), () -> {
            // JsonOps throws converting a translation argument's nested null.
            NullPointerException npe = assertThrows(NullPointerException.class, () -> stack("\"minecraft:custom_name\":{\"translate\":\"k\",\"with\":[{\"x\":null}]},\"minecraft:bundle_contents\":[]"));
            assertEquals(JSON_NULL, npe.getMessage());
            assertEquals(0, calls.get());
            // A completed re-entrant decode leaves a later exception of the outer one intact.
            assertThrows(NullPointerException.class, () -> stack("\"minecraft:bundle_contents\":[{\"id\":\"minecraft:stone\"}],\"minecraft:custom_name\":{\"translate\":\"k\",\"with\":[{\"x\":null}]}"));
            assertEquals(1, calls.get());
        });
    }

    @Test void oversizedResultsAndAnswersAreNotReplayed() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        StringBuilder lore = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            lore.append(i == 0 ? "" : ",").append("\"line ").append(i).append('"');
        }
        String longError = "x".repeat(1000);
        onFreshThread(() -> withDelegates(codec -> watched(codec, () -> {
            calls.incrementAndGet();
            return null;
        }), () -> {
            // The result is several KB, over the first 1 KB buffer.
            ItemStack big = stack("\"minecraft:lore\":[" + lore + "],\"minecraft:bundle_contents\":[{\"id\":\"minecraft:stone\"}]").getOrThrow();
            assertEquals(1, calls.get());
            assertEquals(200, big.get(net.minecraft.core.component.DataComponents.LORE).lines().size());
            assertEquals(1, big.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS).size());
        }));
        // A callback answer over Rust's 256-byte buffer is kept for the retry.
        calls.set(0);
        withDelegates(codec -> watched(codec, () -> {
            calls.incrementAndGet();
            return DataResult.error(() -> longError);
        }), () -> {
            String message = stack("\"minecraft:bundle_contents\":[]").error().orElseThrow().message();
            assertTrue(message.contains(longError), message);
            assertEquals(1, calls.get());
        });
        // A leaf parser's error over Rust's 256-byte buffer (Frozen's message).
        String url = "z".repeat(400);
        assertEquals("ERR Missing protocol in URI: " + url + ": " + url,
            json(net.minecraft.network.chat.ComponentSerialization.CODEC, "{\"text\":\"a\",\"click_event\":{\"action\":\"open_url\",\"url\":\"" + url + "\"}}"));
    }

    // A call that met a block state without the block registry throws the
    // guard's exception, caused by the original installation failure.
    @Test void blockRegistryFailureKeepsItsOriginalCause() throws Exception {
        var failureField = net.minecraft.world.level.block.NativeBlockRegistry.class.getDeclaredField("failure");
        failureField.setAccessible(true);
        Object previous = failureField.get(null);
        Throwable cause = new java.io.UncheckedIOException(new java.io.IOException("export failed"));
        failureField.set(null, cause);
        try {
            IllegalStateException e = NativeData.blocksMissing("Decoding PARTICLE");
            assertEquals("Decoding PARTICLE needs the native block registry", e.getMessage());
            assertSame(cause, e.getCause());
        } finally {
            failureField.set(null, previous);
        }
    }
}
