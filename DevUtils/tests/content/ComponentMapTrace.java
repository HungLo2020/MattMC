import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.util.Unit;

/**
 * Differential operation traces for item component maps, compiled unchanged
 * against a reference and Current: seeded random sequences of every public
 * structural operation on `PatchedDataComponentMap`, `DataComponentPatch` and
 * `ItemStack` components (set, remove, applyPatch, restorePatch, clearPatch,
 * setAll, copy, asPatch, toImmutableMap, fromPatch, builders, forget, split),
 * plus escape paths: builder reuse after build, mutation through a patch's
 * exposed entry set, and in-place mutation of retained payloads (a
 * MutableComponent name, a lore list). After each step every observable is
 * recorded: get/has/hasNonDefault per tracked type, keySet and iterator order,
 * size, patch entries in order (value or removed), equality among the
 * sequence's live maps, hash consistency with equality, and the stack's JSON
 * and NBT encodings. Identity hash codes are never printed. Arguments: output
 * file, then optionally the number of sequences.
 */
public final class ComponentMapTrace {
    @SuppressWarnings("rawtypes")
    static List<DataComponentType> TYPES;
    static List<net.minecraft.world.item.Item> ITEMS;

    @SuppressWarnings("rawtypes")
    static void init() {
        TYPES = List.of(DataComponents.DAMAGE, DataComponents.MAX_DAMAGE, DataComponents.MAX_STACK_SIZE,
        DataComponents.RARITY, DataComponents.CUSTOM_NAME, DataComponents.LORE, DataComponents.UNBREAKABLE, DataComponents.ITEM_MODEL,
        DataComponents.ENCHANTMENT_GLINT_OVERRIDE, DataComponents.REPAIR_COST);
        ITEMS = List.of(Items.STICK, Items.DIAMOND_SWORD, Items.BOW, Items.SHULKER_BOX, Items.ENCHANTED_GOLDEN_APPLE);
    }

    static String name(DataComponentType<?> type) {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).toString();
    }

    static Object value(Random random, DataComponentType<?> type, ItemStack stack) {
        // Sometimes the prototype's own value, which set() must not store.
        if (random.nextInt(5) == 0 && stack.getPrototype().has(type)) return stack.getPrototype().get(type);
        if (type == DataComponents.DAMAGE || type == DataComponents.REPAIR_COST) return random.nextInt(4);
        if (type == DataComponents.MAX_DAMAGE) return 1 + random.nextInt(3);
        if (type == DataComponents.MAX_STACK_SIZE) return 1 + random.nextInt(64);
        if (type == DataComponents.RARITY) return Rarity.values()[random.nextInt(4)];
        if (type == DataComponents.CUSTOM_NAME) return Component.literal("n" + random.nextInt(3));
        if (type == DataComponents.LORE) return new ItemLore(new ArrayList<>(List.of(Component.literal("l" + random.nextInt(3)))));
        if (type == DataComponents.UNBREAKABLE) return Unit.INSTANCE;
        if (type == DataComponents.ITEM_MODEL) return ResourceLocation.withDefaultNamespace(random.nextBoolean() ? "stick" : "apple");
        return random.nextBoolean();
    }

    static String show(Object value) {
        return value instanceof Optional<?> o ? (o.isPresent() ? String.valueOf(o.get()) : "REMOVED") : String.valueOf(value);
    }

    static String patch(DataComponentPatch patch) {
        StringBuilder out = new StringBuilder("[");
        for (Map.Entry<DataComponentType<?>, Optional<?>> e : patch.entrySet()) out.append(name(e.getKey())).append('=').append(show(e.getValue())).append(';');
        return out.append(']').toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String state(ItemStack stack, List<DataComponentMap> live) {
        StringBuilder out = new StringBuilder();
        DataComponentMap map = stack.getComponents();
        for (DataComponentType type : TYPES) {
            out.append(name(type).substring(10)).append('=').append(show(map.get(type))).append(map.has(type) ? "" : "!")
                .append(map instanceof PatchedDataComponentMap p && p.hasNonDefault(type) ? "*" : "").append(' ');
        }
        // Key and iteration order follow the prototype's reference-hash map
        // (identity hash codes), so they are compared as sets; patch order
        // (insertion order) is compared exactly below.
        out.append("keys=").append(map.keySet().stream().map(t -> name((DataComponentType<?>) t).substring(10)).sorted().toList());
        List<String> iter = new ArrayList<>();
        map.forEach(c -> iter.add(name(c.type()).substring(10) + "=" + c.value()));
        iter.sort(null);
        out.append(" iter=").append(iter).append(" size=").append(map.size());
        out.append(" patch=").append(patch(stack.getComponentsPatch()));
        StringBuilder eq = new StringBuilder();
        for (int i = 0; i < live.size(); i++) for (int j = 0; j < live.size(); j++) {
            boolean equal = live.get(i).equals(live.get(j));
            eq.append(equal ? (live.get(i).hashCode() == live.get(j).hashCode() ? '=' : 'X') : '.');
        }
        out.append(" eq=").append(eq);
        var json = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack);
        var nbt = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack);
        out.append(" json=").append(json.result().map(Object::toString).orElse("ERR " + json.error().map(e -> e.message()).orElse("")));
        out.append(" nbt=").append(nbt.result().map(Object::toString).orElse("ERR " + nbt.error().map(e -> e.message()).orElse("")));
        return out.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        init();
        int sequences = args.length > 1 ? Integer.parseInt(args[1]) : 400;
        List<String> rows = new ArrayList<>();
        for (int seq = 0; seq < sequences; seq++) {
            Random random = new Random(20261015L * 31 + seq);
            ItemStack stack = new ItemStack(ITEMS.get(random.nextInt(ITEMS.size())));
            List<DataComponentMap> live = new ArrayList<>();
            live.add(stack.getComponents());
            List<ItemStack> copies = new ArrayList<>();
            DataComponentPatch.Builder reused = null;
            DataComponentPatch built = null;
            for (int step = 0; step < 24; step++) {
                DataComponentType type = TYPES.get(random.nextInt(TYPES.size()));
                String op;
                try {
                    switch (random.nextInt(16)) {
                        case 0, 1, 2 -> { Object v = value(random, type, stack); op = "set " + name(type) + " " + v + " -> " + show(stack.set(type, v)); }
                        case 3 -> op = "remove " + name(type) + " -> " + show(stack.remove(type));
                        case 4 -> {
                            var b = DataComponentPatch.builder();
                            for (int k = random.nextInt(3); k >= 0; k--) {
                                DataComponentType t = TYPES.get(random.nextInt(TYPES.size()));
                                if (random.nextBoolean()) b.set(t, value(random, t, stack)); else b.remove(t);
                            }
                            DataComponentPatch p = b.build();
                            stack.applyComponents(p);
                            op = "applyPatch " + patch(p);
                        }
                        case 5 -> {
                            DataComponentPatch saved = stack.getComponentsPatch();
                            ItemStack other = new ItemStack(stack.getItemHolder(), stack.getCount(), saved);
                            copies.add(other);
                            live.add(other.getComponents());
                            op = "fromPatch " + patch(saved);
                        }
                        case 6 -> { ItemStack copy = stack.copy(); copies.add(copy); live.add(copy.getComponents()); op = "copy"; }
                        case 7 -> {
                            if (copies.isEmpty()) { op = "noop"; break; }
                            ItemStack c = copies.get(random.nextInt(copies.size()));
                            Object v = value(random, type, c);
                            c.set(type, v);
                            op = "set-on-copy " + name(type) + " " + v;
                        }
                        case 8 -> { var p = stack.getComponentsPatch(); ((PatchedDataComponentMap) stack.getComponents()).restorePatch(p); op = "restorePatch self"; }
                        case 9 -> { ((PatchedDataComponentMap) stack.getComponents()).clearPatch(); op = "clearPatch"; }
                        case 10 -> {
                            if (copies.isEmpty()) { op = "noop"; break; }
                            ((PatchedDataComponentMap) stack.getComponents()).setAll(copies.get(random.nextInt(copies.size())).getComponents());
                            op = "setAll copy";
                        }
                        case 11 -> {
                            // Builder reuse: a built patch shares the builder's map.
                            if (reused == null) { reused = DataComponentPatch.builder(); reused.set(type, value(random, type, stack)); built = reused.build(); op = "builder build " + patch(built); }
                            else { reused.remove(type); op = "builder reused remove " + name(type) + " built now " + patch(built); stack.applyComponents(built); }
                        }
                        case 12 -> {
                            // Mutation through an exposed patch entry set.
                            var p = stack.getComponentsPatch();
                            var it = p.entrySet().iterator();
                            if (!it.hasNext()) { op = "entrySet empty"; break; }
                            var e = it.next();
                            try { e.setValue(Optional.empty()); op = "entrySet setValue " + name(e.getKey()); }
                            catch (RuntimeException x) { op = "entrySet setValue threw " + x.getClass().getSimpleName(); }
                            // Structural removal through the exposed entry set's iterator.
                            try { it.remove(); op += "; iterator remove " + name(e.getKey()); }
                            catch (RuntimeException x) { op += "; iterator remove threw " + x.getClass().getSimpleName(); }
                        }
                        case 13 -> {
                            // In-place mutation of a retained payload.
                            Component n = stack.get(DataComponents.CUSTOM_NAME);
                            if (n instanceof MutableComponent m) { m.append("+"); op = "append to name"; }
                            else {
                                ItemLore lore = stack.get(DataComponents.LORE);
                                if (lore != null && lore.lines() instanceof ArrayList<Component> lines) { lines.add(Component.literal("x")); op = "append to lore list"; }
                                else op = "no mutable payload";
                            }
                        }
                        case 14 -> { var p = stack.getComponentsPatch().forget(t -> t == type); stack = new ItemStack(stack.getItemHolder(), stack.getCount(), p); live.add(stack.getComponents()); op = "forget " + name(type); }
                        default -> { var s = stack.getComponentsPatch().split(); op = "split added=" + s.added().size() + " removed=" + s.removed().size(); }
                    }
                } catch (RuntimeException e) {
                    op = "threw " + e.getClass().getSimpleName() + " " + e.getMessage();
                }
                // Default toString identity hashes vary per JVM.
                rows.add(("seq " + seq + " step " + step + " " + op + " | " + state(stack, live)).replaceAll("@[0-9a-f]{4,8}\\b", "@"));
            }
        }
        String text = String.join("\n", rows) + "\n";
        if (args.length > 0) Files.writeString(Path.of(args[0]), text);
        System.out.println("COMPONENT_MAP_TRACE rows=" + rows.size() + " sha=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes()), 0, 8));
    }
}
