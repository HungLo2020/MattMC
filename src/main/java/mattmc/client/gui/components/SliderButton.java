package mattmc.client.gui.components;

/**
 * A slider button for adjusting values like volume.
 * Similar to Minecraft's slider options in the sound settings.
 * <p>
 * The slider displays a label and a draggable handle that can
 * be adjusted from 0% to 100%.
 */
public final class SliderButton {

    private final String label;
    private int x, y, w, h;
    private float value;  // 0.0 - 1.0
    private boolean hover;
    private boolean dragging;

    /** Callback for when the value changes */
    private ValueChangeListener onValueChange;

    /**
     * Create a slider button.
     * @param label The label to display
     * @param x X position
     * @param y Y position
     * @param w Width
     * @param h Height
     * @param initialValue Initial value (0.0 - 1.0)
     */
    public SliderButton(String label, int x, int y, int w, int h, float initialValue) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.value = clamp(initialValue, 0.0f, 1.0f);
    }

    /**
     * Get the label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Get the display text (label + percentage).
     */
    public String getDisplayText() {
        int percent = Math.round(value * 100);
        if (percent == 0) {
            return label + ": OFF";
        }
        return label + ": " + percent + "%";
    }

    /**
     * Get the current value (0.0 - 1.0).
     */
    public float getValue() {
        return value;
    }

    /**
     * Set the value (0.0 - 1.0).
     */
    public void setValue(float value) {
        float oldValue = this.value;
        this.value = clamp(value, 0.0f, 1.0f);
        if (oldValue != this.value && onValueChange != null) {
            onValueChange.onValueChanged(this, this.value);
        }
    }

    /**
     * Get the value as a percentage (0 - 100).
     */
    public int getPercentage() {
        return Math.round(value * 100);
    }

    /**
     * Set the value from a percentage (0 - 100).
     */
    public void setPercentage(int percent) {
        setValue(percent / 100.0f);
    }

    // Position and size accessors
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    /**
     * Check if the given coordinates are within this slider.
     */
    public boolean contains(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /**
     * Check if the slider is being hovered.
     */
    public boolean isHover() {
        return hover;
    }

    /**
     * Set the hover state.
     */
    public void setHover(boolean hover) {
        this.hover = hover;
    }

    /**
     * Check if the slider is being dragged.
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * Set the dragging state.
     */
    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    /**
     * Update the value based on mouse X position while dragging.
     * @param mouseX The current mouse X position
     */
    public void updateFromMouse(double mouseX) {
        if (dragging) {
            float newValue = (float)((mouseX - x) / w);
            setValue(newValue);
        }
    }

    /**
     * Handle mouse press.
     * @param mx Mouse X
     * @param my Mouse Y
     * @return true if the press was handled
     */
    public boolean onMousePressed(double mx, double my) {
        if (contains(mx, my)) {
            dragging = true;
            updateFromMouse(mx);
            return true;
        }
        return false;
    }

    /**
     * Handle mouse release.
     */
    public void onMouseReleased() {
        dragging = false;
    }

    /**
     * Set the value change listener.
     */
    public void setOnValueChange(ValueChangeListener listener) {
        this.onValueChange = listener;
    }

    /**
     * Get the X position of the slider handle (knob) center.
     */
    public float getHandleX() {
        return x + (w * value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Callback interface for value changes.
     */
    @FunctionalInterface
    public interface ValueChangeListener {
        void onValueChanged(SliderButton slider, float newValue);
    }
}
