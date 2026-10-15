import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapLike;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.DelegatingOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;

/** Full Codec results that parse-only value comparisons omit. Compile this
 * unchanged against Frozen, the sealed pre-change runtime and the candidate.
 * Arguments: output file. No candidate implementation supplies expectations.
 */
public final class ComponentCodecContractReference {
    private ComponentCodecContractReference() {}

    private static class MarkedOps extends DelegatingOps<JsonElement> {
        MarkedOps() { super(JsonOps.INSTANCE); }
        @Override public DataResult<Number> getNumberValue(JsonElement value) {
            return super.getNumberValue(value).setLifecycle(Lifecycle.deprecated(2));
        }
        @Override public DataResult<String> getStringValue(JsonElement value) {
            return super.getStringValue(value).setLifecycle(Lifecycle.stable());
        }
        @Override public DataResult<MapLike<JsonElement>> getMap(JsonElement value) {
            return super.getMap(value).setLifecycle(Lifecycle.deprecated(4));
        }
    }

    private static final class PartialMapOps extends MarkedOps {
        @Override public DataResult<MapLike<JsonElement>> getMap(JsonElement value) {
            return super.getMap(value).flatMap(map -> DataResult.error(() -> "custom map error", map, Lifecycle.deprecated(1)));
        }
    }

    private static final class PartialListOps extends MarkedOps {
        @Override public DataResult<Consumer<Consumer<JsonElement>>> getList(JsonElement value) {
            return super.getList(value).flatMap(list -> DataResult.error(() -> "custom list error", list, Lifecycle.deprecated(1)));
        }
    }

    private static final class PartialNumberOps extends MarkedOps {
        @Override public DataResult<Number> getNumberValue(JsonElement value) {
            return super.getNumberValue(value).flatMap(number -> DataResult.error(() -> "custom number error", number, Lifecycle.deprecated(3)));
        }
    }

    private static final class JsonSubclassOps extends JsonOps {
        JsonSubclassOps() { super(false); }
        @Override public DataResult<MapLike<JsonElement>> getMap(JsonElement value) {
            return super.getMap(value).flatMap(map -> DataResult.error(() -> "subclass map error", map, Lifecycle.deprecated(1)));
        }
    }

    private static final String[][] INPUTS = {
        {"food", "{\"nutrition\":4,\"saturation\":1}"},
        {"food", "{\"nutrition\":4,\"saturation\":1,\"can_always_eat\":\"bad\"}"},
        {"food", "{}"}, {"enchantable", "{\"value\":2}"},
        {"bees", "[]"}, {"bees", "[{}]"},
        {"suspicious_stew_effects", "[]"},
        {"banner_patterns", "[]"},
        {"written_book_content", "{\"title\":\"t\",\"author\":\"a\"}"},
        {"break_sound", "{\"sound_id\":\"minecraft:a\"}"}
    };

    @SuppressWarnings("unchecked")
    private static Codec<Object> codec(String path) {
        return (Codec<Object>)BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ResourceLocation.withDefaultNamespace(path)).codecOrThrow();
    }

    private static String result(DataResult<?> value) {
        return "lifecycle=" + value.lifecycle() + " error=" + value.error().map(DataResult.Error::message)
            + " value=" + value.resultOrPartial();
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        List<String> rows = new ArrayList<>();
        int context = 0;
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (DynamicOps<JsonElement> ops : List.of(JsonOps.INSTANCE, new MarkedOps(), new PartialMapOps(), new PartialListOps(),
                new PartialNumberOps(), net.minecraft.resources.RegistryOps.create(new PartialMapOps(), registries),
                JsonOps.COMPRESSED, new JsonSubclassOps())) {
            String name = (context++) + ":" + ops.getClass().getSimpleName();
            for (String[] row : INPUTS) {
                Codec<Object> codec = codec(row[0]);
                JsonElement input = JsonParser.parseString(row[1]);
                JsonObject patch = new JsonObject();
                patch.add("minecraft:" + row[0], input);
                JsonObject stack = new JsonObject();
                stack.addProperty("id", "minecraft:stick");
                stack.add("components", patch);
                var nested = JsonParser.parseString("{\"id\":\"minecraft:shulker_box\",\"components\":{\"minecraft:container\":[{\"slot\":0}]}}").getAsJsonObject();
                nested.getAsJsonObject("components").getAsJsonArray("minecraft:container").get(0).getAsJsonObject().add("item", stack);
                Codec<?>[] codecs = {codec, net.minecraft.core.component.DataComponentPatch.CODEC,
                    net.minecraft.world.item.ItemStack.CODEC, net.minecraft.world.item.ItemStack.CODEC};
                JsonElement[] forms = {input, patch, stack, nested};
                for (int route = 0; route < forms.length; route++) {
                    JsonElement form = forms[route];
                    DataResult<? extends Pair<?, JsonElement>> decoded = codecs[route].decode(ops, form);
                    rows.add("decode " + name + " " + row[0] + " route=" + route + " " + row[1] + " lifecycle=" + decoded.lifecycle()
                        + " error=" + decoded.error().map(DataResult.Error::message) + " remainder="
                        + decoded.resultOrPartial().map(pair -> pair.getSecond().toString() + " same_input=" + (pair.getSecond() == form)));
                }
            }
            for (int nutrition : new int[]{4, -1}) {
                for (String prefix : new String[]{"null", "{}", "{\"keep\":true}", "[]", "7"}) {
                    rows.add("encode " + name + " food=" + nutrition + " prefix=" + prefix + " "
                        + result(FoodProperties.DIRECT_CODEC.encode(new FoodProperties(nutrition, 1, false), ops, JsonParser.parseString(prefix))));
                }
            }
            for (String path : new String[]{"bees", "suspicious_stew_effects", "banner_patterns"}) {
                Codec<Object> codec = codec(path);
                Object empty = codec.parse(ops, JsonParser.parseString("[]")).resultOrPartial().orElseThrow();
                for (String prefix : new String[]{"null", "[]", "[{}]", "{}", "7"}) {
                    rows.add("encode " + name + " " + path + " prefix=" + prefix + " "
                        + result(codec.encode(empty, ops, JsonParser.parseString(prefix))));
                }
            }
        }
        Files.write(Path.of(args[0]), rows);
        System.out.println("COMPONENT_CONTRACT_REFERENCE rows=" + rows.size());
    }
}
