import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.util.valueproviders.ClampedInt;
import net.minecraft.util.valueproviders.ClampedNormalFloat;
import net.minecraft.util.valueproviders.ClampedNormalInt;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Differential decoding and encoding of the value types definitions embed
 * (int and float providers, level-based values), compiled unchanged against
 * Frozen and Current. Sections: "values" runs each codec (and its bounded
 * forms) over generated inputs and their mutations in JSON and NBT, and over
 * Java-built values; each row is the error message or the JSON and NBT
 * encodings. "worldgen" dumps every loaded worldgen registry element's
 * encoding, covering the providers features, carvers and enchantments embed.
 * Arguments: the output file, then sections.
 */
public final class ValueFuzzReference {
    private ValueFuzzReference() {}

    static final JsonElement[] REPLACEMENTS = {
        null, JsonNull.INSTANCE, new JsonPrimitive(true), num("0"), num("-1"), num("3"), num("1.5"), num("1e10"), num("2147483648"),
        num("-0.0"), num("0.9999999"), num("-2147483649"), new JsonPrimitive("x"), new JsonPrimitive("minecraft:uniform"),
        new JsonPrimitive("clamped"), new JsonPrimitive("minecraft:constant"), new JsonPrimitive("other:uniform"), new JsonArray(), new JsonObject(),
    };

    static JsonElement num(String text) {
        return new JsonPrimitive(new com.google.gson.internal.LazilyParsedNumber(text));
    }

    static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    static final String[] INT_INPUTS = {
        "0", "5", "-3", "1.7", "\"5\"", "true", "[]", "{}", "{\"type\":\"constant\",\"value\":4}", "{\"type\":\"minecraft:constant\",\"value\":0}",
        "{\"type\":\"uniform\",\"min_inclusive\":1,\"max_inclusive\":4}", "{\"type\":\"uniform\",\"min_inclusive\":4,\"max_inclusive\":1}",
        "{\"min_inclusive\":2,\"max_inclusive\":6,\"type\":\"biased_to_bottom\"}",
        "{\"type\":\"clamped\",\"source\":{\"type\":\"uniform\",\"min_inclusive\":-4,\"max_inclusive\":9},\"min_inclusive\":0,\"max_inclusive\":5}",
        "{\"type\":\"clamped\",\"source\":7,\"min_inclusive\":0,\"max_inclusive\":5}",
        "{\"type\":\"weighted_list\",\"distribution\":[{\"data\":1,\"weight\":3},{\"data\":{\"type\":\"uniform\",\"min_inclusive\":2,\"max_inclusive\":8},\"weight\":1}]}",
        "{\"type\":\"weighted_list\",\"distribution\":[]}", "{\"type\":\"weighted_list\",\"distribution\":[{\"data\":1,\"weight\":-1}]}",
        "{\"type\":\"clamped_normal\",\"mean\":0.5,\"deviation\":2,\"min_inclusive\":-1,\"max_inclusive\":3}",
        "{\"type\":\"clamped_normal\",\"mean\":0.5,\"deviation\":2,\"min_inclusive\":3,\"max_inclusive\":-1}",
        "{\"type\":\"nope\"}", "{\"value\":3}",
    };
    static final String[] FLOAT_INPUTS = {
        "0", "0.25", "-3", "1e10", "1e-5", "\"5\"", "{\"type\":\"constant\",\"value\":0.5}", "{\"type\":\"uniform\",\"min_inclusive\":0.1,\"max_exclusive\":0.9}",
        "{\"type\":\"uniform\",\"min_inclusive\":0.5,\"max_exclusive\":0.5}", "{\"type\":\"uniform\",\"min_inclusive\":1e9,\"max_exclusive\":1e-9}",
        "{\"type\":\"clamped_normal\",\"mean\":0.5,\"deviation\":2,\"min\":-1,\"max\":3}", "{\"type\":\"clamped_normal\",\"mean\":0.5,\"deviation\":2,\"min\":3.5,\"max\":-1.25}",
        "{\"type\":\"trapezoid\",\"min\":0,\"max\":4,\"plateau\":2}", "{\"type\":\"trapezoid\",\"min\":0,\"max\":4,\"plateau\":5}",
        "{\"type\":\"trapezoid\",\"min\":4,\"max\":0,\"plateau\":0}", "{\"type\":\"weighted_list\"}",
    };
    static final String[] LEVEL_INPUTS = {
        "2", "0.5", "\"x\"", "{\"type\":\"linear\",\"base\":2,\"per_level_above_first\":0.5}", "{\"type\":\"levels_squared\",\"added\":1}",
        "{\"type\":\"clamped\",\"value\":{\"type\":\"linear\",\"base\":1,\"per_level_above_first\":1},\"min\":0,\"max\":4}",
        "{\"type\":\"clamped\",\"value\":3,\"min\":4,\"max\":4}", "{\"type\":\"fraction\",\"numerator\":1,\"denominator\":{\"type\":\"levels_squared\",\"added\":2}}",
        "{\"type\":\"lookup\",\"values\":[1,2.5,3],\"fallback\":{\"type\":\"linear\",\"base\":4,\"per_level_above_first\":1}}",
        "{\"type\":\"lookup\",\"values\":[],\"fallback\":0}", "{\"type\":\"constant\",\"value\":1}",
    };

    static final String[] PARTICLE_INPUTS = {
        "{\"type\":\"dust\",\"color\":[1,0,0.5],\"scale\":1}", "{\"type\":\"dust\",\"color\":16711680,\"scale\":5}",
        "{\"type\":\"minecraft:block\",\"block_state\":{\"Name\":\"minecraft:oak_stairs\",\"Properties\":{\"facing\":\"east\",\"half\":\"top\",\"waterlogged\":\"true\",\"bogus\":\"x\"}}}",
        "{\"type\":\"falling_dust\",\"block_state\":\"minecraft:sand\"}", "{\"type\":\"block_crumble\",\"block_state\":{\"Name\":\"minecraft:stone\"}}",
        "{\"type\":\"item\",\"item\":\"minecraft:diamond\"}", "{\"type\":\"item\",\"item\":{\"id\":\"minecraft:stick\",\"count\":1}}",
        "{\"type\":\"vibration\",\"destination\":{\"type\":\"block\",\"pos\":[1,2,3]},\"arrival_in_ticks\":5}",
        "{\"type\":\"vibration\",\"destination\":{\"type\":\"entity\",\"source_entity\":[1,2,3,4],\"y_offset\":0.5},\"arrival_in_ticks\":5}",
        "{\"type\":\"trail\",\"target\":[1.5,2,3],\"color\":123,\"duration\":4}", "{\"type\":\"effect\",\"color\":[0.5,0.25,1],\"power\":2}",
        "{\"type\":\"instant_effect\"}", "{\"type\":\"entity_effect\",\"color\":[1,0,0,0.5]}", "{\"type\":\"flash\",\"color\":-1}",
        "{\"type\":\"dragon_breath\"}", "{\"type\":\"dragon_breath\",\"power\":1.0}", "{\"type\":\"sculk_charge\",\"roll\":0.5}",
        "{\"type\":\"shriek\",\"delay\":3}", "{\"type\":\"ash\"}", "{\"type\":\"ash\",\"extra\":1}", "{\"type\":\"nope\"}",
        "{\"type\":\"dust_color_transition\",\"from_color\":[1,1,1],\"to_color\":255,\"scale\":0.5}",
    };
    static final String[] COMPONENT_INPUTS = {
        "\"hello\"",
        "[\"a\",\"b\"]",
        "[]",
        "[{\"text\":\"a\",\"bold\":true},\"b\",{\"translate\":\"x\"}]",
        "5",
        "true",
        "{}",
        "{\"text\":\"hi\",\"color\":\"red\",\"bold\":true,\"italic\":false,\"underlined\":true,\"strikethrough\":false,\"obfuscated\":true,\"insertion\":\"ins\",\"font\":\"minecraft:uniform\",\"shadow_color\":-1}",
        "{\"text\":\"c\",\"color\":\"#ff00AA\"}",
        "{\"text\":\"c\",\"color\":\"#GG\"}",
        "{\"text\":\"c\",\"color\":\"#1000000\"}",
        "{\"text\":\"c\",\"color\":\"#+ff\"}",
        "{\"text\":\"c\",\"color\":\"nope\"}",
        "{\"text\":\"c\",\"shadow_color\":[1,0,0,0.5]}",
        "{\"translate\":\"chat.type.text\",\"with\":[\"a\",1,2.5,true,{\"text\":\"b\",\"bold\":true},{\"text\":\"plain\"}],\"fallback\":\"fb\"}",
        "{\"translate\":\"x\",\"with\":[]}",
        "{\"translate\":\"x\",\"with\":[[1]]}",
        "{\"translate\":\"x\",\"fallback\":5}",
        "{\"keybind\":\"key.jump\"}",
        "{\"score\":{\"name\":\"@p\",\"objective\":\"obj\"}}",
        "{\"score\":{\"name\":\"Steve\",\"objective\":\"o\"}}",
        "{\"score\":{\"name\":5,\"objective\":\"o\"}}",
        "{\"selector\":\"@e[type=pig]\",\"separator\":\", \"}",
        "{\"nbt\":\"Pos[0]\",\"entity\":\"@s\",\"interpret\":true,\"separator\":{\"text\":\"-\"}}",
        "{\"nbt\":\"a\",\"block\":\"~ ~ ~\"}",
        "{\"nbt\":\"a\",\"storage\":\"minecraft:x\"}",
        "{\"nbt\":\"a\",\"interpret\":\"x\",\"storage\":\"a:b\"}",
        "{\"type\":\"text\",\"text\":\"t\"}",
        "{\"type\":\"bogus\",\"text\":\"t\"}",
        "{\"type\":\"translatable\",\"text\":\"t\"}",
        "{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"storage\",\"storage\":\"a:b\"}",
        "{\"object\":\"atlas\",\"sprite\":\"minecraft:block/stone\"}",
        "{\"sprite\":\"minecraft:item/apple\",\"atlas\":\"minecraft:items\"}",
        "{\"text\":\"x\",\"extra\":[]}",
        "{\"text\":\"x\",\"extra\":[\"y\",{\"text\":\"z\",\"italic\":true}]}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"open_url\",\"url\":\"https://example.com\"}}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/say hi\"}}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"suggest_command\",\"command\":\"a\\u00a7b\"}}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"change_page\",\"page\":3}}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"copy_to_clipboard\",\"value\":\"v\"}}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"open_file\",\"path\":\"p\"}}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"custom\",\"id\":\"a:b\",\"payload\":{\"x\":1}}}",
        "{\"text\":\"c\",\"click_event\":{\"action\":\"bogus\"}}",
        "{\"text\":\"h\",\"hover_event\":{\"action\":\"show_text\",\"value\":{\"text\":\"t\",\"color\":\"gold\"}}}",
        "{\"text\":\"h\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:diamond\",\"count\":2}}",
        "{\"text\":\"h\",\"hover_event\":{\"action\":\"show_entity\",\"id\":\"minecraft:pig\",\"uuid\":[1,2,3,4],\"name\":\"Bob\"}}",
        "{\"text\":\"h\",\"hover_event\":{\"action\":\"show_entity\",\"id\":\"minecraft:pig\",\"uuid\":\"00000000-0000-0001-0000-000000000002\"}}",
        "{\"text\":\"h\",\"hover_event\":{\"action\":\"show_entity\",\"id\":\"minecraft:pig\",\"uuid\":\"1-2-3-4-5\"}}",
    };
    static final String[] STACK_INPUTS = {
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:dyed_color\":[1,0.5,0]}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:map_color\":123}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:map_id\":7}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:ominous_bottle_amplifier\":3}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:potion_duration_scale\":0.5}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:base_color\":\"red\"}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:custom_model_data\":{\"floats\":[1.5],\"flags\":[true],\"strings\":[\"s\"],\"colors\":[255,[1,0,0]]}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:use_cooldown\":{\"seconds\":1.5,\"cooldown_group\":\"a:b\"}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:weapon\":{\"item_damage_per_attack\":2,\"disable_blocking_for_seconds\":1.0}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:tooltip_display\":{\"hide_tooltip\":true,\"hidden_components\":[\"minecraft:damage\",\"minecraft:lore\",\"minecraft:damage\"]}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:lodestone_tracker\":{\"target\":{\"dimension\":\"minecraft:overworld\",\"pos\":[1,2,3]},\"tracked\":false}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:container_loot\":{\"loot_table\":\"minecraft:chests/simple_dungeon\",\"seed\":123456789012}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:firework_explosion\":{\"shape\":\"star\",\"colors\":[1,2],\"fade_colors\":[3],\"has_trail\":true,\"has_twinkle\":false}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:fireworks\":{\"flight_duration\":2,\"explosions\":[{\"shape\":\"burst\"}]}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:writable_book_content\":{\"pages\":[\"page one\",{\"raw\":\"r\",\"filtered\":\"f\"}]}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:block_state\":{\"facing\":\"east\",\"lit\":\"true\"}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:container\":[{\"slot\":3,\"item\":{\"id\":\"minecraft:stone\"}},{\"slot\":1,\"item\":{\"id\":\"minecraft:dirt\",\"count\":4}},{\"slot\":3,\"item\":{\"id\":\"minecraft:apple\"}}]}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:pot_decorations\":[\"minecraft:angler_pottery_sherd\",\"minecraft:brick\"]}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:charged_projectiles\":[{\"id\":\"minecraft:arrow\"}]}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:use_remainder\":{\"id\":\"minecraft:bowl\"}}}",
        "{\"id\":\"minecraft:stick\"}",
        "{\"id\":\"minecraft:stick\",\"count\":5}",
        "{\"id\":\"minecraft:stick\",\"count\":100}",
        "{\"id\":\"minecraft:air\"}",
        "{\"id\":\"nope\"}",
        "\"minecraft:stick\"",
        "{\"id\":\"minecraft:diamond_sword\",\"components\":{\"minecraft:damage\":5,\"minecraft:custom_name\":\"Excalibur\",\"minecraft:lore\":[\"a\",{\"text\":\"b\",\"color\":\"red\"}],\"minecraft:rarity\":\"epic\",\"minecraft:unbreakable\":{},\"!minecraft:max_damage\":{},\"minecraft:enchantment_glint_override\":true,\"minecraft:item_model\":\"minecraft:stick\",\"minecraft:repair_cost\":3}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:custom_data\":{\"a\":1},\"minecraft:dyed_color\":123,\"minecraft:max_stack_size\":16,\"minecraft:tooltip_style\":\"a:b\"}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"bogus:x\":1,\"minecraft:damage\":\"q\",\"!minecraft:max_damage\":{},\"minecraft:rarity\":\"epic\"}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:damage\":5,\"!minecraft:damage\":{}}}",
        "{\"id\":\"minecraft:stick\",\"components\":{\"minecraft:creative_slot_lock\":{}}}",
        "{\"id\":\"minecraft:shulker_box\",\"components\":{\"minecraft:container\":[{\"slot\":0,\"item\":{\"id\":\"minecraft:stone\",\"count\":2,\"components\":{\"minecraft:custom_name\":\"x\"}}}]}}",
        "{\"id\":\"minecraft:bundle\",\"components\":{\"minecraft:bundle_contents\":[{\"id\":\"minecraft:apple\"},{\"id\":\"minecraft:air\"}]}}",
    };
    static final String[] SLOT_INPUTS = {
        "{\"Slot\":3,\"id\":\"minecraft:stick\",\"count\":2}",
        "{\"Slot\":300,\"id\":\"minecraft:stick\"}",
        "{\"Slot\":-1,\"id\":\"minecraft:stick\"}",
        "{\"id\":\"minecraft:stick\"}",
        "{\"Slot\":\"x\",\"id\":\"minecraft:stick\"}",
        "{\"Slot\":1.5,\"id\":\"minecraft:diamond_sword\",\"components\":{\"minecraft:damage\":4,\"minecraft:custom_name\":\"n\"}}",
        "{\"Slot\":2}",
        "[]",
    };
    static final String[] POSITION_INPUTS = {
        "{\"type\":\"block\",\"pos\":[1,2,3]}", "{\"type\":\"entity\",\"source_entity\":[1,2,3,4],\"y_offset\":0.5}",
        "{\"type\":\"entity\",\"source_entity\":[1,2,3,4,5]}", "{\"type\":\"minecraft:block\",\"pos\":[1,2]}",
    };

    static String outcome(Codec<Object> codec, DataResult<?> result) {
        if (result.isError()) {
            // A partial's contents are compared too, through both encodings.
            return "ERR " + result.error().orElseThrow().message() + (result.hasResultOrPartial() ? " (partial " + partial(codec, result) + ")" : "");
        }
        Object value = result.getOrThrow();
        return "OK " + encode(codec, value);
    }

    static String partial(Codec<Object> codec, DataResult<?> result) {
        try {
            return encode(codec, result.resultOrPartial().orElseThrow());
        } catch (RuntimeException e) {
            return "encode " + thrown(e);
        }
    }

    static String encode(Codec<Object> codec, Object value) {
        DataResult<JsonElement> j = codec.encodeStart(JsonOps.INSTANCE, value);
        DataResult<?> n = codec.encodeStart(NbtOps.INSTANCE, value);
        return (j.isError() ? "ERR " + j.error().orElseThrow().message() : j.getOrThrow().toString()) + " nbt="
            + (n.isError() ? "ERR " + n.error().orElseThrow().message() : n.getOrThrow().toString());
    }

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

    @SuppressWarnings("unchecked")
    static Codec<Object> field(Class<?> owner, String name) throws ReflectiveOperationException {
        return (Codec<Object>) owner.getField(name).get(null);
    }

    @SuppressWarnings("unchecked")
    static Codec<Object> bounded(Class<?> owner, Object min, Object max) throws ReflectiveOperationException {
        Class<?> type = min instanceof Integer ? int.class : float.class;
        return (Codec<Object>) owner.getMethod("codec", type, type).invoke(null, min, max);
    }

    static void decodeAll(List<String> out, String name, Codec<Object> codec, String[] inputs) {
        decodeAll(out, name, codec, codec, inputs);
    }

    /** Decodes with {@code codec}, encodes results with {@code encoder}. */
    static void decodeAll(List<String> out, String name, Codec<Object> codec, Codec<Object> encoder, String[] inputs) {
        for (String text : inputs) {
            JsonElement root = json(text);
            List<JsonElement> cases = new ArrayList<>();
            cases.add(root);
            var paths = new ArrayList<List<Object>>();
            paths(root, List.of(), paths);
            for (var path : paths) for (JsonElement r : REPLACEMENTS) cases.add(mutate(root, path, r));
            for (JsonElement input : cases) {
                String json;
                try {
                    json = outcome(encoder, codec.parse(JsonOps.INSTANCE, input));
                } catch (Throwable e) {
                    json = thrown(e);
                }
                String nbt;
                try {
                    nbt = outcome(encoder, codec.parse(NbtOps.INSTANCE, JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, input)));
                } catch (Throwable e) {
                    nbt = thrown(e);
                }
                out.add("value " + name + " " + input + " json=" + json + " | nbt=" + nbt);
            }
        }
    }

    /** A thrown exception's class and message, which callers can observe. */
    static String thrown(Throwable e) {
        return "THROW " + e.getClass().getName() + ": " + e.getMessage();
    }

    static void encodeAll(List<String> out, String name, Codec<Object> codec, List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            String row;
            try {
                row = encode(codec, value);
            } catch (Throwable e) {
                row = thrown(e);
            }
            out.add("built " + name + " " + i + " " + row);
        }
    }

    static void values(List<String> out) throws ReflectiveOperationException {
        Class<?> ints = net.minecraft.util.valueproviders.IntProvider.class;
        Class<?> floats = net.minecraft.util.valueproviders.FloatProvider.class;
        Map<String, Codec<Object>> intCodecs = new LinkedHashMap<>();
        intCodecs.put("int", field(ints, "CODEC"));
        intCodecs.put("int_non_negative", field(ints, "NON_NEGATIVE_CODEC"));
        intCodecs.put("int_positive", field(ints, "POSITIVE_CODEC"));
        intCodecs.put("int_0_4", bounded(ints, 0, 4));
        intCodecs.put("int_-2_8", bounded(ints, -2, 8));
        Map<String, Codec<Object>> floatCodecs = new LinkedHashMap<>();
        floatCodecs.put("float", field(floats, "CODEC"));
        floatCodecs.put("float_0_1", bounded(floats, 0.0F, 1.0F));
        floatCodecs.put("float_-1_10", bounded(floats, -1.0F, 10.0F));
        Codec<Object> level = field(LevelBasedValue.class, "CODEC");
        for (var e : intCodecs.entrySet()) decodeAll(out, e.getKey(), e.getValue(), INT_INPUTS);
        for (var e : floatCodecs.entrySet()) decodeAll(out, e.getKey(), e.getValue(), FLOAT_INPUTS);
        decodeAll(out, "level", level, LEVEL_INPUTS);
        Codec<Object> particles = field(net.minecraft.core.particles.ParticleTypes.class, "CODEC");
        Codec<Object> positions = field(net.minecraft.world.level.gameevent.PositionSource.class, "CODEC");
        decodeAll(out, "particle", particles, PARTICLE_INPUTS);
        decodeAll(out, "position", positions, POSITION_INPUTS);
        Codec<Object> components = field(net.minecraft.network.chat.ComponentSerialization.class, "CODEC");
        decodeAll(out, "component", components, COMPONENT_INPUTS);
        Codec<Object> stacks = field(net.minecraft.world.item.ItemStack.class, "CODEC");
        decodeAll(out, "stack", stacks, STACK_INPUTS);
        decodeAll(out, "single_stack", field(net.minecraft.world.item.ItemStack.class, "SINGLE_ITEM_CODEC"), STACK_INPUTS);
        Codec<Object> slotted = field(net.minecraft.world.ItemStackWithSlot.class, "CODEC");
        decodeAll(out, "slotted", slotted, SLOT_INPUTS);
        // The type registries' cases (`ParticleArgument` reads options with them).
        for (String name : List.of("dust", "block", "vibration", "effect", "dragon_breath", "ash")) {
            @SuppressWarnings("unchecked")
            Codec<Object> caseCodec = (Codec<Object>) (Object) net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE
                .getValue(net.minecraft.resources.ResourceLocation.withDefaultNamespace(name)).codec().codec();
            List<String> rows = new ArrayList<>();
            // As `ParticleArgument`: parsed by the type's case, sent with the full codec.
            decodeAll(rows, "case_" + name, caseCodec, particles, PARTICLE_INPUTS);
            out.addAll(rows);
        }

        List<Object> builtInts = List.of(ConstantInt.of(0), ConstantInt.of(-7), UniformInt.of(1, 5), UniformInt.of(5, 1), BiasedToBottomInt.of(-3, 2),
            ClampedInt.of(UniformInt.of(-10, 10), 0, 3), ClampedInt.of(ConstantInt.of(4), 6, 2),
            new WeightedListInt(WeightedList.of(List.of(new Weighted<>(ConstantInt.of(2), 4), new Weighted<>(UniformInt.of(1, 9), 1)))),
            new WeightedListInt(WeightedList.of()), ClampedNormalInt.of(1.5F, 0.25F, -2, 6), ClampedNormalInt.of(1.5F, 0.25F, 6, -2));
        for (var e : intCodecs.entrySet()) encodeAll(out, e.getKey(), e.getValue(), builtInts);
        List<Object> builtFloats = List.of(ConstantFloat.of(0.0F), ConstantFloat.of(2.5F), UniformFloat.of(0.25F, 0.75F),
            ClampedNormalFloat.of(0.0F, 1.0F, -1.0F, 1.0F), ClampedNormalFloat.of(0.0F, 1.0F, 1.0F, -1.0F), TrapezoidFloat.of(0.0F, 6.0F, 2.0F),
            TrapezoidFloat.of(0.0F, 6.0F, 9.0F), ConstantFloat.of(Float.NaN), ConstantFloat.of(1e-7F), ConstantFloat.of(3.4e38F));
        for (var e : floatCodecs.entrySet()) encodeAll(out, e.getKey(), e.getValue(), builtFloats);
        encodeAll(out, "level", level, List.of(LevelBasedValue.constant(3.0F), LevelBasedValue.perLevel(2.0F, 0.5F),
            new LevelBasedValue.Clamped(LevelBasedValue.perLevel(1.0F), 0.0F, 4.0F), new LevelBasedValue.Clamped(LevelBasedValue.constant(1.0F), 4.0F, 4.0F),
            new LevelBasedValue.Fraction(LevelBasedValue.constant(1.0F), new LevelBasedValue.LevelsSquared(2.0F)),
            LevelBasedValue.lookup(List.of(1.0F, 2.5F), LevelBasedValue.perLevel(4.0F, 1.0F))));
        var types = net.minecraft.core.particles.ParticleTypes.class;
        encodeAll(out, "particle", particles, List.of(net.minecraft.core.particles.ParticleTypes.FLAME,
            new net.minecraft.core.particles.DustParticleOptions(0xFF0000, 1.0F), net.minecraft.core.particles.DustColorTransitionOptions.SCULK_TO_REDSTONE,
            new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState()),
            new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST)),
            net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, 0x80FF0000),
            net.minecraft.core.particles.SpellParticleOption.create(net.minecraft.core.particles.ParticleTypes.EFFECT, -1, 1.0F),
            net.minecraft.core.particles.SpellParticleOption.create(net.minecraft.core.particles.ParticleTypes.INSTANT_EFFECT, 0x123456, 2.0F),
            net.minecraft.core.particles.PowerParticleOption.create(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, 1.0F),
            new net.minecraft.core.particles.VibrationParticleOption(new net.minecraft.world.level.gameevent.BlockPositionSource(new net.minecraft.core.BlockPos(1, 2, 3)), 7),
            new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)),
            new net.minecraft.core.particles.TrailParticleOption(new net.minecraft.world.phys.Vec3(1.5, 2, 3), 0x00FF00, 4),
            new net.minecraft.core.particles.ShriekParticleOption(5), new net.minecraft.core.particles.SculkChargeParticleOptions(0.25F)));
        var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD, 1);
        sword.set(net.minecraft.core.component.DataComponents.DAMAGE, 7);
        sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Edge"));
        sword.remove(net.minecraft.core.component.DataComponents.MAX_DAMAGE);
        sword.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(net.minecraft.network.chat.Component.literal("l"))));
        sword.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x123456));
        encodeAll(out, "stack", stacks, List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 3), sword,
            net.minecraft.world.item.ItemStack.EMPTY, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 200)));
        encodeAll(out, "slotted", slotted, List.of(new net.minecraft.world.ItemStackWithSlot(5, sword), new net.minecraft.world.ItemStackWithSlot(300, sword),
            new net.minecraft.world.ItemStackWithSlot(-1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 2)),
            new net.minecraft.world.ItemStackWithSlot(2, net.minecraft.world.item.ItemStack.EMPTY)));
        encodeAll(out, "component", components, List.of(net.minecraft.network.chat.Component.literal("x"), net.minecraft.network.chat.Component.empty(),
            net.minecraft.network.chat.Component.translatable("a.b", 1, "s", 2L, (byte) 3, 4.5F, true,
                net.minecraft.network.chat.Component.literal("c").withStyle(net.minecraft.ChatFormatting.RED), net.minecraft.network.chat.Component.literal("plain")),
            net.minecraft.network.chat.Component.empty().append("a").append(net.minecraft.network.chat.Component.keybind("key.jump")),
            net.minecraft.network.chat.Component.literal("x").withStyle(net.minecraft.network.chat.Style.EMPTY
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/x"))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("h")))
                .withColor(0x123456).withBold(true).withInsertion("i")),
            net.minecraft.network.chat.Component.translatableWithFallback("k", "fallback"),
            net.minecraft.network.chat.Component.literal("x").withStyle(net.minecraft.network.chat.Style.EMPTY.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenFile("p")))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /** Effect maps covering every component, effect type, requirement context and restriction. */
    static final String[] EFFECT_MAP_INPUTS = {
        """
        {"minecraft:damage_protection":[{"effect":{"type":"minecraft:add","value":{"type":"minecraft:linear","base":2,"per_level_above_first":1}},
           "requirements":{"condition":"minecraft:damage_source_properties","predicate":{"tags":[{"id":"minecraft:is_fire","expected":true}]}}}],
         "minecraft:damage_immunity":[{"effect":{},"requirements":{"condition":"minecraft:inverted","term":{"condition":"minecraft:random_chance","chance":0.5}}}],
         "minecraft:damage":[{"effect":{"type":"minecraft:all_of","effects":[{"type":"minecraft:multiply","factor":1.5},{"type":"minecraft:set","value":3},
           {"type":"minecraft:all_of","effects":[{"type":"minecraft:remove_binomial","chance":0.25}]}]}}],
         "minecraft:knockback":[{"effect":{"type":"minecraft:add","value":1}}],
         "minecraft:item_damage":[{"effect":{"type":"minecraft:remove_binomial","chance":{"type":"minecraft:fraction","numerator":1,"denominator":2}},
           "requirements":{"condition":"minecraft:match_tool","predicate":{"items":"minecraft:stick"}}}],
         "minecraft:attributes":[{"id":"minecraft:enchantment.x","attribute":"minecraft:movement_speed","amount":0.1,"operation":"add_multiplied_base"}],
         "minecraft:equipment_drops":[{"enchanted":"attacker","effect":{"type":"minecraft:add","value":0.01}}],
         "minecraft:projectile_count":[{"effect":{"type":"minecraft:add","value":2}}],
         "minecraft:crossbow_charge_time":{"type":"minecraft:add","value":-0.25},
         "minecraft:crossbow_charging_sounds":[{"start":"minecraft:item.crossbow.quick_charge_1","end":"minecraft:item.crossbow.loading_end"}],
         "minecraft:trident_sound":["minecraft:item.trident.riptide_1",{"sound_id":"minecraft:x","range":4}],
         "minecraft:prevent_equipment_drop":{},"minecraft:prevent_armor_change":{},
         "minecraft:trident_spin_attack_strength":{"type":"minecraft:add","value":0.5}}
        """,
        """
        {"minecraft:post_attack":[{"enchanted":"attacker","affected":"victim","effect":{"type":"minecraft:all_of","effects":[
           {"type":"minecraft:apply_mob_effect","to_apply":"minecraft:slowness","min_duration":1.5,"max_duration":{"type":"minecraft:linear","base":1.5,"per_level_above_first":0.5},
             "min_amplifier":3,"max_amplifier":3},
           {"type":"minecraft:damage_entity","min_damage":1,"max_damage":5,"damage_type":"minecraft:thorns"},
           {"type":"minecraft:ignite","duration":{"type":"minecraft:linear","base":4,"per_level_above_first":4}},
           {"type":"minecraft:change_item_damage","amount":2},
           {"type":"minecraft:play_sound","sound":"minecraft:item.trident.thunder","volume":5,"pitch":1},
           {"type":"minecraft:summon_entity","entity":"minecraft:lightning_bolt"},
           {"type":"minecraft:run_function","function":"minecraft:f"}]},
           "requirements":[{"condition":"minecraft:weather_check","thundering":true},{"condition":"minecraft:entity_properties","entity":"this","predicate":{"location":{"can_see_sky":true}}}]}],
         "minecraft:hit_block":[{"effect":{"type":"minecraft:explode","attribute_to_user":true,"radius":3.5,"block_interaction":"trigger","small_particle":{"type":"minecraft:gust_emitter_small"},
           "large_particle":{"type":"minecraft:gust_emitter_large"},"sound":"minecraft:entity.wind_charge.wind_burst","knockback_multiplier":{"type":"minecraft:lookup","values":[1.2,1.75,2.2],"fallback":{"type":"minecraft:linear","base":1.5,"per_level_above_first":0.35}},
           "immune_blocks":"#minecraft:blocks_wind_charge_explosions","offset":[0,0.5,0],"create_fire":false,"block_particles":[{"data":{"particle":{"type":"minecraft:poof"},"scaling":1,"speed":1},"weight":1}]},
           "requirements":{"condition":"minecraft:location_check","predicate":{"block":{"blocks":"minecraft:lightning_rod"}}}}],
         "minecraft:tick":[{"effect":{"type":"minecraft:spawn_particles","particle":{"type":"minecraft:soul"},"horizontal_position":{"type":"in_bounding_box"},
           "vertical_position":{"type":"entity_position","offset":0.1},"horizontal_velocity":{"base":{"type":"minecraft:uniform","min_inclusive":-0.2,"max_exclusive":0.2}},
           "vertical_velocity":{"movement_scale":-0.2},"speed":1},"requirements":{"condition":"minecraft:all_of","terms":[{"condition":"minecraft:enchantment_active_check","active":true}]}}],
         "minecraft:location_changed":[{"effect":{"type":"minecraft:replace_disk","block_state":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:frosted_ice","Properties":{"age":"0"}}},
           "predicate":{"type":"minecraft:all_of","predicates":[{"type":"minecraft:matching_blocks","offset":[0,1,0],"blocks":"minecraft:air"}]},"radius":{"type":"minecraft:clamped","value":{"type":"minecraft:linear","base":3,"per_level_above_first":1},"min":0,"max":16},
           "height":1,"offset":[0,-1,0],"trigger_game_event":"minecraft:block_place"},"requirements":{"condition":"minecraft:entity_properties","entity":"this","predicate":{"flags":{"is_on_ground":true}}}},
           {"effect":{"type":"minecraft:attribute","id":"minecraft:enchantment.y","attribute":"minecraft:movement_efficiency","amount":0.5,"operation":"add_value"}},
           {"effect":{"type":"minecraft:replace_block","block_state":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:fire"}},"offset":[1,2,3]}},
           {"effect":{"type":"minecraft:set_block_properties","properties":{"lit":"true"},"trigger_game_event":"minecraft:block_change"}}]}
        """,
        """
        {"minecraft:equipment_drops":[{"enchanted":"damaging_entity","effect":{"type":"minecraft:add","value":1}}],
         "minecraft:damage":[{"effect":{"type":"minecraft:add","value":1},"requirements":{"condition":"minecraft:entity_properties","entity":"block_entity_x"}}],
         "minecraft:hit_block":[{"effect":{"type":"minecraft:ignite","duration":1},"requirements":{"condition":"minecraft:damage_source_properties"}}],
         "minecraft:post_attack":[{"enchanted":"attacker","affected":"nobody","effect":{"type":"minecraft:unknown"}}],
         "minecraft:unknown_component":[],"minecraft:knockback":[{"effect":{"type":"minecraft:play_sound","sound":"minecraft:x","volume":0,"pitch":3}}]}
        """,
    };

    /** The enchantment effect codecs over registry ops, as data packs decode them. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void enchant(List<String> out) throws ReflectiveOperationException {
        var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        var loaded = net.minecraft.resources.RegistryDataLoader.load(packs, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        var access = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, loaded).compositeAccess();
        var json = access.createSerializationContext(JsonOps.INSTANCE);
        var nbt = access.createSerializationContext(NbtOps.INSTANCE);
        Codec<Object> effects = field(net.minecraft.world.item.enchantment.EnchantmentEffectComponents.class, "CODEC");
        // Every vanilla enchantment's effects, then every path of eight rich ones mutated.
        var files = new java.util.TreeMap<String, JsonElement>();
        packs.listResources("enchantment", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                files.put(id.toString(), com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("effects"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        var mutated = java.util.Set.of("thorns", "frost_walker", "wind_burst", "channeling", "fire_aspect", "efficiency", "looting", "swift_sneak");
        for (var entry : files.entrySet()) {
            String name = entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1).replace(".json", "");
            decodeOps(out, "effects/" + name, effects, entry.getValue(), mutated.contains(name), json, nbt);
        }
        for (int i = 0; i < EFFECT_MAP_INPUTS.length; i++) decodeOps(out, "effects/input" + i, effects, json(EFFECT_MAP_INPUTS[i]), true, json, nbt);
        // The registered effect types: each registry's MapCodec decodes its case's fields.
        for (var key : List.of(net.minecraft.core.registries.Registries.ENCHANTMENT_VALUE_EFFECT_TYPE, net.minecraft.core.registries.Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE,
                net.minecraft.core.registries.Registries.ENCHANTMENT_LOCATION_BASED_EFFECT_TYPE)) {
            net.minecraft.core.Registry<com.mojang.serialization.MapCodec<?>> registry = (net.minecraft.core.Registry)net.minecraft.core.registries.BuiltInRegistries.REGISTRY.getValue(key.location());
            var names = new java.util.TreeSet<String>();
            registry.keySet().forEach(id -> names.add(id.toString()));
            for (String id : names) {
                Codec<Object> codec = (Codec<Object>)registry.getValue(net.minecraft.resources.ResourceLocation.parse(id)).codec();
                for (String fields : CASE_FIELDS) decodeOps(out, "case " + key.location().getPath() + " " + id, codec, json(fields), false, json, nbt);
            }
        }
    }

    /** Field maps every case codec is given: most fail, some match a case. */
    static final String[] CASE_FIELDS = {
        "{\"value\":2}", "{\"factor\":0.5}", "{\"chance\":0.1}", "{\"effects\":[]}", "{\"amount\":1}", "{\"duration\":2}", "{\"function\":\"minecraft:f\"}",
        "{\"entity\":\"minecraft:pig\",\"join_team\":true}", "{\"min_damage\":1,\"max_damage\":2,\"damage_type\":\"minecraft:magic\"}",
        "{\"id\":\"minecraft:a\",\"attribute\":\"minecraft:armor\",\"amount\":1,\"operation\":\"add_value\"}", "{\"properties\":{}}", "{}",
    };

    /** Decodes {@code root} (and, when {@code mutate}, every path replaced) with registry ops, encoding back the same way. */
    static void decodeOps(List<String> out, String name, Codec<Object> codec, JsonElement root, boolean mutate,
            com.mojang.serialization.DynamicOps<JsonElement> json, com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> nbt) {
        List<JsonElement> cases = new ArrayList<>();
        cases.add(root);
        if (mutate) {
            var paths = new ArrayList<List<Object>>();
            paths(root, List.of(), paths);
            for (var path : paths) for (JsonElement r : REPLACEMENTS) cases.add(mutate(root, path, r));
        }
        for (JsonElement input : cases) {
            String j;
            try {
                j = outcomeOps(codec, codec.parse(json, input), json, nbt);
            } catch (Throwable e) {
                j = thrown(e);
            }
            String n;
            try {
                n = outcomeOps(codec, codec.parse(nbt, JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, input)), json, nbt);
            } catch (Throwable e) {
                n = thrown(e);
            }
            out.add("enchant " + name + " " + input + " json=" + j + " | nbt=" + n);
        }
    }

    /**
     * A component map of 8 or more entries iterates in identity-hash order
     * (`DataComponentMap.buildFromMapTrusted` uses a hash map), which differs
     * between runs; its keys are compared sorted. Smaller maps keep their order.
     */
    static JsonElement unordered(JsonElement element) {
        if (!(element instanceof JsonObject object) || object.size() < 8) {
            return element;
        }
        JsonObject sorted = new JsonObject();
        object.keySet().stream().sorted().forEach(key -> sorted.add(key, object.get(key)));
        return sorted;
    }

    static String outcomeOps(Codec<Object> codec, DataResult<?> result, com.mojang.serialization.DynamicOps<JsonElement> json,
            com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> nbt) {
        if (result.isError()) {
            // A partial's contents are compared too, through both encodings.
            return "ERR " + result.error().orElseThrow().message()
                + (result.hasResultOrPartial() ? " (partial " + encoded(codec, result.resultOrPartial().orElseThrow(), json, nbt) + ")" : "");
        }
        return "OK " + encoded(codec, result.getOrThrow(), json, nbt);
    }

    static String encoded(Codec<Object> codec, Object value, com.mojang.serialization.DynamicOps<JsonElement> json,
            com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> nbt) {
        try {
            DataResult<JsonElement> j = codec.encodeStart(json, value);
            DataResult<?> n = codec.encodeStart(nbt, value);
            return (j.isError() ? "ERR " + j.error().orElseThrow().message() : unordered(j.getOrThrow()).toString()) + " nbt="
                + (n.isError() ? "ERR " + n.error().orElseThrow().message() : n.getOrThrow().toString());
        } catch (RuntimeException e) {
            return "encode " + thrown(e);
        }
    }

    static void worldgen(List<String> out) {
        var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        var loaded = net.minecraft.resources.RegistryDataLoader.load(packs, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        var access = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, loaded).compositeAccess();
        var ops = access.createSerializationContext(JsonOps.INSTANCE);
        for (var data : net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES) {
            net.minecraft.core.Registry registry = access.lookupOrThrow(data.key());
            Codec codec = data.elementCodec();
            var holders = new ArrayList<net.minecraft.core.Holder.Reference<?>>();
            registry.listElements().forEach(h -> holders.add((net.minecraft.core.Holder.Reference<?>) h));
            holders.sort(java.util.Comparator.comparing(h -> h.key().location().toString()));
            for (var holder : holders) {
                DataResult<?> encoded = codec.encodeStart(ops, holder.value());
                out.add("worldgen " + data.key().location() + " " + holder.key().location() + " "
                    + (encoded.isError() ? "ERR " + encoded.error().orElseThrow().message() : encoded.getOrThrow()));
            }
        }
    }

    record ComponentCase(String path, String input) {}

    static final ComponentCase[] ITEM_COMPONENT_INPUTS = {
        new ComponentCase("break_sound", "\"minecraft:entity.generic.eat\""),
        new ComponentCase("repairable", "{\"items\":[\"minecraft:stone\",\"minecraft:stick\"]}"),
        new ComponentCase("repairable", "{\"items\":\"#minecraft:logs\"}"),
        new ComponentCase("enchantable", "{\"value\":12}"),
        new ComponentCase("damage_resistant", "{\"types\":\"#minecraft:is_fire\"}"),
        new ComponentCase("trim", "{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:coast\"}"),
        new ComponentCase("instrument", "\"minecraft:ponder_goat_horn\""),
        new ComponentCase("jukebox_playable", "\"minecraft:cat\""),
        new ComponentCase("provides_trim_material", "\"minecraft:iron\""),
        new ComponentCase("banner_patterns", "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"red\"},{\"pattern\":\"minecraft:cross\",\"color\":\"blue\"}]"),
        new ComponentCase("food", "{\"nutrition\":4,\"saturation\":2.4,\"can_always_eat\":true}"),
        new ComponentCase("attribute_modifiers", "[{\"type\":\"minecraft:attack_damage\",\"id\":\"test:boost\",\"amount\":1.5,\"operation\":\"add_value\",\"slot\":\"mainhand\",\"display\":{\"type\":\"override\",\"value\":{\"text\":\"bonus\"}}}]"),
        new ComponentCase("attribute_modifiers", "[{\"type\":\"minecraft:attack_damage\",\"id\":\"test:boost\",\"amount\":1.5,\"operation\":\"add_value\"}]"),
        new ComponentCase("potion_contents", "{\"potion\":\"minecraft:healing\",\"custom_color\":123,\"custom_effects\":[{\"id\":\"minecraft:speed\",\"duration\":100}],\"custom_name\":\"boost\"}"),
        new ComponentCase("potion_contents", "\"minecraft:healing\""),
        new ComponentCase("suspicious_stew_effects", "[{\"id\":\"minecraft:speed\",\"duration\":100}]"),
        new ComponentCase("written_book_content", "{\"title\":{\"raw\":\"Test\",\"filtered\":\"Safe\"},\"author\":\"Tester\",\"pages\":[\"page\",{\"raw\":{\"text\":\"raw\"},\"filtered\":{\"text\":\"safe\"}}],\"generation\":1,\"resolved\":false}"),
        new ComponentCase("bees", "[{\"entity_data\":{\"id\":\"minecraft:bee\",\"Health\":10.0},\"ticks_in_hive\":2,\"min_ticks_in_hive\":10}]"),
        new ComponentCase("enchantments", "{\"minecraft:sharpness\":2,\"minecraft:unbreaking\":1}"),
        new ComponentCase("stored_enchantments", "{\"minecraft:sharpness\":2}"),
        new ComponentCase("enchantments", "{\"sharpness\":2,\"minecraft:sharpness\":3}")
    };

    /** Registry-aware component, patch, stack, nested-container and hover cases.
     * The same observer compiles unchanged against Frozen and Current; no
     * native implementation is used to construct its expected outcomes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void itemComponents(List<String> out, boolean fullContract) throws ReflectiveOperationException {
        try (var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            var loaded = net.minecraft.resources.RegistryDataLoader.load(packs, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var access = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, loaded).compositeAccess();
            var jsonOps = access.createSerializationContext(JsonOps.INSTANCE);
            var nbtOps = access.createSerializationContext(NbtOps.INSTANCE);
            var successfulTypes = new java.util.HashSet<String>();
            int caseNumber = 0;
            for (var fixture : ITEM_COMPONENT_INPUTS) {
                Codec<Object> direct = (Codec<Object>)net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                    .getValue(net.minecraft.resources.ResourceLocation.withDefaultNamespace(fixture.path())).codecOrThrow();
                JsonElement root = json(fixture.input());
                if (direct.parse(jsonOps, root).isSuccess()) successfulTypes.add(fixture.path());
                List<JsonElement> inputs = new ArrayList<>();
                inputs.add(root);
                for (var replacement : REPLACEMENTS) if (replacement != null) inputs.add(replacement);
                List<List<Object>> paths = new ArrayList<>();
                paths(root, List.of(), paths);
                for (var path : paths) for (var replacement : REPLACEMENTS) inputs.add(mutate(root, path, replacement));
                for (var input : inputs) {
                    var patch = new JsonObject();
                    patch.add("minecraft:" + fixture.path(), input.deepCopy());
                    var stack = new JsonObject();
                    stack.addProperty("id", "minecraft:stick");
                    stack.add("components", patch);
                    var nested = json("{\"id\":\"minecraft:shulker_box\",\"components\":{\"minecraft:container\":[{\"slot\":0}]}}").getAsJsonObject();
                    nested.getAsJsonObject("components").getAsJsonArray("minecraft:container").get(0).getAsJsonObject().add("item", stack);
                    var hover = json("{\"text\":\"parent\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:stick\"}}").getAsJsonObject();
                    hover.getAsJsonObject("hover_event").add("components", patch);
                    Codec<?>[] codecs = {direct, net.minecraft.core.component.DataComponentPatch.CODEC, net.minecraft.world.item.ItemStack.CODEC,
                        net.minecraft.world.item.ItemStack.SINGLE_ITEM_CODEC, net.minecraft.world.item.ItemStack.CODEC,
                        field(net.minecraft.network.chat.ComponentSerialization.class, "CODEC")};
                    JsonElement[] forms = {input, patch, stack, stack, nested, hover};
                    for (int route = 0; route < forms.length; route++) {
                        Codec<Object> codec = (Codec<Object>)codecs[route];
                        String j, n;
                        try { j = fullContract ? componentDecodeOutcome(codec, jsonOps, forms[route], jsonOps, nbtOps)
                            : componentOutcome(codec, codec.parse(jsonOps, forms[route]), jsonOps, nbtOps); }
                        catch (Throwable e) { j = thrown(e); }
                        try {
                            var tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, forms[route]);
                            n = fullContract ? componentDecodeOutcome(codec, nbtOps, tag, jsonOps, nbtOps)
                                : componentOutcome(codec, codec.parse(nbtOps, tag), jsonOps, nbtOps);
                        }
                        catch (Throwable e) { n = thrown(e); }
                        out.add("item_component " + fixture.path() + " " + caseNumber + " " + route + " json=" + j + " | nbt=" + n);
                    }
                    caseNumber++;
                }
            }
            var expected = new java.util.HashSet<String>();
            for (var fixture : ITEM_COMPONENT_INPUTS) expected.add(fixture.path());
            if (!successfulTypes.equals(expected)) {
                expected.removeAll(successfulTypes);
                throw new IllegalStateException("No successful direct fixture for " + expected);
            }
            out.add("component_coverage types=" + successfulTypes.size() + " mutated_inputs=" + caseNumber + " routes=6");
        }
    }

    /** Observe the full public Codec contract, including metadata parse() drops. */
    static <T> String componentDecodeOutcome(Codec<Object> codec, com.mojang.serialization.DynamicOps<T> ops, T input,
            net.minecraft.resources.RegistryOps<JsonElement> jsonOps, net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> nbtOps) {
        var decoded = codec.decode(ops, input);
        String value = componentOutcome(codec, decoded.map(com.mojang.datafixers.util.Pair::getFirst), jsonOps, nbtOps);
        String remainder = decoded.resultOrPartial().map(pair -> String.valueOf(pair.getSecond())
            + " same_input=" + (pair.getSecond() == input)).orElse("absent");
        String encoded = decoded.resultOrPartial().map(pair -> {
            var result = codec.encode(pair.getFirst(), ops, ops.empty());
            return " lifecycle=" + result.lifecycle() + " error=" + result.error().map(DataResult.Error::message)
                + " result=" + result.resultOrPartial();
        }).orElse("absent");
        return value + " decode_lifecycle=" + decoded.lifecycle() + " remainder=" + remainder + " encode=" + encoded;
    }

    static String componentOutcome(Codec<Object> codec, DataResult<Object> result,
            net.minecraft.resources.RegistryOps<JsonElement> jsonOps, net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> nbtOps) {
        String message = result.error().map(e -> "ERR " + e.message()).orElse("OK");
        if (!result.hasResultOrPartial()) return message;
        Object value = result.resultOrPartial().orElseThrow();
        try {
            var j = codec.encodeStart(jsonOps, value);
            var n = codec.encodeStart(nbtOps, value);
            return message + (result.isError() ? " partial=" : " ")
                + (j.isError() ? "ERR " + j.error().orElseThrow().message() : j.getOrThrow().toString())
                + " nbt=" + (n.isError() ? "ERR " + n.error().orElseThrow().message() : n.getOrThrow().toString());
        } catch (Throwable e) { return message + " encode=" + thrown(e); }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var sections = new LinkedHashMap<String, List<String>>();
        sections.put("values", new ArrayList<>());
        sections.put("worldgen", new ArrayList<>());
        sections.put("enchant", new ArrayList<>());
        sections.put("item_components", new ArrayList<>());
        sections.put("item_component_contract", new ArrayList<>());
        var selected = args.length > 1 ? List.of(args).subList(1, args.length) : List.copyOf(sections.keySet());
        if (selected.contains("values")) values(sections.get("values"));
        if (selected.contains("worldgen")) worldgen(sections.get("worldgen"));
        if (selected.contains("enchant")) enchant(sections.get("enchant"));
        if (selected.contains("item_components")) itemComponents(sections.get("item_components"), false);
        if (selected.contains("item_component_contract")) itemComponents(sections.get("item_component_contract"), true);
        var text = new StringBuilder();
        var summary = new StringBuilder("VALUE_FUZZ_REFERENCE");
        for (String section : selected) {
            var rows = sections.get(section);
            for (String row : rows) text.append(row).append('\n');
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", rows).getBytes(StandardCharsets.UTF_8)));
            // Live successes are reported apart from (matching) failures.
            long ok = rows.stream().mapToLong(r -> r.split("=OK ", -1).length - 1L).sum();
            long err = rows.stream().mapToLong(r -> r.split("=ERR ", -1).length - 1L).sum();
            long thrown = rows.stream().mapToLong(r -> r.split("=THROW ", -1).length - 1L).sum();
            summary.append(' ').append(section).append('=').append(rows.size()).append(':').append(digest, 0, 16)
                .append(" ok=").append(ok).append(" err=").append(err).append(" throw=").append(thrown);
        }
        if (args.length > 0) Files.writeString(Path.of(args[0]), text);
        System.out.println(summary);
    }
}
