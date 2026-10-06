package net.minecraft.world.level.chunk.storage;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.PlayerChunkDistancesVerification;
import net.minecraft.world.level.chunk.NativeChunkSections;
import net.minecraft.world.level.levelgen.WorldgenTestRegistries;

/** Opt-in benchmark of chunk saving with Rust-encoded sections against the
 * Java tag tree and tape writer (-Dmattmc.storage.javaChunkSections=true),
 * over the 40 saved chunks of {@code storage/chunks.bin}. Timed per round:
 * {@code encode} turns every chunk's {@link SerializableChunkData} into its
 * tape (what {@code ChunkMap.save} and the IO thread do before the region
 * write); {@code save} adds the region file write of each tape. Parsing the
 * saved NBT into chunk data is untimed setup. */
public final class NativeChunkSectionsVerification {
    /** {@code main(mode, case[, quick])}: mode {@code java} or {@code native}; case {@code encode} or {@code save}. */
    public static void main(String[] args) throws Exception {
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        if (!List.of("java", "native").contains(mode) || !List.of("encode", "save").contains(name)) throw new IllegalArgumentException();
        NativeChunkSectionsTest.registries = WorldgenTestRegistries.load();
        NativeChunkSectionsTest.factory = net.minecraft.world.level.chunk.PalettedContainerFactory.create(NativeChunkSectionsTest.registries);
        var dir = Files.createTempDirectory("chunk-sections-bench");
        try {
            var chunks = new ArrayList<SerializableChunkData>();
            for (var tag : NativeChunkSectionsTest.corpus()) chunks.add(NativeChunkSectionsTest.parse(tag));
            NativeChunkSections.setEnabled(mode.equals("native"));
            long before = NativeChunkSections.CHUNKS.get();
            var storage = new RegionFileStorage(new RegionStorageInfo("bench", net.minecraft.world.level.Level.OVERWORLD, "chunk"), dir, false);
            PlayerChunkDistancesVerification.measure(mode, name, quick, () -> chunks, list -> {
                long checksum = 0;
                for (var data : list) {
                    byte[] tape = data.encode().tape();
                    if (name.equals("save")) {
                        try {
                            storage.writeTape(data.chunkPos(), tape);
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }
                    checksum = checksum * 31 + java.util.Arrays.hashCode(tape);
                }
                return checksum;
            });
            storage.close();
            long encoded = NativeChunkSections.CHUNKS.get() - before;
            if (mode.equals("java") != (encoded == 0)) throw new IllegalStateException("Route not taken: " + encoded);
            System.out.println("CHUNK_SECTIONS_ROUTE case=" + name + " mode=" + mode + " rust_chunks=" + encoded);
        } finally {
            NativeChunkSections.setEnabled(true);
            WorldgenTestRegistries.close();
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
