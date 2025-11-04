package MattMC.world;

import MattMC.util.AppPaths;
import net.querz.nbt.tag.CompoundTag;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages saving and loading worlds to/from disk using Minecraft-style format.
 * Uses region files (.mca) in Anvil format and level.dat with NBT.
 */
public final class WorldSaveManager {
    
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
     * Save a world to disk using Minecraft-style format.
     */
    public static void saveWorld(World world, String worldName, float playerX, float playerY, float playerZ, float playerYaw, float playerPitch) throws IOException {
        Path savesDir = getSavesDirectory();
        Path worldDir = savesDir.resolve(worldName);
        System.out.println("[DEBUG] Saving world to: " + worldDir.toAbsolutePath());
        Files.createDirectories(worldDir);
        
        // Save level.dat with NBT format
        LevelData levelData = new LevelData();
        levelData.setWorldName(worldName);
        levelData.setLastPlayed(System.currentTimeMillis());
        levelData.setPlayerX(playerX);
        levelData.setPlayerY(playerY);
        levelData.setPlayerZ(playerZ);
        levelData.setPlayerYaw(playerYaw);
        levelData.setPlayerPitch(playerPitch);
        
        // Set spawn at player's location
        levelData.setSpawnX((int) playerX);
        levelData.setSpawnY((int) playerY);
        levelData.setSpawnZ((int) playerZ);
        
        Path levelDatFile = worldDir.resolve("level.dat");
        levelData.save(levelDatFile);
        
        // Save backup
        if (Files.exists(levelDatFile)) {
            Path backupFile = worldDir.resolve("level.dat_old");
            try {
                Files.copy(levelDatFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Failed to create level.dat backup: " + e.getMessage());
            }
        }
        
        // Save chunks to region files
        Path regionDir = worldDir.resolve("region");
        Files.createDirectories(regionDir);
        
        // Group chunks by region
        Map<String, List<Chunk>> chunksByRegion = new HashMap<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            int[] regionCoords = RegionFile.getRegionCoords(chunk.chunkX(), chunk.chunkZ());
            String regionKey = regionCoords[0] + "," + regionCoords[1];
            chunksByRegion.computeIfAbsent(regionKey, k -> new ArrayList<>()).add(chunk);
        }
        
        // Save each region
        for (Map.Entry<String, List<Chunk>> entry : chunksByRegion.entrySet()) {
            String[] coords = entry.getKey().split(",");
            int regionX = Integer.parseInt(coords[0]);
            int regionZ = Integer.parseInt(coords[1]);
            
            Path regionFilePath = regionDir.resolve(String.format("r.%d.%d.mca", regionX, regionZ));
            
            try (RegionFile regionFile = new RegionFile(regionFilePath, regionX, regionZ)) {
                for (Chunk chunk : entry.getValue()) {
                    CompoundTag chunkNBT = ChunkNBT.toNBT(chunk);
                    regionFile.writeChunk(chunk.chunkX(), chunk.chunkZ(), chunkNBT);
                }
            }
        }
        
        System.out.println("World saved: " + worldName + " (" + world.getLoadedChunkCount() + " chunks in region files)");
    }
    
    /**
     * Load a world from disk using Minecraft-style format.
     * Returns the loaded World and metadata.
     */
    public static WorldLoadResult loadWorld(String worldName) throws IOException {
        Path worldDir = getSavesDirectory().resolve(worldName);
        if (!Files.exists(worldDir)) {
            throw new IOException("World does not exist: " + worldName);
        }
        
        // Load level.dat
        Path levelDatFile = worldDir.resolve("level.dat");
        LevelData levelData;
        
        // Try to load level.dat, fallback to level.dat_old if corrupted
        try {
            levelData = LevelData.load(levelDatFile);
        } catch (IOException e) {
            System.err.println("Failed to load level.dat, trying backup: " + e.getMessage());
            Path backupFile = worldDir.resolve("level.dat_old");
            if (Files.exists(backupFile)) {
                levelData = LevelData.load(backupFile);
            } else {
                throw new IOException("Failed to load world data and no backup available", e);
            }
        }
        
        // Create world
        World world = new World();
        
        // Load chunks from region files
        Path regionDir = worldDir.resolve("region");
        if (Files.exists(regionDir)) {
            try (var stream = Files.list(regionDir)) {
                var regionFiles = stream
                        .filter(p -> p.getFileName().toString().endsWith(".mca"))
                        .collect(Collectors.toList());
                
                for (Path regionFilePath : regionFiles) {
                    try {
                        loadRegion(world, regionFilePath);
                    } catch (IOException e) {
                        System.err.println("Failed to load region: " + regionFilePath + " - " + e.getMessage());
                    }
                }
            }
        }
        
        System.out.println("World loaded: " + worldName + " (" + world.getLoadedChunkCount() + " chunks)");
        
        // Convert LevelData to WorldMetadata for compatibility
        WorldMetadata metadata = new WorldMetadata();
        metadata.worldName = levelData.getWorldName();
        metadata.lastPlayed = levelData.getLastPlayed();
        metadata.playerX = (float) levelData.getPlayerX();
        metadata.playerY = (float) levelData.getPlayerY();
        metadata.playerZ = (float) levelData.getPlayerZ();
        metadata.playerYaw = levelData.getPlayerYaw();
        metadata.playerPitch = levelData.getPlayerPitch();
        
        return new WorldLoadResult(world, metadata);
    }
    
    /**
     * Load all chunks from a region file.
     */
    private static void loadRegion(World world, Path regionFilePath) throws IOException {
        // Parse region coordinates from filename: r.x.z.mca
        String fileName = regionFilePath.getFileName().toString();
        String[] parts = fileName.replace("r.", "").replace(".mca", "").split("\\.");
        if (parts.length != 2) {
            System.err.println("Invalid region filename: " + fileName);
            return;
        }
        
        int regionX = Integer.parseInt(parts[0]);
        int regionZ = Integer.parseInt(parts[1]);
        
        try (RegionFile regionFile = new RegionFile(regionFilePath, regionX, regionZ)) {
            // Try to load all possible chunks in the region
            for (int localX = 0; localX < RegionFile.REGION_SIZE; localX++) {
                for (int localZ = 0; localZ < RegionFile.REGION_SIZE; localZ++) {
                    int chunkX = regionX * RegionFile.REGION_SIZE + localX;
                    int chunkZ = regionZ * RegionFile.REGION_SIZE + localZ;
                    
                    if (regionFile.hasChunk(chunkX, chunkZ)) {
                        try {
                            CompoundTag chunkNBT = regionFile.readChunk(chunkX, chunkZ);
                            if (chunkNBT != null) {
                                Chunk chunk = ChunkNBT.fromNBT(chunkNBT);
                                // Manually add the loaded chunk to the world
                                world.getChunk(chunk.chunkX(), chunk.chunkZ()); // Ensures it's in the map
                                // Copy the loaded data
                                Chunk worldChunk = world.getChunk(chunk.chunkX(), chunk.chunkZ());
                                for (int x = 0; x < Chunk.WIDTH; x++) {
                                    for (int y = 0; y < Chunk.HEIGHT; y++) {
                                        for (int z = 0; z < Chunk.DEPTH; z++) {
                                            worldChunk.setBlock(x, y, z, chunk.getBlock(x, y, z));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to load chunk (" + chunkX + ", " + chunkZ + "): " + e.getMessage());
                        }
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
