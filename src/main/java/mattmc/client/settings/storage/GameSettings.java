package mattmc.client.settings.storage;

/**
 * Data class holding all game settings.
 */
public class GameSettings {
    private int fpsCapValue;
    private int renderDistance;
    private boolean fullscreenEnabled;
    private int resolutionWidth;
    private int resolutionHeight;
    private boolean titleScreenBlurEnabled;
    private boolean menuScreenBlurEnabled;
    private int mipmapLevel;
    private int anisotropicFiltering;
    
    private final SettingsValidator validator = new SettingsValidator();
    
    public GameSettings() {
        // Initialize with defaults
        this.fpsCapValue = 0;
        this.renderDistance = 8;
        this.fullscreenEnabled = false;
        this.resolutionWidth = 1280;
        this.resolutionHeight = 720;
        this.titleScreenBlurEnabled = true;
        this.menuScreenBlurEnabled = true;
        this.mipmapLevel = 4;
        this.anisotropicFiltering = 4;
    }
    
    // FPS Cap
    public int getFpsCapValue() { return fpsCapValue; }
    public void setFpsCapValue(int value) { 
        this.fpsCapValue = validator.validateFpsCap(value); 
    }
    
    // Render Distance
    public int getRenderDistance() { return renderDistance; }
    public void setRenderDistance(int distance) { 
        this.renderDistance = validator.validateRenderDistance(distance); 
    }
    
    // Fullscreen
    public boolean isFullscreenEnabled() { return fullscreenEnabled; }
    public void setFullscreenEnabled(boolean enabled) { 
        this.fullscreenEnabled = enabled; 
    }
    
    // Resolution
    public int getResolutionWidth() { return resolutionWidth; }
    public int getResolutionHeight() { return resolutionHeight; }
    public void setResolution(int width, int height) {
        SettingsValidator.Resolution res = validator.validateResolution(width, height);
        this.resolutionWidth = res.width;
        this.resolutionHeight = res.height;
    }
    
    // Blur Effects
    public boolean isTitleScreenBlurEnabled() { return titleScreenBlurEnabled; }
    public void setTitleScreenBlurEnabled(boolean enabled) { 
        this.titleScreenBlurEnabled = enabled; 
    }
    
    public boolean isMenuScreenBlurEnabled() { return menuScreenBlurEnabled; }
    public void setMenuScreenBlurEnabled(boolean enabled) { 
        this.menuScreenBlurEnabled = enabled; 
    }
    
    // Mipmap Level
    public int getMipmapLevel() { return mipmapLevel; }
    public void setMipmapLevel(int level) { 
        this.mipmapLevel = validator.validateMipmapLevel(level); 
    }
    
    // Anisotropic Filtering
    public int getAnisotropicFiltering() { return anisotropicFiltering; }
    public void setAnisotropicFiltering(int level) { 
        this.anisotropicFiltering = validator.validateAnisotropicLevel(level); 
    }
}
