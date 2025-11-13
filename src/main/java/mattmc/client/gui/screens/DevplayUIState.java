package mattmc.client.gui.screens;

/**
 * Manages UI state for the Devplay screen.
 * This includes debug menu visibility, command overlay state, and feedback messages.
 */
public class DevplayUIState {
    // Debug menu toggle state
    private boolean debugMenuVisible = false;
    
    // Lighting debug overlay toggle state
    private boolean lightingDebugVisible = false;
    
    // Command overlay state
    private boolean commandOverlayVisible = false;
    private StringBuilder commandText = new StringBuilder("/");
    
    // Command feedback message (shown above hotbar area, independent of command overlay)
    private String commandFeedbackMessage = "";
    private double commandFeedbackDisplayTime = 0;
    
    // FPS tracking
    private double fps = 0.0;
    private int frameCount = 0;
    private double lastFpsUpdateTime;
    
    public DevplayUIState(double currentTime) {
        this.lastFpsUpdateTime = currentTime;
    }
    
    public boolean isDebugMenuVisible() {
        return debugMenuVisible;
    }
    
    public void toggleDebugMenu() {
        debugMenuVisible = !debugMenuVisible;
    }
    
    public boolean isLightingDebugVisible() {
        return lightingDebugVisible;
    }
    
    public void toggleLightingDebug() {
        lightingDebugVisible = !lightingDebugVisible;
    }
    
    public boolean isCommandOverlayVisible() {
        return commandOverlayVisible;
    }
    
    public void openCommandOverlay() {
        commandOverlayVisible = true;
        commandText = new StringBuilder("/");
    }
    
    public void closeCommandOverlay() {
        commandOverlayVisible = false;
    }
    
    public String getCommandText() {
        return commandText.toString();
    }
    
    public void appendToCommand(char c) {
        if (commandText.length() < 100) {
            commandText.append(c);
        }
    }
    
    public void deleteFromCommand() {
        if (commandText.length() > 1) { // Keep the initial "/"
            commandText.deleteCharAt(commandText.length() - 1);
        }
    }
    
    public void setCommandFeedback(String message, double displayTime) {
        this.commandFeedbackMessage = message;
        this.commandFeedbackDisplayTime = displayTime;
    }
    
    public String getCommandFeedbackMessage() {
        return commandFeedbackMessage;
    }
    
    public boolean hasCommandFeedback() {
        return commandFeedbackDisplayTime > 0;
    }
    
    public void updateFeedbackTimer(double deltaTime) {
        if (commandFeedbackDisplayTime > 0) {
            commandFeedbackDisplayTime -= deltaTime;
        }
    }
    
    public void updateFPS(double currentTime) {
        frameCount++;
        double timeSinceLastUpdate = currentTime - lastFpsUpdateTime;
        if (timeSinceLastUpdate >= 0.5) { // Update FPS every 0.5 seconds
            fps = frameCount / timeSinceLastUpdate;
            frameCount = 0;
            lastFpsUpdateTime = currentTime;
        }
    }
    
    public double getFPS() {
        return fps;
    }
}
