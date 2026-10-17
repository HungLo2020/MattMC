import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.biome.Biome;

/**
 * Content startup costs in one fresh JVM, compiled unchanged against Frozen
 * and Current: bootstrap (item, entity and component registration), the
 * worldgen registry load from the vanilla data pack (biome decoding
 * included), and repeated decoding of every biome file through the registry
 * loader's per-file path. Prints one CONTENT_TIMING line in milliseconds.
 */
public final class ContentTiming {
    public static void main(String[] args) throws Exception {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        net.minecraft.SharedConstants.tryDetectVersion();
        long t0 = System.nanoTime();
        net.minecraft.server.Bootstrap.bootStrap();
        long t1 = System.nanoTime();
        var packs = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(packs, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
        long t2 = System.nanoTime();
        var worldgen = net.minecraft.resources.RegistryDataLoader.load(packs, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        long t3 = System.nanoTime();
        var registries = layers.replaceFrom(net.minecraft.server.RegistryLayer.WORLDGEN, worldgen).compositeAccess();
        var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        Method load = net.minecraft.resources.RegistryDataLoader.class.getDeclaredMethod("loadElementFromResource",
            net.minecraft.core.WritableRegistry.class, com.mojang.serialization.Decoder.class, net.minecraft.resources.RegistryOps.class,
            ResourceKey.class, Resource.class, RegistrationInfo.class);
        load.setAccessible(true);
        var files = new ArrayList<byte[]>();
        packs.listResources("worldgen/biome", l -> l.getPath().endsWith(".json")).forEach((location, resource) -> {
            try (var in = resource.open()) { files.add(in.readAllBytes()); } catch (Exception e) { throw new RuntimeException(e); }
        });
        com.mojang.serialization.Decoder<?> biomeDecoder = null;
        for (var data : net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES)
            if (data.key().equals(Registries.BIOME)) biomeDecoder = data.elementCodec();
        long decodeStart = 0;
        int warmup = Math.max(1, rounds / 5);
        for (int round = 0; round < rounds + warmup; round++) {
            if (round == warmup) decodeStart = System.nanoTime();
            var registry = new MappedRegistry<Biome>(Registries.BIOME, com.mojang.serialization.Lifecycle.stable());
            for (int i = 0; i < files.size(); i++) {
                byte[] bytes = files.get(i);
                var key = ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("b" + i));
                load.invoke(null, registry, biomeDecoder, ops, key, new Resource(null, () -> new ByteArrayInputStream(bytes)), RegistrationInfo.BUILT_IN);
            }
        }
        long t4 = System.nanoTime();
        System.out.printf("CONTENT_TIMING bootstrap=%.1f worldgen_load=%.1f biome_decode_per_round=%.3f biomes=%d%n",
            (t1 - t0) / 1e6, (t3 - t2) / 1e6, (t4 - decodeStart) / 1e6 / rounds, files.size());
    }
}
