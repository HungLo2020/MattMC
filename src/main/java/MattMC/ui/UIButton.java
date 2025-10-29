package MattMC.ui;

public final class UIButton {
    public final String label;
    public int x, y, w, h;
    private boolean hover;

    public UIButton(String label, int x, int y, int w, int h) {
        this.label = label; this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public void setHover(boolean v) { hover = v; }
    public boolean hover() { return hover; }
}
