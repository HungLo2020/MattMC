import com.mojang.serialization.Codec;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.*;

/** Unchanged Frozen/pre/current behavioral observer. No native API in the oracle.
 * Arguments: output file. Records aliases and callback traces without object hashes.
 */
public final class ItemStateContractReference {
    static final List<String> rows = new ArrayList<>();
    static HolderLookup.Provider registries;
    static Method getItems, setItems;
    static final DataComponentType<Probe> KEY = DataComponentType.<Probe>builder().persistent(Codec.STRING.xmap(Probe::new, p -> p.name)).build();
    static final class Probe {
        final String name;
        Runnable onEqual = () -> {};
        Runnable onHash = () -> {};
        Probe(String name) { this.name = name; }
        @Override public boolean equals(Object other) { onEqual.run(); return other instanceof Probe p && name.equals(p.name); }
        @Override public int hashCode() { onHash.run(); return name.hashCode(); }
        @Override public String toString() { return name; }
    }
    static String error(Throwable failure) {
        if (failure instanceof java.lang.reflect.InvocationTargetException invocation) failure = invocation.getCause();
        return failure.getClass().getName() + ":" + failure.getMessage();
    }
    static String count(ItemStack stack) { return stack.getCount() + "/empty=" + stack.isEmpty(); }
    static String patch(DataComponentPatch patch) {
        List<String> result = new ArrayList<>();
        for (var e : patch.entrySet()) result.add((e.getKey() == KEY ? "foreign" : BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(e.getKey()))
            + "=" + e.getValue().map(Object::toString).orElse("REMOVED"));
        return result.toString();
    }
    @SuppressWarnings("unchecked") static NonNullList<ItemStack> slots(ChestBlockEntity chest) throws Exception { return (NonNullList<ItemStack>)getItems.invoke(chest); }
    static ChestBlockEntity chest() { return new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
    static String slotState(ChestBlockEntity chest) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty()) result.add(i + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount());
        }
        return result.toString();
    }
    static void counts() {
        for (int n : new int[]{Integer.MIN_VALUE, -1, 0, 1, 64, Integer.MAX_VALUE}) {
            ItemStack stack = new ItemStack(Items.STICK, n);
            ItemStack copy = stack.copy();
            String before = count(stack); stack.grow(1);
            rows.add("count " + n + " before=" + before + " grown=" + count(stack) + " copy=" + count(copy) + " sameEmpty=" + (copy == ItemStack.EMPTY));
        }
        for (int amount : new int[]{Integer.MIN_VALUE, -1, 0, 1, 8, Integer.MAX_VALUE}) {
            ItemStack stack = new ItemStack(Items.STICK, 5);
            ItemStack taken = stack.split(amount);
            rows.add("split " + amount + " original=" + count(stack) + " taken=" + count(taken) + " same=" + (taken == stack));
        }
        ItemStack air = new ItemStack(Items.AIR, 40);
        rows.add("air=" + count(air) + " copyEmpty=" + (air.copy() == ItemStack.EMPTY));
        int original = ItemStack.EMPTY.getCount(); ItemStack.EMPTY.setCount(27);
        rows.add("EMPTY mutated=" + count(ItemStack.EMPTY) + " copySame=" + (ItemStack.EMPTY.copy() == ItemStack.EMPTY));
        ItemStack.EMPTY.setCount(original);
        int[] lookups = {0};
        ItemStack unusual = new ItemStack((net.minecraft.world.level.ItemLike)() -> ++lookups[0] == 1 ? Items.STICK : Items.DIAMOND_SWORD, 1);
        rows.add("itemLike calls=" + lookups[0] + " item=" + BuiltInRegistries.ITEM.getKey(unusual.getItem())
            + " componentMaxDamage=" + unusual.getMaxDamage() + " nominalPrototypeMaxDamage=" + unusual.getPrototype().get(DataComponents.MAX_DAMAGE));
    }
    static void maps() {
        DataComponentMap prototype = DataComponentMap.builder().set(KEY, new Probe("base")).build();
        for (String mode : List.of("patch", "copy", "throw", "nested")) {
            PatchedDataComponentMap map = new PatchedDataComponentMap(prototype);
            map.set(DataComponents.REPAIR_COST, 7);
            DataComponentPatch[] escaped = {DataComponentPatch.EMPTY};
            PatchedDataComponentMap[] copied = {null}; List<String> calls = new ArrayList<>();
            RuntimeException thrown = new IllegalStateException("original equals failure");
            Probe value = new Probe("changed");
            value.onEqual = () -> {
                calls.add("equals");
                if (!mode.equals("copy")) escaped[0] = map.asPatch();
                if (!mode.equals("patch")) copied[0] = map.copy();
                if (mode.equals("nested")) map.set(DataComponents.DAMAGE, 2);
                if (mode.equals("throw")) throw thrown;
            };
            String outcome;
            try { outcome = "returned=" + map.set(KEY, value); }
            catch (Throwable e) { outcome = error(e) + " same=" + (e == thrown); }
            value.onEqual = () -> {};
            rows.add("equals " + mode + " " + outcome + " calls=" + calls + " map=" + patch(map.asPatch())
                + " escaped=" + patch(escaped[0]) + " copied=" + (copied[0] == null ? "none" : patch(copied[0].asPatch())));
            map.set(DataComponents.REPAIR_COST, 8);
            rows.add("equals detach " + mode + " map=" + patch(map.asPatch()) + " escaped=" + patch(escaped[0])
                + " copied=" + (copied[0] == null ? "none" : patch(copied[0].asPatch())));
        }
        var builder = DataComponentPatch.builder().set(DataComponents.REPAIR_COST, 2);
        var built = builder.build(); builder.set(DataComponents.DAMAGE, 3);
        var map = PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, built); var view = map.asPatch(); var copy = map.copy();
        var it = view.entrySet().iterator(); it.next(); it.remove();
        rows.add("escaped builder=" + patch(built) + " map=" + patch(map.asPatch()) + " copy=" + patch(copy.asPatch()));
        var proto = DataComponentMap.builder().set(DataComponents.REPAIR_COST, 3).set(DataComponents.DAMAGE, 4).build();
        var over = new PatchedDataComponentMap(proto); var snapshot = over.toImmutableMap(); proto.keySet().remove(DataComponents.DAMAGE);
        rows.add("prototype escape currentDamage=" + over.get(DataComponents.DAMAGE) + " snapshotDamage=" + snapshot.get(DataComponents.DAMAGE));
        var tombstone = new PatchedDataComponentMap(prototype);
        rows.add("tombstone removed=" + tombstone.remove(KEY) + " visible=" + tombstone.get(KEY));
        rows.add("tombstone set returned=" + tombstone.set(KEY, new Probe("replacement")) + " visible=" + tombstone.get(KEY));
        PatchedDataComponentMap[] holder = {null}; DataComponentPatch[] getterView = {DataComponentPatch.EMPTY};
        List<String> events = new ArrayList<>(); boolean[] armed = {false}; Probe base = new Probe("dynamic base");
        DataComponentMap dynamic = new DataComponentMap() {
            @SuppressWarnings("unchecked") public <T> T get(DataComponentType<? extends T> key) {
                if (key != KEY) return null;
                events.add("get"); if (armed[0]) getterView[0] = holder[0].asPatch(); return (T)base;
            }
            public Set<DataComponentType<?>> keySet() { return Set.of(KEY); }
        };
        holder[0] = new PatchedDataComponentMap(dynamic); holder[0].set(DataComponents.REPAIR_COST, 2); armed[0] = true;
        Probe replacement = new Probe("dynamic replacement"); replacement.onEqual = () -> events.add("equals");
        Probe previous = holder[0].set(KEY, replacement); replacement.onEqual = () -> {};
        rows.add("prototype callback previous=" + previous + " events=" + events + " escaped=" + patch(getterView[0]) + " map=" + patch(holder[0].asPatch()));
    }
    static void patchWrapperIdentity() {
        var builder = DataComponentPatch.builder().set(DataComponents.REPAIR_COST, 2);
        var built = builder.build(); var wrapper = built.get(DataComponents.REPAIR_COST);
        var map = PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, built);
        var escaped = map.asPatch(); var copy = map.copy(); var forgotten = built.forget(type -> false);
        rows.add("patch wrapper stable=" + (wrapper == built.get(DataComponents.REPAIR_COST))
            + " escaped=" + (wrapper == escaped.get(DataComponents.REPAIR_COST))
            + " copied=" + (wrapper == copy.asPatch().get(DataComponents.REPAIR_COST))
            + " forgotten=" + (wrapper == forgotten.get(DataComponents.REPAIR_COST)));
        map.set(DataComponents.REPAIR_COST, 3);
        rows.add("patch wrapper detachedOld=" + (wrapper == built.get(DataComponents.REPAIR_COST))
            + " newStable=" + (map.asPatch().get(DataComponents.REPAIR_COST) == map.asPatch().get(DataComponents.REPAIR_COST)));
    }
    static void malformedPatchCounts() {
        for (int[] counts : new int[][]{{-1, 0}, {0, -1}, {Integer.MAX_VALUE, Integer.MAX_VALUE}, {Integer.MIN_VALUE, 0}}) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), (RegistryAccess)registries);
            try {
                buffer.writeVarInt(counts[0]); buffer.writeVarInt(counts[1]);
                try { DataComponentPatch.STREAM_CODEC.decode(buffer); rows.add("wire counts " + Arrays.toString(counts) + " accepted"); }
                catch (Throwable e) { rows.add("wire counts " + Arrays.toString(counts) + " " + error(e) + " read=" + buffer.readerIndex()); }
            } finally { buffer.release(); }
        }
    }
    static void chests() throws Exception {
        ChestBlockEntity first = chest(), second = chest();
        ItemStack shared = new ItemStack(Items.STICK, 12);
        MutableComponent name = Component.literal("before"); shared.set(DataComponents.CUSTOM_NAME, name);
        first.setItem(0, shared); first.setItem(1, shared); ItemStack copy = shared.copy();
        shared.shrink(2); name.append("+");
        rows.add("chest alias same=" + (first.getItem(0) == first.getItem(1)) + " retained=" + (first.getItem(0) == shared)
            + " slots=" + slotState(first) + " copyCount=" + copy.getCount() + " payloadSame=" + (copy.get(DataComponents.CUSTOM_NAME) == name));
        NonNullList<ItemStack> firstSlots = slots(first); NonNullList<ItemStack> secondSlots = slots(second);
        ChestBlockEntity.swapContents(first, second);
        rows.add("chest swap firstList=" + (slots(first) == secondSlots) + " secondList=" + (slots(second) == firstSlots) + " first=" + slotState(first) + " second=" + slotState(second));
        CompoundTag saved = second.saveWithFullMetadata(registries);
        rows.add("chest saved=" + saved);
        var oldSlots = slots(first); first.loadWithComponents(TagValueInput.create(new ProblemReporter.Collector(), registries, saved));
        rows.add("chest load replacedList=" + (oldSlots != slots(first)) + " oldSize=" + oldSlots.size() + " loaded=" + slotState(first)
            + " loadedAlias=" + (first.getItem(0) == first.getItem(1)) + " roundtrip=" + saved.equals(first.saveWithFullMetadata(registries)));
        second.clearContent(); shared.setCount(9);
        rows.add("chest clear=" + slotState(second) + " escapedStack=" + shared.getCount() + " loaded=" + slotState(first));
        var replacement = NonNullList.withSize(27, ItemStack.EMPTY); replacement.set(4, shared); setItems.invoke(second, replacement);
        replacement.set(5, shared); second.getItem(4).setCount(6);
        rows.add("chest replace identity=" + (slots(second) == replacement) + " alias=" + (second.getItem(4) == second.getItem(5)) + " state=" + slotState(second));
        rows.add("chest remove same=" + (second.removeItemNoUpdate(4) == shared) + " surviving=" + slotState(second));
    }
    static void prototypeHashOrder() {
        var builder = DataComponentMap.builder();
        var insertion = new it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap<DataComponentType<?>, Object>();
        List<DataComponentType<Integer>> keys = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            var key = DataComponentType.<Integer>builder().persistent(Codec.INT).build(); keys.add(key);
            builder.set(key, i); insertion.put(key, i);
        }
        DataComponentMap actual = builder.build();
        var expected = new it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap<DataComponentType<?>, Object>(insertion);
        for (int step = 0; step <= keys.size(); step++) {
            var actualOrder = new ArrayList<>(actual.keySet()); var expectedOrder = new ArrayList<>(expected.keySet());
            if (!actualOrder.equals(expectedOrder)) throw new AssertionError("prototype reference-hash order changed at step " + step);
            if (actual.size() != expected.size() || actual.hashCode() != expected.hashCode()) throw new AssertionError("prototype size/hash changed at step " + step);
            for (var key : keys) if (!Objects.equals(actual.get(key), expected.get(key))) throw new AssertionError("prototype lookup changed at step " + step);
            if (step < keys.size()) { var key = keys.get((step * 5) % keys.size()); actual.keySet().remove(key); expected.keySet().remove(key); }
        }
        rows.add("prototype hash-order original-fastutil comparisons=13 all=true");
    }
    static int referenceSlot(Object key) { int mixed = System.identityHashCode(key) * 0x9e3779b9; return (mixed ^ (mixed >>> 16)) & 15; }
    static void hashCallbacks() {
        for (int entries : new int[]{2, 12}) {
            Probe value = new Probe("hash callback"); DataComponentType<Probe> probeKey;
            do { probeKey = DataComponentType.<Probe>builder().persistent(Codec.STRING.xmap(Probe::new, p -> p.name)).build(); }
            while (referenceSlot(probeKey) != 0);
            var builder = DataComponentMap.builder().set(probeKey, value);
            for (int i = 1; i < entries; i++) {
                DataComponentType<Integer> key;
                do { key = DataComponentType.<Integer>builder().persistent(Codec.INT).build(); } while (entries >= 8 && referenceSlot(key) != 14);
                builder.set(key, i);
            }
            var prototype = builder.build(); List<String> calls = new ArrayList<>();
            value.onHash = () -> { calls.add("hash"); prototype.keySet().clear(); };
            String result;
            try { prototype.hashCode(); result = "returned"; } catch (Throwable e) { result = error(e); }
            value.onHash = () -> {};
            rows.add("prototype hash callback entries=" + entries + " " + result + " events=" + calls + " size=" + prototype.size() + " visible=" + prototype.get(probeKey));
        }
        Probe value = new Probe("patch hash callback");
        var patch = DataComponentPatch.builder().set(KEY, value).set(DataComponents.REPAIR_COST, 4).build(); List<String> calls = new ArrayList<>();
        value.onHash = () -> { calls.add("hash"); patch.entrySet().clear(); };
        String result;
        try { patch.hashCode(); result = "returned"; } catch (Throwable e) { result = error(e); }
        value.onHash = () -> {};
        rows.add("patch hash callback " + result + " events=" + calls + " size=" + patch.size());
    }
    static void prototypeWrappingCursor() {
        var insertion = new it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap<DataComponentType<?>, Object>();
        var builder = DataComponentMap.builder();
        for (int attempts = 0; insertion.size() < 12; attempts++) {
            if (attempts >= 100000) throw new AssertionError("No wrapping identity-hash fixture");
            var key = DataComponentType.<Integer>builder().persistent(Codec.INT).build();
            int mixed = System.identityHashCode(key) * 0x9e3779b9; mixed ^= mixed >>> 16;
            if ((mixed & 15) == 14) { insertion.put(key, insertion.size()); builder.set(key, insertion.size() - 1); }
        }
        var actual = builder.build();
        var expected = new it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap<DataComponentType<?>, Object>(insertion);
        var left = actual.keySet().iterator(); var right = expected.keySet().iterator(); int steps = 0;
        while (right.hasNext()) {
            if (!left.hasNext() || left.next() != right.next()) throw new AssertionError("prototype wrapping iterator order changed at " + steps);
            left.remove(); right.remove(); steps++;
            if (actual.size() != expected.size() || actual.hashCode() != expected.hashCode()) throw new AssertionError("prototype wrapping iterator removal changed");
        }
        if (left.hasNext() || steps != 12 || actual.size() != 0) throw new AssertionError("prototype wrapping iterator lost/duplicated key");
        rows.add("prototype wrapping-cluster iterator removals=12 all=true");
    }
    static void stockListFailures() throws Exception {
        var list = slots(chest());
        List<Runnable> operations = List.of(() -> list.get(-1), () -> list.get(27), () -> list.set(-1, ItemStack.EMPTY),
            () -> list.set(27, ItemStack.EMPTY), () -> list.set(-1, null), () -> list.add(-1, ItemStack.EMPTY),
            () -> list.remove(-1), () -> list.remove(0));
        for (int i = 0; i < operations.size(); i++) {
            try { operations.get(i).run(); rows.add("fixed slots " + i + " accepted size=" + list.size()); }
            catch (Throwable e) { rows.add("fixed slots " + i + " " + error(e) + " size=" + list.size()); }
        }
    }
    static void reporting() throws Exception {
        ChestBlockEntity chest = chest(); chest.setItem(0, new ItemStack(Items.STICK, 2)); chest.setItem(1, new ItemStack(Items.STICK, 3));
        CompoundTag valid = chest.saveWithFullMetadata(registries);
        ListTag list = valid.getListOrEmpty("Items");
        CompoundTag broken = list.getCompoundOrEmpty(0); broken.putString("id", "test:missing_item");
        List<String> calls = new ArrayList<>();
        ProblemReporter reporter = new ProblemReporter() {
            public ProblemReporter forChild(ProblemReporter.PathElement element) { calls.add("child:" + element.get()); return this; }
            public void report(ProblemReporter.Problem problem) {
                calls.add("report:" + problem.description());
                list.getCompoundOrEmpty(1).putInt("count", 11);
            }
        };
        ChestBlockEntity loaded = chest();
        try { loaded.loadWithComponents(TagValueInput.create(reporter, registries, valid)); rows.add("load reporter calls=" + calls + " state=" + slotState(loaded)); }
        catch (Throwable e) { rows.add("load reporter " + error(e) + " calls=" + calls + " state=" + slotState(loaded)); }
        // Preserve the original source iterator's observed structural behavior.
        list.clear(); CompoundTag again = broken.copy(); list.add(again); list.add(new ItemStackWithSlotTag(1, 3).tag());
        List<String> prefix = new ArrayList<>(); boolean[] appended = {false};
        ProblemReporter structural = new ProblemReporter() {
            public ProblemReporter forChild(ProblemReporter.PathElement element) { return this; }
            public void report(ProblemReporter.Problem problem) {
                prefix.add("report");
                if (!appended[0]) { appended[0] = true; list.add(new ItemStackWithSlotTag(2, 13).tag()); }
            }
        };
        try { loaded.loadWithComponents(TagValueInput.create(structural, registries, valid)); rows.add("load structural success prefix=" + prefix + " state=" + slotState(loaded)); }
        catch (Throwable e) { rows.add("load structural " + error(e) + " prefix=" + prefix + " state=" + slotState(loaded)); }
    }
    record ItemStackWithSlotTag(int slot, int count) {
        CompoundTag tag() { CompoundTag tag = new CompoundTag(); tag.putByte("Slot", (byte)slot); tag.putString("id", "minecraft:stick"); tag.putInt("count", count); return tag; }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        registries = net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        getItems = ChestBlockEntity.class.getDeclaredMethod("getItems"); getItems.setAccessible(true);
        setItems = ChestBlockEntity.class.getDeclaredMethod("setItems", NonNullList.class); setItems.setAccessible(true);
        counts(); maps(); patchWrapperIdentity(); malformedPatchCounts(); prototypeHashOrder(); prototypeWrappingCursor(); hashCallbacks(); chests(); stockListFailures(); reporting();
        Files.write(Path.of(args[0]), rows); System.out.println("ITEM_STATE_CONTRACT rows=" + rows.size());
    }
}
