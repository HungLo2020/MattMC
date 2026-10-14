import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EntityType;

/**
 * Dumps content definitions by section, compiled unchanged against Frozen and
 * Current: data component types, entity data serializers, every entity
 * class's synchronized data fields, items, every element of the data-driven
 * entity, item and biome registries loaded from the vanilla data pack, and
 * every item's stacks in save and network form. Each line starts with its section's row kind.
 * Optional arguments: an output file, then section names to include.
 */
public final class ContentReference {
    private ContentReference() {}

    static Object read(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static void componentTypes(List<String> out) throws ReflectiveOperationException {
        for (var type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            int id = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(type);
            var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            boolean persistent = !type.isTransient();
            boolean cached = persistent && type.codec().getClass().getName().contains("EncoderCache");
            out.add("component_type " + id + " " + key + " persistent=" + persistent + " cached=" + cached
                + " stream=" + (type.streamCodec() != null));
        }
    }

    static Map<EntityDataSerializer<?>, String> serializerNames() throws ReflectiveOperationException {
        var names = new java.util.IdentityHashMap<EntityDataSerializer<?>, String>();
        for (Field field : EntityDataSerializers.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && EntityDataSerializer.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                names.put((EntityDataSerializer<?>) field.get(null), field.getName().toLowerCase());
            }
        }
        return names;
    }

    static void serializers(List<String> out) throws ReflectiveOperationException {
        var names = serializerNames();
        for (int id = 0; ; id++) {
            var serializer = EntityDataSerializers.getSerializer(id);
            if (serializer == null) break;
            out.add("serializer " + id + " " + names.get(serializer));
        }
    }

    /** Every class of a registered entity type, and its superclasses, with their accessor fields. */
    static void synchedData(List<String> out) throws ReflectiveOperationException {
        var names = serializerNames();
        var classes = new java.util.TreeSet<String>();
        var byName = new java.util.HashMap<String, Class<?>>();
        for (Field field : EntityType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityType.class) continue;
            if (!(field.getGenericType() instanceof ParameterizedType generic)) continue;
            if (!(generic.getActualTypeArguments()[0] instanceof Class<?> entity)) continue;
            for (Class<?> type = entity; type != null && type != Object.class; type = type.getSuperclass()) {
                classes.add(type.getName()); byName.put(type.getName(), type);
            }
        }
        for (String name : classes) {
            Class<?> type = byName.get(name);
            var fields = new TreeMap<Integer, String>();
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityDataAccessor.class) continue;
                field.setAccessible(true);
                var accessor = (EntityDataAccessor<?>) field.get(null);
                String previous = fields.put(accessor.id(), field.getName() + " " + names.get(accessor.serializer()));
                if (previous != null) throw new IllegalStateException("Duplicate synched data id in " + name);
            }
            if (fields.isEmpty()) continue;
            // The nearest superclass that defines fields: IDs continue after its last.
            String base = "none";
            for (Class<?> parent = type.getSuperclass(); parent != null && parent != Object.class; parent = parent.getSuperclass()) {
                if (definesAccessors(parent)) { base = parent.getName(); break; }
            }
            for (var entry : fields.entrySet())
                out.add("synched " + name + " base=" + base + " " + entry.getKey() + " " + entry.getValue());
        }
    }

    static boolean definesAccessors(Class<?> type) {
        for (Field field : type.getDeclaredFields())
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == EntityDataAccessor.class) return true;
        return false;
    }

    /** Persistent encoding of a component value (SNBT), and whether it decodes back to an equal value. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String encoded(net.minecraft.core.component.DataComponentType type, Object value,
                          net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops) {
        if (type.isTransient()) return "transient " + value;
        var tag = ((com.mojang.serialization.Codec) type.codecOrThrow()).encodeStart(ops, value).getOrThrow();
        var back = ((com.mojang.serialization.Codec) type.codecOrThrow()).parse(ops, tag);
        String mark = back.isError() ? "UNDECODABLE " : back.getOrThrow().equals(value) ? "" : "UNEQUAL ";
        return mark + tag;
    }

    /** Every item: identity, description, remainder, features, block and default components; then block aliases. */
    static void items(List<String> out) throws ReflectiveOperationException {
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,
            net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        for (var item : BuiltInRegistries.ITEM) {
            int id = BuiltInRegistries.ITEM.getId(item);
            var remainder = item.getCraftingRemainder();
            String block = item instanceof net.minecraft.world.item.BlockItem blockItem
                ? BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString() : "none";
            out.add("item " + id + " " + BuiltInRegistries.ITEM.getKey(item) + " class=" + item.getClass().getName()
                + " desc=" + item.getDescriptionId() + " remainder=" + (remainder.isEmpty() ? "none" : BuiltInRegistries.ITEM.getKey(remainder.getItem()) + "x" + remainder.getCount())
                + " features=" + new java.util.TreeSet<>(net.minecraft.world.flag.FeatureFlags.REGISTRY.toNames(item.requiredFeatures())) + " block=" + block);
            var components = new TreeMap<Integer, String>();
            for (var typed : item.components()) {
                var type = typed.type();
                components.put(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(type),
                    BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) + " " + encoded(type, typed.value(), ops));
            }
            for (var component : components.values()) out.add("item_component " + id + " " + component);
        }
        var aliases = new TreeMap<Integer, String>();
        for (var entry : net.minecraft.world.item.Item.BY_BLOCK.entrySet())
            aliases.put(BuiltInRegistries.BLOCK.getId(entry.getKey()), BuiltInRegistries.BLOCK.getKey(entry.getKey()) + " " + BuiltInRegistries.ITEM.getKey(entry.getValue()));
        for (var alias : aliases.values()) out.add("block_item " + alias);
    }

    /** Worldgen registries as a server loads them from the vanilla data pack. */
    static net.minecraft.core.RegistryAccess.Frozen worldgen() {
        var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        var worldgen = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        return layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, worldgen).compositeAccess();
    }

    static String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0, 8);
    }

    /**
     * Each item's default stack and a modified one (name, repair cost, damage,
     * a removed default component): save encoding and network bytes, and
     * whether both decode back to a matching stack.
     */
    static void itemStacks(List<String> out, net.minecraft.core.RegistryAccess access) throws Exception {
        var nbt = access.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        for (var item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            var plain = new net.minecraft.world.item.ItemStack(item, 1);
            var edited = plain.copyWithCount(Math.min(2, plain.getMaxStackSize()));
            edited.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("x"));
            edited.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 3);
            if (edited.isDamageableItem()) edited.setDamageValue(5);
            edited.remove(net.minecraft.core.component.DataComponents.RARITY);
            for (var stack : List.of(plain, edited)) {
                var save = net.minecraft.world.item.ItemStack.CODEC.encodeStart(nbt, stack).getOrThrow();
                var loaded = net.minecraft.world.item.ItemStack.CODEC.parse(nbt, save).getOrThrow();
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), access);
                net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.getBytes(0, bytes);
                var received = net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                boolean roundTrip = net.minecraft.world.item.ItemStack.matches(stack, loaded) && net.minecraft.world.item.ItemStack.matches(stack, received);
                out.add("item_stack " + BuiltInRegistries.ITEM.getKey(item) + (stack == plain ? " plain" : " edited") + " save=" + digest(save.toString())
                    + " net=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 8) + " round_trip=" + roundTrip);
            }
        }
    }

    /** The data-driven entity, item and biome registries Rust decodes (`content/data`). */
    static final List<String> DATA_REGISTRIES = List.of("worldgen/biome", "painting_variant", "damage_type", "banner_pattern", "jukebox_song",
        "trim_pattern", "trim_material", "instrument", "enchantment", "enchantment_provider", "cat_variant", "frog_variant", "wolf_variant",
        "wolf_sound_variant", "pig_variant", "cow_variant", "chicken_variant", "zombie_nautilus_variant");

    /** A registry's direct and network codecs from the loader's tables (network null when not synchronized). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static com.mojang.serialization.Codec[] codecs(String registry) {
        com.mojang.serialization.Codec direct = null, network = null;
        for (var data : net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES)
            if (data.key().location().getPath().equals(registry)) direct = data.elementCodec();
        for (var data : net.minecraft.resources.RegistryDataLoader.SYNCHRONIZED_REGISTRIES)
            if (data.key().location().getPath().equals(registry)) network = data.elementCodec();
        return new com.mojang.serialization.Codec[]{direct, network};
    }

    static String encoded(com.mojang.serialization.DataResult<?> result) {
        return result.isSuccess() ? String.valueOf(result.getOrThrow()) : "ERROR";
    }

    /** Every loaded element: registry ID, tags, save (direct) and network encodings. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void dataRegistries(List<String> out, net.minecraft.core.RegistryAccess registries) {
        var json = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var nbt = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        for (String name : DATA_REGISTRIES) {
            var codecs = codecs(name);
            net.minecraft.core.Registry registry = registries.lookupOrThrow(net.minecraft.resources.ResourceKey.createRegistryKey(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace(name)));
            var holders = new ArrayList<net.minecraft.core.Holder.Reference<?>>();
            registry.listElements().forEach(h -> holders.add((net.minecraft.core.Holder.Reference<?>) h));
            holders.sort(java.util.Comparator.comparing(h -> h.key().location().toString()));
            for (var holder : holders) {
                Object value = holder.value();
                String direct = encoded(codecs[0].encodeStart(json, value));
                String network = codecs[1] == null ? "-" : encoded(codecs[1].encodeStart(nbt, value));
                out.add("data " + name + " " + holder.key().location() + " id=" + registry.getId(value)
                    + " tags=" + holder.tags().map(t -> t.location().toString()).sorted().toList() + " direct=" + direct + " network=" + network);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var sections = new LinkedHashMap<String, List<String>>();
        for (String section : new String[]{"component_types", "serializers", "synched", "items", "data_registries", "item_stacks"}) sections.put(section, new ArrayList<>());
        componentTypes(sections.get("component_types"));
        serializers(sections.get("serializers"));
        synchedData(sections.get("synched"));
        items(sections.get("items"));
        var worldgen = worldgen();
        dataRegistries(sections.get("data_registries"), worldgen);
        itemStacks(sections.get("item_stacks"), worldgen);
        var selected = args.length > 1 ? List.of(args).subList(1, args.length) : List.copyOf(sections.keySet());
        var text = new StringBuilder();
        var summary = new StringBuilder("CONTENT_REFERENCE");
        for (String section : selected) {
            var rows = sections.get(section);
            if (rows == null) throw new IllegalArgumentException("Unknown section " + section);
            for (String row : rows) text.append(row).append('\n');
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", rows).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            summary.append(' ').append(section).append('=').append(rows.size()).append(':').append(digest, 0, 16);
        }
        if (args.length > 0) Files.writeString(Path.of(args[0]), text);
        System.out.println(summary);
    }
}
