import com.google.gson.*;
import com.mojang.serialization.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.entity.decoration.PaintingVariant;

/** Compile unchanged against Frozen/pre/current. Outputs live mutation and callback contracts.
 * Arguments: output file. Loads actual vanilla data-driven registries; never changes them.
 */
public final class ComponentMutationContractReference {
    static HolderLookup.Provider registries;
    static RegistryOps<JsonElement> ops(int factory, DynamicOps<JsonElement> base, Runnable mutate, List<String> calls) {
        HolderLookup.Provider changed = new HolderLookup.Provider() {
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() { throw new AssertionError("unexpected enumeration"); }
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return registries.lookup(key).map(parent -> new HolderLookup.RegistryLookup.Delegate<T>() {
                    public HolderLookup.RegistryLookup<T> parent() { return parent; }
                    public Optional<Holder.Reference<T>> get(ResourceKey<T> value) {
                        calls.add(value.location().toString());
                        if (key.equals(Registries.TRIM_MATERIAL) || key.equals(Registries.BANNER_PATTERN)) mutate.run();
                        return parent.get(value);
                    }
                });
            }
        };
        if (factory == 0) return RegistryOps.create(base, new RegistryOps.RegistryInfoLookup() {
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return changed.lookup(key).map(RegistryOps.RegistryInfo::fromRegistryLookup);
            }
        });
        if (factory == 1) return RegistryOps.create(base, changed);
        return RegistryOps.create(base, new HolderLookup.Provider() {
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() { return changed.listRegistryKeys(); }
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) { return changed.lookup(key).map(value -> value); }
        });
    }
    static JsonElement input(boolean list) {
        return JsonParser.parseString(list
            ? "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"red\"},{\"pattern\":\"minecraft:cross\",\"color\":\"blue\"}]"
            : "{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:coast\"}");
    }
    static void mutate(JsonElement input, boolean list, String mode) {
        if (mode.equals("throw")) throw new UnsupportedOperationException("original getter failure");
        if (mode.equals("append")) { input.getAsJsonArray().add(input.getAsJsonArray().get(0).deepCopy()); return; }
        if (list) input.getAsJsonArray().get(1).getAsJsonObject().addProperty("color", mode.equals("invalid") ? "invalid_color" : "green");
        else input.getAsJsonObject().addProperty("pattern", mode.equals("invalid") ? "minecraft:missing_pattern" : "minecraft:spire");
        if (mode.equals("reentry")) {
            var nested = ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:food\":{\"nutrition\":3,\"saturation\":1}}}")).getOrThrow();
            if (nested.get(DataComponents.FOOD).nutrition() != 3) throw new AssertionError("reentry result");
        }
    }
    static String outcome(DataResult<?> result, boolean list, String route) {
        String value = result.resultOrPartial().map(object -> {
            Object component = object;
            if (route.equals("patch")) component = ((DataComponentPatch)object).get(list ? DataComponents.BANNER_PATTERNS : DataComponents.TRIM).orElseThrow();
            if (route.equals("stack")) component = ((ItemStack)object).get(list ? DataComponents.BANNER_PATTERNS : DataComponents.TRIM);
            if (component instanceof ArmorTrim trim) return "pattern=" + trim.pattern().unwrapKey().orElseThrow().location();
            if (component instanceof BannerPatternLayers layers) return "colors=" + layers.layers().stream().map(layer -> layer.color().getName()).toList();
            return "unexpected=" + component;
        }).orElse("none");
        return "error=" + result.error().map(DataResult.Error::message) + " value=" + value;
    }
    static Throwable unwrap(Throwable thrown) {
        return thrown instanceof java.lang.reflect.InvocationTargetException invocation ? invocation.getCause() : thrown;
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        List<String> rows = new ArrayList<>();
        var nullKey = ResourceKey.<Object>createRegistryKey(ResourceLocation.parse("test:null_registry"));
        var invalidAccess = new RegistryAccess() {
            public <T> Optional<Registry<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) { throw new AssertionError("unexpected lookup"); }
            public Stream<RegistryAccess.RegistryEntry<?>> registries() { return Stream.of(new RegistryAccess.RegistryEntry<>(nullKey, null)); }
        };
        try { invalidAccess.freeze(); rows.add("invalid_registry unexpected success"); }
        catch (NullPointerException failure) { rows.add("invalid_registry " + failure.getClass().getName() + ":" + failure.getMessage()); }
        try (var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            var loaded = RegistryDataLoader.load(packs, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
            registries = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, loaded).compositeAccess();
            for (int factory = 0; factory < 3; factory++) for (boolean list : new boolean[]{false, true})
                for (String mode : list ? List.of("mutate", "invalid", "append", "throw", "reentry") : List.of("mutate", "invalid", "throw", "reentry"))
                for (String route : List.of("direct", "patch", "stack")) {
                    JsonElement input = input(list); List<String> calls = new ArrayList<>();
                    var ops = ops(factory, JsonOps.INSTANCE, () -> mutate(input, list, mode), calls);
                    String result;
                    try {
                        if (route.equals("direct")) result = outcome((list ? BannerPatternLayers.CODEC : ArmorTrim.CODEC).parse(ops, input), list, route);
                        else {
                            var patch = new JsonObject(); patch.add(list ? "minecraft:banner_patterns" : "minecraft:trim", input);
                            if (route.equals("patch")) result = outcome(DataComponentPatch.CODEC.parse(ops, patch), list, route);
                            else { var stack = new JsonObject(); stack.addProperty("id", "minecraft:stick"); stack.add("components", patch); result = outcome(ItemStack.CODEC.parse(ops, stack), list, route); }
                        }
                    } catch (Throwable thrown) { result = "thrown=" + unwrap(thrown).getClass().getName() + ":" + unwrap(thrown).getMessage(); }
                    rows.add("factory=" + factory + " list=" + list + " mode=" + mode + " route=" + route + " " + result + " calls=" + calls);
                }
            for (int factory = 0; factory < 3; factory++) for (boolean list : new boolean[]{false, true})
                for (String mode : List.of("mutate", "throw", "reentry")) for (String route : List.of("tree", "file")) {
                    var components = new JsonObject(); components.add(list ? "minecraft:banner_patterns" : "minecraft:trim", input(list));
                    String source = "{\"width\":2,\"height\":3,\"asset_id\":\"minecraft:a\",\"title\":{\"text\":\"a\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:stick\",\"components\":" + components + "}}}";
                    JsonElement[] live = {null}; List<String> calls = new ArrayList<>();
                    var base = new DelegatingOps<JsonElement>(JsonOps.INSTANCE) {
                        public DataResult<MapLike<JsonElement>> getMap(JsonElement node) {
                            if (node.isJsonObject() && node.getAsJsonObject().has("material")) live[0] = node;
                            return super.getMap(node);
                        }
                        public DataResult<java.util.function.Consumer<java.util.function.Consumer<JsonElement>>> getList(JsonElement node) {
                            if (node.isJsonArray()) live[0] = node;
                            return super.getList(node);
                        }
                    };
                    var ops = ops(factory, base, () -> mutate(Objects.requireNonNull(live[0], "component node"), list, mode), calls);
                    String result;
                    try {
                        Codec<PaintingVariant> codec = PaintingVariant.DIRECT_CODEC;
                        DataResult<?> decoded;
                        try { decoded = route.equals("file") ? (DataResult<?>)codec.getClass().getMethod("decodeFile", RegistryOps.class, byte[].class)
                            .invoke(codec, ops, source.getBytes(StandardCharsets.UTF_8)) : codec.parse(ops, JsonParser.parseString(source)); }
                        catch (NoSuchMethodException originalFilePath) { decoded = codec.parse(ops, JsonParser.parseString(source)); }
                        result = "error=" + decoded.error().map(DataResult.Error::message) + " partial=" + decoded.hasResultOrPartial();
                        if (decoded.hasResultOrPartial()) {
                            var encoded = codec.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), (PaintingVariant)decoded.resultOrPartial().orElseThrow()).getOrThrow();
                            var item = encoded.getAsJsonObject().getAsJsonObject("title").getAsJsonObject("hover_event").getAsJsonObject("components");
                            result += list ? " color=" + item.getAsJsonArray("minecraft:banner_patterns").get(1).getAsJsonObject().get("color").getAsString()
                                : " pattern=" + item.getAsJsonObject("minecraft:trim").get("pattern").getAsString();
                        }
                    } catch (Throwable thrown) { result = "thrown=" + unwrap(thrown).getClass().getName() + ":" + unwrap(thrown).getMessage(); }
                    rows.add("factory=" + factory + " list=" + list + " mode=" + mode + " route=" + route + " " + result + " calls=" + calls);
                }
            for (String route : List.of("direct", "patch", "stack")) {
                var food = JsonParser.parseString("{\"saturation\":1}").getAsJsonObject(); int[] calls = {0};
                food.add("nutrition", new JsonPrimitive(new Number() {
                    public int intValue() { calls[0]++; food.addProperty("saturation", 7); return 4; }
                    public long longValue() { return 4; } public float floatValue() { return 4; } public double doubleValue() { return 4; }
                    public String toString() { return "4"; }
                }));
                net.minecraft.world.food.FoodProperties value;
                if (route.equals("direct")) value = net.minecraft.world.food.FoodProperties.DIRECT_CODEC.parse(JsonOps.INSTANCE, food).getOrThrow();
                else {
                    var patch = new JsonObject(); patch.add("minecraft:food", food);
                    if (route.equals("patch")) value = DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, patch).getOrThrow().get(DataComponents.FOOD).orElseThrow();
                    else { var stack = new JsonObject(); stack.addProperty("id", "minecraft:stick"); stack.add("components", patch); value = ItemStack.CODEC.parse(JsonOps.INSTANCE, stack).getOrThrow().get(DataComponents.FOOD); }
                }
                rows.add("number route=" + route + " saturation=" + value.saturation() + " intCalls=" + calls[0]);
            }
        }
        Files.write(Path.of(args[0]), rows);
        System.out.println("COMPONENT_MUTATION_CONTRACT rows=" + rows.size());
    }
}
