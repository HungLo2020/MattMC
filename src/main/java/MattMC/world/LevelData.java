package MattMC.world;

import MattMC.nbt.NBTUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Represents the level.dat file structure for a Minecraft-style world.
 * Stores world metadata and player information.
 */
public class LevelData {
    private String worldName;
    private long lastPlayed;
    private int spawnX;
    private int spawnY;
    private int spawnZ;
    private int gameMode;
    private boolean hardcore;
    private int difficulty;
    
    // Player data
    private double playerX;
    private double playerY;
    private double playerZ;
    private float playerYaw;
    private float playerPitch;
    
    public LevelData() {
        this.worldName = "World";
        this.lastPlayed = System.currentTimeMillis();
        this.spawnX = 0;
        this.spawnY = 64;
        this.spawnZ = 0;
        this.gameMode = 0; // Survival
        this.hardcore = false;
        this.difficulty = 2; // Normal
        this.playerX = 0.0;
        this.playerY = 65.0;
        this.playerZ = 0.0;
        this.playerYaw = 0.0f;
        this.playerPitch = 0.0f;
    }
    
    /**
     * Convert level data to NBT format (Map-based).
     */
    public Map<String, Object> toNBT() {
        Map<String, Object> data = new HashMap<>();
        
        // World metadata
        data.put("LevelName", worldName);
        data.put("LastPlayed", lastPlayed);
        data.put("SpawnX", spawnX);
        data.put("SpawnY", spawnY);
        data.put("SpawnZ", spawnZ);
        data.put("GameType", gameMode);
        data.put("hardcore", (byte) (hardcore ? 1 : 0));
        data.put("Difficulty", (byte) difficulty);
        
        // Version info
        Map<String, Object> version = new HashMap<>();
        version.put("Name", "MattMC 1.0");
        version.put("Id", 1);
        data.put("Version", version);
        
        // Player data
        Map<String, Object> player = new HashMap<>();
        
        List<Double> pos = new ArrayList<>();
        pos.add(playerX);
        pos.add(playerY);
        pos.add(playerZ);
        player.put("Pos", pos);
        
        List<Float> rotation = new ArrayList<>();
        rotation.add(playerYaw);
        rotation.add(playerPitch);
        player.put("Rotation", rotation);
        
        data.put("Player", player);
        
        Map<String, Object> root = new HashMap<>();
        root.put("Data", data);
        
        return root;
    }
    
    /**
     * Load level data from NBT format (Map-based).
     */
    public static LevelData fromNBT(Map<String, Object> root) {
        LevelData levelData = new LevelData();
        
        Object dataObj = root.get("Data");
        if (!(dataObj instanceof Map)) {
            return levelData;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        
        // World metadata
        if (data.get("LevelName") instanceof String) {
            levelData.worldName = (String) data.get("LevelName");
        }
        if (data.get("LastPlayed") instanceof Long) {
            levelData.lastPlayed = (Long) data.get("LastPlayed");
        }
        if (data.get("SpawnX") instanceof Integer) {
            levelData.spawnX = (Integer) data.get("SpawnX");
        }
        if (data.get("SpawnY") instanceof Integer) {
            levelData.spawnY = (Integer) data.get("SpawnY");
        }
        if (data.get("SpawnZ") instanceof Integer) {
            levelData.spawnZ = (Integer) data.get("SpawnZ");
        }
        if (data.get("GameType") instanceof Integer) {
            levelData.gameMode = (Integer) data.get("GameType");
        }
        if (data.get("hardcore") instanceof Byte) {
            levelData.hardcore = ((Byte) data.get("hardcore")) != 0;
        }
        if (data.get("Difficulty") instanceof Byte) {
            levelData.difficulty = (Byte) data.get("Difficulty");
        }
        
        // Player data
        Object playerObj = data.get("Player");
        if (playerObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> player = (Map<String, Object>) playerObj;
            
            Object posObj = player.get("Pos");
            if (posObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Double> pos = (List<Double>) posObj;
                if (pos.size() >= 3) {
                    levelData.playerX = pos.get(0);
                    levelData.playerY = pos.get(1);
                    levelData.playerZ = pos.get(2);
                }
            }
            
            Object rotationObj = player.get("Rotation");
            if (rotationObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Float> rotation = (List<Float>) rotationObj;
                if (rotation.size() >= 2) {
                    levelData.playerYaw = rotation.get(0);
                    levelData.playerPitch = rotation.get(1);
                }
            }
        }
        
        return levelData;
    }
    
    /**
     * Save level data to a file.
     */
    public void save(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        
        try (OutputStream out = Files.newOutputStream(filePath)) {
            NBTUtil.writeCompressed(toNBT(), out);
        }
    }
    
    /**
     * Load level data from a file.
     */
    public static LevelData load(Path filePath) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            Map<String, Object> root = NBTUtil.readCompressed(in);
            return fromNBT(root);
        }
    }
    
    // Getters and setters
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    
    public long getLastPlayed() { return lastPlayed; }
    public void setLastPlayed(long lastPlayed) { this.lastPlayed = lastPlayed; }
    
    public int getSpawnX() { return spawnX; }
    public void setSpawnX(int spawnX) { this.spawnX = spawnX; }
    
    public int getSpawnY() { return spawnY; }
    public void setSpawnY(int spawnY) { this.spawnY = spawnY; }
    
    public int getSpawnZ() { return spawnZ; }
    public void setSpawnZ(int spawnZ) { this.spawnZ = spawnZ; }
    
    public int getGameMode() { return gameMode; }
    public void setGameMode(int gameMode) { this.gameMode = gameMode; }
    
    public boolean isHardcore() { return hardcore; }
    public void setHardcore(boolean hardcore) { this.hardcore = hardcore; }
    
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    
    public double getPlayerX() { return playerX; }
    public void setPlayerX(double playerX) { this.playerX = playerX; }
    
    public double getPlayerY() { return playerY; }
    public void setPlayerY(double playerY) { this.playerY = playerY; }
    
    public double getPlayerZ() { return playerZ; }
    public void setPlayerZ(double playerZ) { this.playerZ = playerZ; }
    
    public float getPlayerYaw() { return playerYaw; }
    public void setPlayerYaw(float playerYaw) { this.playerYaw = playerYaw; }
    
    public float getPlayerPitch() { return playerPitch; }
    public void setPlayerPitch(float playerPitch) { this.playerPitch = playerPitch; }
}
