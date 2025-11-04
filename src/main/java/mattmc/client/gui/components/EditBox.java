package mattmc.client.gui.components;

/**
 * Simple text field UI component for user input.
 */
public final class EditBox {
    public int x, y, w, h;
    private StringBuilder text;
    private boolean focused;
    private boolean hover;
    private final int maxLength;
    
    public EditBox(int x, int y, int w, int h, int maxLength) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.text = new StringBuilder();
        this.maxLength = maxLength;
        this.focused = false;
        this.hover = false;
    }
    
    public boolean contains(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    
    public void setHover(boolean v) { 
        hover = v; 
    }
    
    public boolean hover() { 
        return hover; 
    }
    
    public void setFocused(boolean v) { 
        focused = v; 
    }
    
    public boolean isFocused() { 
        return focused; 
    }
    
    public String getText() { 
        return text.toString(); 
    }
    
    public void setText(String str) {
        if (str == null) {
            text = new StringBuilder();
        } else {
            text = new StringBuilder(str.substring(0, Math.min(str.length(), maxLength)));
        }
    }
    
    public void appendChar(char c) {
        if (text.length() < maxLength) {
            text.append(c);
        }
    }
    
    public void backspace() {
        if (text.length() > 0) {
            text.deleteCharAt(text.length() - 1);
        }
    }
    
    public void clear() {
        text.setLength(0);
    }
}
