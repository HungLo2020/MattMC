package net.minecraft.client.renderer.shader.uniform;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Provides world state uniforms for shaders.
 * Updates time, weather, celestial information, etc.
 */
@Environment(EnvType.CLIENT)
public class WorldStateUniforms {
    private final Minecraft minecraft;
    
    public WorldStateUniforms(Minecraft minecraft) {
        this.minecraft = minecraft;
    }
    
    /**
     * Updates all world state uniforms.
     */
    public void updateUniforms(UniformManager uniformManager) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        
        // Time uniforms
        long worldTime = level.getDayTime();
        float worldTimeFloat = (float)(worldTime % 24000L);
        uniformManager.setFloat("worldTime", worldTimeFloat);
        uniformManager.setInt("worldDay", (int)(worldTime / 24000L));
        
        // Frame time counter (increases every frame)
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float frameTimeCounter = (float)worldTime + partialTick;
        uniformManager.setFloat("frameTimeCounter", frameTimeCounter);
        
        // Celestial angle (sun/moon position)
        float celestialAngle = level.getTimeOfDay(partialTick);
        uniformManager.setFloat("sunAngle", celestialAngle);
        uniformManager.setFloat("moonAngle", celestialAngle);
        
        // Weather
        float rainStrength = level.getRainLevel(partialTick);
        float thunderStrength = level.getThunderLevel(partialTick);
        uniformManager.setFloat("rainStrength", rainStrength);
        uniformManager.setFloat("wetness", rainStrength); // Alias
        uniformManager.setFloat("thunderStrength", thunderStrength);
        
        // Sky/fog color
        float skyBrightness = level.getSkyDarken(partialTick);
        uniformManager.setFloat("skyBrightness", 1.0f - skyBrightness);
        
        // Dimension info
        boolean isNether = level.dimension() == net.minecraft.world.level.Level.NETHER;
        boolean isEnd = level.dimension() == net.minecraft.world.level.Level.END;
        uniformManager.setBoolean("isNether", isNether);
        uniformManager.setBoolean("isEnd", isEnd);
        uniformManager.setBoolean("isOverworld", !isNether && !isEnd);
    }
}
