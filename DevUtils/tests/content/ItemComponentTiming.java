import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.lang.management.ManagementFactory;
import java.lang.management.CompilationMXBean;
import java.util.Arrays;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/** Matched component workloads; compile unchanged against Frozen and Current.
 * Each component is exercised directly and in an explicit stack patch, using
 * real registry contexts. Reports cold totals and warm per-family costs in ms.
 * Run without concurrent builds; the caller supplies alternating JVM repeats.
 */
public final class ItemComponentTiming {
    private static volatile int sink;
    private record Input(String name, String json) {}
    private static final Input[] INPUTS = {
        new Input("break_sound", "\"minecraft:entity.generic.eat\""),
        new Input("repairable", "{\"items\":[\"minecraft:stone\",\"minecraft:stick\"]}"),
        new Input("enchantable", "{\"value\":12}"),
        new Input("damage_resistant", "{\"types\":\"#minecraft:is_fire\"}"),
        new Input("trim", "{\"material\":\"minecraft:iron\",\"pattern\":\"minecraft:coast\"}"),
        new Input("instrument", "\"minecraft:ponder_goat_horn\""),
        new Input("jukebox_playable", "\"minecraft:cat\""),
        new Input("provides_trim_material", "\"minecraft:iron\""),
        new Input("banner_patterns", "[{\"pattern\":\"minecraft:stripe_bottom\",\"color\":\"red\"}]"),
        new Input("food", "{\"nutrition\":4,\"saturation\":2.4,\"can_always_eat\":true}"),
        new Input("attribute_modifiers", "[{\"type\":\"minecraft:attack_damage\",\"id\":\"test:boost\",\"amount\":1.5,\"operation\":\"add_value\",\"slot\":\"mainhand\"}]"),
        new Input("potion_contents", "{\"potion\":\"minecraft:healing\",\"custom_effects\":[{\"id\":\"minecraft:speed\",\"duration\":100}]}"),
        new Input("suspicious_stew_effects", "[{\"id\":\"minecraft:speed\",\"duration\":100}]"),
        new Input("written_book_content", "{\"title\":\"Test\",\"author\":\"Tester\",\"pages\":[\"page\"],\"generation\":1}"),
        new Input("bees", "[{\"entity_data\":{\"id\":\"minecraft:bee\",\"Health\":10.0},\"ticks_in_hive\":2,\"min_ticks_in_hive\":10}]")
    };
    private record Work(String name, Codec<Object> component, Tag componentTag, Tag stackTag, List<ItemStack> stacks) {}

    private static void runRound(List<Work> work, RegistryOps<Tag> ops, int copies, long[][] times) {
        for (int index = 0; index < work.size(); index++) {
            Work item = work.get(index);
            int hash = 0;
            long a = System.nanoTime();
            for (int i = 0; i < copies; i++) hash = 31 * hash + item.component().parse(ops, item.componentTag()).getOrThrow().hashCode();
            long b = System.nanoTime();
            for (int i = 0; i < copies; i++) hash += ItemStack.CODEC.parse(ops, item.stackTag()).getOrThrow().getCount();
            long c = System.nanoTime();
            for (ItemStack stack : item.stacks()) hash += ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().sizeInBytes();
            long d = System.nanoTime();
            sink = hash;
            times[index][0] += b - a;
            times[index][1] += c - b;
            times[index][2] += d - c;
        }
    }

    /** Exercise the same measured body until compilation has been quiet for 500ms. */
    private static int warm(List<Work> work, RegistryOps<Tag> ops, int copies, CompilationMXBean compiler, int minimumRounds, long minimumNanos) {
        long start = System.nanoTime(), quietSince = start, previousCompilation = compiler.getTotalCompilationTime();
        long[][] ignored = new long[work.size()][3];
        int rounds = 0;
        while (true) {
            long now = System.nanoTime(), compiled = compiler.getTotalCompilationTime();
            if (compiled != previousCompilation) {
                quietSince = now;
                previousCompilation = compiled;
            }
            if (rounds >= minimumRounds && now - start >= minimumNanos && now - quietSince >= 500_000_000L) return rounds;
            if (now - start > 60_000_000_000L) throw new IllegalStateException("Compiler did not become quiet during warmup");
            runRound(work, ops, copies, ignored);
            rounds++;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int copies = 32;
        var compilation = ManagementFactory.getCompilationMXBean();
        net.minecraft.SharedConstants.tryDetectVersion();
        long start = System.nanoTime();
        net.minecraft.server.Bootstrap.bootStrap();
        double bootstrap = (System.nanoTime() - start) / 1e6;
        try (var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            var loaded = net.minecraft.resources.RegistryDataLoader.load(packs, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var access = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, loaded).compositeAccess();
            RegistryOps<Tag> ops = access.createSerializationContext(NbtOps.INSTANCE);
            List<Work> work = new ArrayList<>();
            double coldComponent = 0, coldStack = 0;
            int contentChecksum = 1;
            for (Input input : INPUTS) {
                Codec<Object> codec = (Codec<Object>)net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                    .getValue(net.minecraft.resources.ResourceLocation.withDefaultNamespace(input.name())).codecOrThrow();
                JsonElement value = JsonParser.parseString(input.json());
                Tag componentTag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, value);
                JsonObject patch = new JsonObject();
                patch.add("minecraft:" + input.name(), value);
                JsonObject stack = new JsonObject();
                stack.addProperty("id", "minecraft:stick");
                stack.add("components", patch);
                Tag stackTag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, stack);
                start = System.nanoTime();
                Object decoded = codec.parse(ops, componentTag).getOrThrow();
                coldComponent += (System.nanoTime() - start) / 1e6;
                Tag encodedValue = codec.encodeStart(ops, decoded).getOrThrow();
                contentChecksum = 31 * contentChecksum + encodedValue.toString().hashCode();
                List<ItemStack> sources = new ArrayList<>();
                start = System.nanoTime();
                for (int i = 0; i < copies; i++) sources.add(ItemStack.CODEC.parse(ops, stackTag).getOrThrow());
                coldStack += (System.nanoTime() - start) / 1e6;
                work.add(new Work(input.name(), codec, componentTag, stackTag, sources));
            }
            int warmup = warm(work, ops, copies, compilation, Math.max(400, rounds / 2), 3_000_000_000L);
            List<double[][]> accepted = new ArrayList<>();
            int attempts = 0;
            while (accepted.size() < 3 && attempts < 9) {
                if (attempts > 0) warm(work, ops, copies, compilation, 0, 500_000_000L);
                attempts++;
                long[][] times = new long[work.size()][3];
                long beforeCompilation = compilation.getTotalCompilationTime(), epochStart = System.nanoTime();
                int actualRounds = 0;
                do {
                    runRound(work, ops, copies, times);
                    actualRounds++;
                } while (actualRounds < rounds || System.nanoTime() - epochStart < 2_000_000_000L);
                long elapsed = System.nanoTime() - epochStart;
                long compiled = compilation.getTotalCompilationTime() - beforeCompilation;
                double[][] perRound = new double[work.size()][3];
                JsonObject epoch = new JsonObject();
                epoch.addProperty("attempt", attempts);
                epoch.addProperty("rounds", actualRounds);
                epoch.addProperty("elapsed_ns", elapsed);
                epoch.addProperty("compilation_ms", compiled);
                epoch.addProperty("accepted", compiled == 0);
                for (int i = 0; i < work.size(); i++) {
                    for (int metric = 0; metric < 3; metric++) {
                        perRound[i][metric] = times[i][metric] / 1e6 / actualRounds;
                        epoch.addProperty(work.get(i).name() + "_" + metric, perRound[i][metric]);
                    }
                }
                System.out.println("ITEM_COMPONENT_EPOCH " + epoch);
                if (compiled == 0) accepted.add(perRound);
            }
            if (accepted.size() != 3) throw new IllegalStateException("Fewer than three compiler-quiet epochs; retain diagnostic attempts");
            StringBuilder line = new StringBuilder("ITEM_COMPONENT_TIMING");
            line.append(String.format(Locale.ROOT, " bootstrap=%.3f cold_component=%.3f cold_stack=%.3f copies=%d families=%d warmup=%d rounds=%d content_checksum=%d measured_compilation_ms=0 accepted_epochs=3 attempts=%d",
                bootstrap, coldComponent, coldStack, copies, work.size(), warmup, rounds, Integer.toUnsignedLong(contentChecksum), attempts));
            for (int i = 0; i < work.size(); i++) {
                String name = work.get(i).name();
                double[] medians = new double[3];
                for (int metric = 0; metric < 3; metric++) {
                    double[] samples = new double[3];
                    for (int epoch = 0; epoch < 3; epoch++) samples[epoch] = accepted.get(epoch)[i][metric];
                    Arrays.sort(samples);
                    medians[metric] = samples[1];
                }
                line.append(String.format(Locale.ROOT, " %s_direct_decode=%.6f %s_stack_decode=%.6f %s_stack_encode=%.6f",
                    name, medians[0], name, medians[1], name, medians[2]));
            }
            System.out.println(line);
        }
    }
}
