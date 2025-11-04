package MattMC.world;

import net.querz.nbt.io.*;
import net.querz.nbt.tag.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
     * Convert level data to NBT format.
     */
    public CompoundTag toNBT() {
        CompoundTag data = new CompoundTag();
        
        // World metadata
        data.putString("LevelName", worldName);
        data.putLong("LastPlayed", lastPlayed);
        data.putInt("SpawnX", spawnX);
        data.putInt("SpawnY", spawnY);
        data.putInt("SpawnZ", spawnZ);
        data.putInt("GameType", gameMode);
        data.putByte("hardcore", (byte) (hardcore ? 1 : 0));
        data.putByte("Difficulty", (byte) difficulty);
        
        // Version info
        CompoundTag version = new CompoundTag();
        version.putString("Name", "MattMC 1.0");
        version.putInt("Id", 1);
        data.put("Version", version);
        
        // Player data
        CompoundTag player = new CompoundTag();
        
        ListTag<DoubleTag> pos = new ListTag<>(DoubleTag.class);
        pos.add(new DoubleTag(playerX));
        pos.add(new DoubleTag(playerY));
        pos.add(new DoubleTag(playerZ));
        player.put("Pos", pos);
        
        ListTag<FloatTag> rotation = new ListTag<>(FloatTag.class);
        rotation.add(new FloatTag(playerYaw));
        rotation.add(new FloatTag(playerPitch));
        player.put("Rotation", rotation);
        
        data.put("Player", player);
        
        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        
        return root;
    }
    
    /**
     * Load level data from NBT format.
     */
    public static LevelData fromNBT(CompoundTag root) {
        LevelData levelData = new LevelData();
        
        CompoundTag data = root.getCompoundTag("Data");
        if (data == null) {
            return levelData;
        }
        
        // World metadata
        if (data.containsKey("LevelName")) {
            levelData.worldName = data.getString("LevelName");
        }
        if (data.containsKey("LastPlayed")) {
            levelData.lastPlayed = data.getLong("LastPlayed");
        }
        if (data.containsKey("SpawnX")) {
            levelData.spawnX = data.getInt("SpawnX");
        }
        if (data.containsKey("SpawnY")) {
            levelData.spawnY = data.getInt("SpawnY");
        }
        if (data.containsKey("SpawnZ")) {
            levelData.spawnZ = data.getInt("SpawnZ");
        }
        if (data.containsKey("GameType")) {
            levelData.gameMode = data.getInt("GameType");
        }
        if (data.containsKey("hardcore")) {
            levelData.hardcore = data.getByte("hardcore") != 0;
        }
        if (data.containsKey("Difficulty")) {
            levelData.difficulty = data.getByte("Difficulty");
        }
        
        // Player data
        CompoundTag player = data.getCompoundTag("Player");
        if (player != null) {
            ListTag<?> pos = player.getListTag("Pos");
            if (pos != null && pos.size() >= 3) {
                levelData.playerX = pos.asDoubleTagList().get(0).asDouble();
                levelData.playerY = pos.asDoubleTagList().get(1).asDouble();
                levelData.playerZ = pos.asDoubleTagList().get(2).asDouble();
            }
            
            ListTag<?> rotation = player.getListTag("Rotation");
            if (rotation != null && rotation.size() >= 2) {
                levelData.playerYaw = rotation.asFloatTagList().get(0).asFloat();
                levelData.playerPitch = rotation.asFloatTagList().get(1).asFloat();
            }
        }
        
        return levelData;
    }
    
    /**
     * Save level data to a file.
     */
    public void save(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        
        try (OutputStream out = Files.newOutputStream(filePath);
             GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {
            new NBTSerializer(true).toStream(new NamedTag(null, toNBT()), gzipOut);
        }
    }
    
    /**
     * Load level data from a file.
     */
    public static LevelData load(Path filePath) throws IOException {
        try (InputStream in = Files.newInputStream(filePath);
             GZIPInputStream gzipIn = new GZIPInputStream(in)) {
            NamedTag namedTag = new NBTDeserializer(true).fromStream(gzipIn);
            if (namedTag != null && namedTag.getTag() instanceof CompoundTag) {
                return fromNBT((CompoundTag) namedTag.getTag());
            }
            throw new IOException("Invalid level.dat format");
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
