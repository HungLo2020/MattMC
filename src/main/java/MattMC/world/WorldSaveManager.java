package MattMC.world;

import MattMC.util.AppPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages saving and loading worlds to/from disk.
 * Similar to Minecraft's world save system.
 */
public final class WorldSaveManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Get the saves directory where all worlds are stored.
     * Saves are stored in the MattMC data directory alongside options and other game data.
     */
    public static Path getSavesDirectory() throws IOException {
        // Get the MattMC data directory first, then create saves inside it
        Path mattmcDir = AppPaths.ensureDataDirInJarParent("MattMC");
        Path savesDir = mattmcDir.resolve("saves");
        Files.createDirectories(savesDir);
        return savesDir;
    }
    
    /**
     * Get a list of all saved world names.
     */
    public static List<String> listWorlds() {
        try {
            Path savesDir = getSavesDirectory();
            System.out.println("[DEBUG] Listing worlds from: " + savesDir.toAbsolutePath());
            if (!Files.exists(savesDir)) {
                System.out.println("[DEBUG] Saves directory does not exist yet");
                return new ArrayList<>();
            }
            
            try (var stream = Files.list(savesDir)) {
                return stream
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            System.err.println("Failed to list worlds: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Check if a world with the given name exists.
     */
    public static boolean worldExists(String worldName) {
        try {
            Path worldDir = getSavesDirectory().resolve(worldName);
            return Files.exists(worldDir) && Files.isDirectory(worldDir);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Generate a unique world name based on a base name.
     * If "New World" exists, returns "New World (2)", etc.
     */
    public static String generateUniqueWorldName(String baseName) {
        if (!worldExists(baseName)) {
            return baseName;
        }
        
        int counter = 2;
        while (worldExists(baseName + " (" + counter + ")")) {
            counter++;
        }
        return baseName + " (" + counter + ")";
    }
    
    /**
     * Save a world to disk.
     */
    public static void saveWorld(World world, String worldName, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) throws IOException {
        Path savesDir = getSavesDirectory();
        Path worldDir = savesDir.resolve(worldName);
        System.out.println("[DEBUG] Saving world to: " + worldDir.toAbsolutePath());
        Files.createDirectories(worldDir);
        
        // Save world metadata
        WorldMetadata metadata = new WorldMetadata();
        metadata.worldName = worldName;
        metadata.lastPlayed = System.currentTimeMillis();
        metadata.playerX = playerX;
        metadata.playerY = playerY;
        metadata.playerZ = playerZ;
        metadata.playerYaw = playerYaw;
        metadata.playerPitch = playerPitch;
        
        Path metadataFile = worldDir.resolve("level.json");
        try (Writer writer = Files.newBufferedWriter(metadataFile)) {
            GSON.toJson(metadata, writer);
        }
        
        // Save chunks
        Path chunksDir = worldDir.resolve("chunks");
        Files.createDirectories(chunksDir);
        
        for (Chunk chunk : world.getLoadedChunks()) {
            saveChunk(chunk, chunksDir);
        }
        
        System.out.println("World saved: " + worldName);
    }
    
    /**
     * Save a single chunk to disk.
     */
    private static void saveChunk(Chunk chunk, Path chunksDir) throws IOException {
        String fileName = "chunk_" + chunk.chunkX() + "_" + chunk.chunkZ() + ".dat";
        Path chunkFile = chunksDir.resolve(fileName);
        
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(chunkFile)))) {
            
            // Write chunk coordinates
            out.writeInt(chunk.chunkX());
            out.writeInt(chunk.chunkZ());
            
            // Write block data
            // For each position, write block identifier
            for (int x = 0; x < Chunk.WIDTH; x++) {
                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    for (int z = 0; z < Chunk.DEPTH; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        String identifier = block.getIdentifier();
                        out.writeUTF(identifier != null ? identifier : "mattmc:air");
                    }
                }
            }
        }
    }
    
    /**
     * Load a world from disk.
     * Returns the loaded World and metadata.
     */
    public static WorldLoadResult loadWorld(String worldName) throws IOException {
        Path worldDir = getSavesDirectory().resolve(worldName);
        if (!Files.exists(worldDir)) {
            throw new IOException("World does not exist: " + worldName);
        }
        
        // Load metadata
        Path metadataFile = worldDir.resolve("level.json");
        WorldMetadata metadata;
        try (Reader reader = Files.newBufferedReader(metadataFile)) {
            metadata = GSON.fromJson(reader, WorldMetadata.class);
        }
        
        // Create world
        World world = new World();
        
        // Load chunks
        Path chunksDir = worldDir.resolve("chunks");
        if (Files.exists(chunksDir)) {
            try (var stream = Files.list(chunksDir)) {
                var chunkFiles = stream
                        .filter(p -> p.getFileName().toString().endsWith(".dat"))
                        .collect(Collectors.toList());
                
                for (Path chunkFile : chunkFiles) {
                    try {
                        loadChunk(world, chunkFile);
                    } catch (IOException e) {
                        System.err.println("Failed to load chunk: " + chunkFile + " - " + e.getMessage());
                    }
                }
            }
        }
        
        System.out.println("World loaded: " + worldName);
        return new WorldLoadResult(world, metadata);
    }
    
    /**
     * Load a single chunk from disk.
     */
    private static void loadChunk(World world, Path chunkFile) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(chunkFile)))) {
            
            // Read chunk coordinates
            int chunkX = in.readInt();
            int chunkZ = in.readInt();
            
            // Get or create chunk
            Chunk chunk = world.getChunk(chunkX, chunkZ);
            
            // Read block data
            for (int x = 0; x < Chunk.WIDTH; x++) {
                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    for (int z = 0; z < Chunk.DEPTH; z++) {
                        String identifier = in.readUTF();
                        Block block = Blocks.getBlockOrAir(identifier);
                        chunk.setBlock(x, y, z, block);
                    }
                }
            }
        }
    }
    
    /**
     * Delete a world from disk.
     */
    public static void deleteWorld(String worldName) throws IOException {
        Path worldDir = getSavesDirectory().resolve(worldName);
        if (Files.exists(worldDir)) {
            deleteDirectory(worldDir);
            System.out.println("World deleted: " + worldName);
        }
    }
    
    private static void deleteDirectory(Path dir) throws IOException {
        // Collect all paths first to properly close the stream
        List<Path> pathsToDelete;
        try (var stream = Files.walk(dir)) {
            pathsToDelete = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        }
        
        // Delete all paths, collecting any errors
        List<IOException> errors = new ArrayList<>();
        for (Path path : pathsToDelete) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                errors.add(e);
                System.err.println("Failed to delete: " + path);
            }
        }
        
        // If any deletions failed, throw an exception
        if (!errors.isEmpty()) {
            IOException ex = new IOException("Failed to delete some files/directories");
            for (IOException error : errors) {
                ex.addSuppressed(error);
            }
            throw ex;
        }
    }
    
    /**
     * World metadata stored in level.json
     */
    public static class WorldMetadata {
        public String worldName;
        public long lastPlayed;
        public float playerX;
        public float playerY;
        public float playerZ;
        public float playerYaw;
        public float playerPitch;
    }
    
    /**
     * Result of loading a world.
     */
    public static class WorldLoadResult {
        public final World world;
        public final WorldMetadata metadata;
        
        public WorldLoadResult(World world, WorldMetadata metadata) {
            this.world = world;
            this.metadata = metadata;
        }
    }
}
