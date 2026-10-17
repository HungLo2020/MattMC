import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Per-registry decode time of the vanilla worldgen registries, compiled
 * unchanged against a reference and Current (TimeContent.py --observer
 * RegistryTiming). After one full load, every file of each registry goes
 * through the loader's per-file path (`loadElementFromResource`) with the
 * loaded registries' ops: warmup rounds, then the mean per round in ms.
 * Shows which registries a whole-load difference comes from. Argument: rounds.
 */
public final class RegistryTiming {
    public static void main(String[] args) throws Exception {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        var worldgen = net.minecraft.resources.RegistryDataLoader.load(packs, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        // The loader's own lookups (static registries with their pending tags) plus the loaded registries.
        var ops = net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.concat(lookups.stream(), worldgen.listRegistries()))
            .createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        Method load = net.minecraft.resources.RegistryDataLoader.class.getDeclaredMethod("loadElementFromResource",
            net.minecraft.core.WritableRegistry.class, com.mojang.serialization.Decoder.class, net.minecraft.resources.RegistryOps.class,
            ResourceKey.class, Resource.class, RegistrationInfo.class);
        load.setAccessible(true);
        var times = new LinkedHashMap<String, Double>();
        for (var data : net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES) {
            String dir = data.key().location().getPath();
            var files = new ArrayList<byte[]>();
            packs.listResources(dir, l -> l.getPath().endsWith(".json") && l.getPath().indexOf('/', dir.length() + 1) < 0).forEach((location, resource) -> {
                try (var in = resource.open()) { files.add(in.readAllBytes()); } catch (Exception e) { throw new RuntimeException(e); }
            });
            if (files.isEmpty()) continue;
            int warmup = Math.max(1, rounds / 5);
            long start = 0;
            for (int round = 0; round < rounds + warmup; round++) {
                if (round == warmup) start = System.nanoTime();
                @SuppressWarnings({"unchecked", "rawtypes"})
                var registry = new MappedRegistry(data.key(), com.mojang.serialization.Lifecycle.stable());
                for (int i = 0; i < files.size(); i++) {
                    byte[] bytes = files.get(i);
                    var key = ResourceKey.create(data.key(), ResourceLocation.withDefaultNamespace("f" + i));
                    load.invoke(null, registry, data.elementCodec(), ops, key, new Resource(null, () -> new ByteArrayInputStream(bytes)), RegistrationInfo.BUILT_IN);
                }
            }
            times.put(dir.replaceAll("\\W", "_"), (System.nanoTime() - start) / 1e6 / rounds);
        }
        var line = new StringBuilder("REGISTRY_TIMING");
        double total = 0;
        for (var e : times.entrySet()) {
            line.append(String.format(" %s=%.3f", e.getKey(), e.getValue()));
            total += e.getValue();
        }
        System.out.println(line.append(String.format(" total=%.3f", total)));
    }
}
