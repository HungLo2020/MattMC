package mattmc.client.gui.overlay;

/**
 * Manages the command input overlay UI.
 * Handles command text input and feedback message display.
 */
public class CommandOverlay {
    private boolean visible = false;
    private StringBuilder commandText = new StringBuilder("/");
    private String feedbackMessage = "";
    private double feedbackDisplayTime = 0;
    
    /**
     * Show the command overlay.
     */
    public void show() {
        this.visible = true;
        this.commandText = new StringBuilder("/");
    }
    
    /**
     * Hide the command overlay.
     */
    public void hide() {
        this.visible = false;
    }
    
    /**
     * Check if the overlay is visible.
     */
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * Append a character to the command text.
     */
    public void appendChar(char c) {
        if (visible) {
            commandText.append(c);
        }
    }
    
    /**
     * Delete the last character from the command text.
     */
    public void deleteLastChar() {
        if (visible && commandText.length() > 0) {
            commandText.deleteCharAt(commandText.length() - 1);
        }
    }
    
    /**
     * Get the current command text.
     */
    public String getCommandText() {
        return commandText.toString();
    }
    
    /**
     * Set a feedback message to display.
     */
    public void setFeedback(String message, double displayTime) {
        this.feedbackMessage = message;
        this.feedbackDisplayTime = displayTime;
    }
    
    /**
     * Get the current feedback message.
     */
    public String getFeedbackMessage() {
        return feedbackMessage;
    }
    
    /**
     * Get the remaining display time for the feedback message.
     */
    public double getFeedbackDisplayTime() {
        return feedbackDisplayTime;
    }
    
    /**
     * Update the overlay state (decrements feedback display time).
     * @param deltaTime Time elapsed since last tick in seconds
     */
    public void tick(double deltaTime) {
        if (feedbackDisplayTime > 0) {
            feedbackDisplayTime -= deltaTime;
            if (feedbackDisplayTime <= 0) {
                feedbackMessage = "";
            }
        }
    }
    
    /**
     * Clear the command text.
     */
    public void clearCommand() {
        commandText = new StringBuilder("/");
    }
}
