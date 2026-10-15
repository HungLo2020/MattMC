import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Differential decoding of the data-driven entity, item and biome registries,
 * compiled unchanged against Frozen and Current: deterministic mutations of
 * the vanilla data pack files (each value replaced or removed, and malformed
 * JSON text) go through the registry loader's file path, the direct codec over
 * a parsed JSON tree, and the network codec (direct when not synchronized)
 * over NBT. Each row is the outcome: "ERR", or a hash of the decoded element's
 * direct and network encodings. File errors keep the loader's exception;
 * tree and NBT errors keep their message and their partial value's
 * encodings. Arguments: the output file.
 */
public final class DataFuzzReference {
    private DataFuzzReference() {}

    static final List<String> REGISTRIES = List.of("worldgen/biome", "painting_variant", "damage_type", "banner_pattern", "jukebox_song",
        "trim_pattern", "trim_material", "instrument", "enchantment", "enchantment_provider", "cat_variant", "frog_variant", "wolf_variant",
        "wolf_sound_variant", "pig_variant", "cow_variant", "chicken_variant", "zombie_nautilus_variant");

    static final JsonElement[] REPLACEMENTS = {
        null, JsonNull.INSTANCE, new JsonPrimitive(true), new JsonPrimitive(false), num("0"), num("-1"), num("1"), num("1.5"),
        num("1e10"), num("2147483648"), num("-0.0"), num("1e400"), num("0.9999999"), new JsonPrimitive("x"), new JsonPrimitive("minecraft:pig"),
        new JsonPrimitive("#minecraft:is_forest"), new JsonPrimitive("minecraft:cave"), new JsonArray(), new JsonObject(), array("minecraft:cave"),
    };

    static JsonElement num(String text) {
        return new JsonPrimitive(new com.google.gson.internal.LazilyParsedNumber(text));
    }

    static JsonArray array(String value) {
        JsonArray array = new JsonArray();
        array.add(value);
        return array;
    }

    /** Paths to every value: object keys and array indices. */
    static void paths(JsonElement element, List<Object> prefix, List<List<Object>> out) {
        if (element instanceof JsonObject object) {
            for (String key : object.keySet()) {
                List<Object> path = new ArrayList<>(prefix);
                path.add(key);
                out.add(path);
                paths(object.get(key), path, out);
            }
        } else if (element instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                List<Object> path = new ArrayList<>(prefix);
                path.add(i);
                out.add(path);
                paths(array.get(i), path, out);
            }
        }
    }

    /** A copy with the value at {@code path} replaced, or removed when {@code value} is null. */
    static JsonElement mutate(JsonElement root, List<Object> path, JsonElement value) {
        JsonElement copy = root.deepCopy();
        JsonElement parent = copy;
        for (int i = 0; i < path.size() - 1; i++) {
            Object step = path.get(i);
            parent = step instanceof String key ? parent.getAsJsonObject().get(key) : parent.getAsJsonArray().get((Integer) step);
        }
        Object last = path.getLast();
        if (parent instanceof JsonObject object) {
            if (value == null) object.remove((String) last); else object.add((String) last, value.deepCopy());
        } else {
            JsonArray array = parent.getAsJsonArray();
            if (value == null) array.remove((int) (Integer) last); else array.set((Integer) last, value.deepCopy());
        }
        return copy;
    }

    static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)), 0, 8);
    }

    static net.minecraft.core.RegistryAccess.Frozen registries;
    static Method loadElement;

    static com.mojang.serialization.Codec<Object> direct, network;
    static ResourceKey<? extends net.minecraft.core.Registry<Object>> registryKey;

    static String outcome(Object value) throws Exception {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var nbt = registries.createSerializationContext(NbtOps.INSTANCE);
        return "OK " + hash(direct.encodeStart(ops, value).getOrThrow() + "|" + network.encodeStart(nbt, value).getOrThrow());
    }

    /** The registry loader's per-file path (`loadElementFromResource`). */
    static String file(byte[] bytes) {
        try {
            var registry = new MappedRegistry<Object>(registryKey, Lifecycle.stable());
            var key = ResourceKey.create(registryKey, ResourceLocation.withDefaultNamespace("fuzz"));
            var resource = new Resource(null, () -> new ByteArrayInputStream(bytes));
            loadElement.invoke(null, registry, direct, registries.createSerializationContext(JsonOps.INSTANCE), key, resource, RegistrationInfo.BUILT_IN);
            registry.freeze();
            return outcome(registry.getValue(key));
        } catch (Throwable e) {
            if (DEBUG && debugged++ < 3) e.printStackTrace();
            // The loader's diagnostic: the exception the per-file path throws.
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException t ? t.getCause() : e;
            return ("ERR " + cause.getClass().getName() + ": " + cause.getMessage()).replace("\n", "\\n");
        }
    }

    static final boolean DEBUG = System.getenv("BIOME_FUZZ_DEBUG") != null;
    static int debugged;

    static String tree(JsonElement element) {
        try {
            var result = direct.parse(registries.createSerializationContext(JsonOps.INSTANCE), element);
            if (DEBUG && result.isError() && debugged++ < 6) System.out.println("TREE " + result);
            return result(result);
        } catch (Throwable e) {
            return "ERR";
        }
    }

    /** An outcome, or the error's message and its partial value's encodings. */
    static String result(com.mojang.serialization.DataResult<Object> result) {
        if (result.isError()) {
            return "ERR " + result.error().orElseThrow().message() + result.resultOrPartial().map(p -> {
                try { return " (partial " + outcome(p) + ")"; } catch (Exception e) { return " (partial unencodable)"; }
            }).orElse("");
        }
        try { return outcome(result.getOrThrow()); } catch (Exception e) { return "ERR"; }
    }

    static String nbt(JsonElement element) {
        try {
            var tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, element);
            return result(network.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag));
        } catch (Throwable e) {
            return "ERR";
        }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        var worldgen = net.minecraft.resources.RegistryDataLoader.load(packs, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        registries = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, worldgen).compositeAccess();
        loadElement = net.minecraft.resources.RegistryDataLoader.class.getDeclaredMethod("loadElementFromResource",
            net.minecraft.core.WritableRegistry.class, com.mojang.serialization.Decoder.class, net.minecraft.resources.RegistryOps.class,
            ResourceKey.class, Resource.class, RegistrationInfo.class);
        loadElement.setAccessible(true);
        var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var rows = new ArrayList<String>();
        var random = new Random(20261013L);
        String[] raw = {"", "  ", "\uFEFF%s", "%s,", "%s x", "\f%s", "// c\n%s", "[%s]", "null", "{\"a\":1,}", "{'a':1}", "{\"x\":NaN}",
            "{\"x\":01}", "{\"x\":\"\u0001\"}", "{\"x\":\"\\'\"}"};
        int cases = 0;
        for (String registryName : REGISTRIES) {
            registryKey = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace(registryName));
            direct = null;
            network = null;
            for (var data : net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES)
                if (data.key().equals(registryKey)) direct = (com.mojang.serialization.Codec<Object>) (Object) data.elementCodec();
            for (var data : net.minecraft.resources.RegistryDataLoader.SYNCHRONIZED_REGISTRIES)
                if (data.key().equals(registryKey)) network = (com.mojang.serialization.Codec<Object>) (Object) data.elementCodec();
            if (network == null) network = direct;
            var files = new java.util.TreeMap<String, String>();
            resources.listResources(registryName, l -> l.getPath().endsWith(".json")).forEach((location, resource) -> {
                try (var in = resource.open()) { files.put(location.toString(), new String(in.readAllBytes(), StandardCharsets.UTF_8)); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            // Every path of the first files; a sample of the rest.
            var everyPath = new java.util.HashSet<>(files.keySet().stream().limit(registryName.equals("worldgen/biome") ? 0 : 2).toList());
            if (registryName.equals("worldgen/biome")) everyPath.addAll(List.of("minecraft:worldgen/biome/plains.json", "minecraft:worldgen/biome/basalt_deltas.json", "minecraft:worldgen/biome/soul_sand_valley.json"));
            int rawFiles = 0;
            for (var entry : files.entrySet()) {
                String name = entry.getKey();
                String text = entry.getValue();
                JsonElement root = JsonParser.parseString(text);
                rows.add("fuzz " + name + " original file=" + file(text.getBytes(StandardCharsets.UTF_8)) + " tree=" + tree(root) + " nbt=" + nbt(root));
                var paths = new ArrayList<List<Object>>();
                paths(root, List.of(), paths);
                boolean full = everyPath.contains(name);
                List<List<Object>> chosen = paths;
                if (!full) {
                    chosen = new ArrayList<>();
                    for (int i = 0; i < Math.min(40, paths.size()); i++) chosen.add(paths.get(random.nextInt(paths.size())));
                }
                for (var path : chosen) {
                    for (int r = 0; r < REPLACEMENTS.length; r++) {
                        if (!full && random.nextInt(4) != 0) continue;
                        JsonElement mutated = mutate(root, path, REPLACEMENTS[r]);
                        byte[] bytes = mutated.toString().getBytes(StandardCharsets.UTF_8);
                        rows.add("fuzz " + name + " " + path + "=" + r + " file=" + file(bytes) + " tree=" + tree(mutated) + " nbt=" + nbt(mutated));
                        cases++;
                    }
                }
                if (rawFiles++ < 3) {
                    for (int r = 0; r < raw.length; r++) {
                        String body = raw[r].contains("%s") ? raw[r].replace("%s", text) : raw[r];
                        rows.add("fuzz " + name + " raw" + r + " file=" + file(body.getBytes(StandardCharsets.UTF_8)));
                        cases++;
                    }
                    // Duplicate keys: the last value wins in the first position.
                    String duplicated = text.replaceFirst("\\{", "{\"" + root.getAsJsonObject().keySet().iterator().next() + "\": 5.0,");
                    rows.add("fuzz " + name + " duplicate file=" + file(duplicated.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
        Files.writeString(Path.of(args[0]), String.join("\n", rows) + "\n");
        long errors = rows.stream().filter(r -> r.contains("=ERR")).count();
        System.out.println("DATA_FUZZ_REFERENCE rows=" + rows.size() + " cases=" + cases + " with_errors=" + errors + " sha=" + hash(String.join("\n", rows)));
    }
}
