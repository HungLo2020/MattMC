package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.sodium.client.gui.SodiumOptionsGUI;
import net.sodium.client.gui.options.control.ControlElement;
import net.sodium.client.gui.widgets.FlatButtonWidget;

/** Diagnostic-only ordinary UI edits, Apply clicks, and observed projection changes. */
public final class GraphicsAuditGuiScale {
    private static final boolean ENABLED = "true".equalsIgnoreCase(System.getenv("MATTMC_CAPTURE_GUI_SCALE_APPLY"));
    private static final Sequence ACTIVE = new Sequence();
    private GraphicsAuditGuiScale() {}

    public static final class Sequence {
        public enum Action { EDIT_UP, EDIT_DOWN, APPLY, WAIT, CAPTURE }
        private int phase;
        private int frames;
        public Action afterPresentation(int setting, double actual) {
            if (phase == 0) {
                if (setting != 2 || actual != 2.0) throw new IllegalStateException("GUI scale fixture requires initial scale2");
                phase = 1;
                return Action.EDIT_UP;
            }
            if (phase == 1 || phase == 4) {
                phase++;
                return Action.APPLY;
            }
            if (phase == 2 || phase == 5) {
                int target = phase == 2 ? 3 : 2;
                if (setting != target || actual != target) { frames = 0; return Action.WAIT; }
                if (++frames < 2) return Action.WAIT;
                frames = 0;
                if (phase == 2) { phase = 4; return Action.EDIT_DOWN; }
                phase = 6;
            }
            return Action.CAPTURE;
        }
        public boolean complete() { return phase == 6; }
    }

    public static boolean prepareCapture(Minecraft minecraft) {
        if (!ENABLED) return true;
        if (!(minecraft.screen instanceof SodiumOptionsGUI screen)) throw new IllegalStateException("GUI scale Apply requires Video Settings");
        var action = ACTIVE.afterPresentation(minecraft.options.guiScale().get(), minecraft.getWindow().getGuiScale());
        if (action == Sequence.Action.EDIT_UP || action == Sequence.Action.EDIT_DOWN) {
            ControlElement<?> target = null;
            String label = Component.translatable("options.guiScale").getString();
            for (var child : screen.children()) {
                if (child instanceof ControlElement<?> control && control.getOption().getName().getString().equals(label)) {
                    if (target != null) throw new IllegalStateException("Ambiguous GUI scale control");
                    target = control;
                }
            }
            if (target == null) throw new IllegalStateException("Missing GUI scale control");
            var previousInput = minecraft.getLastInputType();
            boolean accepted;
            minecraft.setLastInputType(net.minecraft.client.InputType.KEYBOARD_ARROW);
            try {
                target.setFocused(true);
                accepted = target.keyPressed(new KeyEvent(action == Sequence.Action.EDIT_UP ? 262 : 263, 0, 0));
            } finally {
                target.setFocused(false);
                minecraft.setLastInputType(previousInput);
            }
            int expected = action == Sequence.Action.EDIT_UP ? 3 : 2;
            if (!accepted || !Integer.valueOf(expected).equals(target.getOption().getValue()))
                throw new IllegalStateException("GUI scale edit was not accepted");
        } else if (action == Sequence.Action.APPLY) {
            FlatButtonWidget target = null;
            String label = Component.translatable("sodium.options.buttons.apply").getString();
            for (var child : screen.children()) {
                if (child instanceof FlatButtonWidget button && button.getLabel().getString().equals(label)) {
                    if (target != null) throw new IllegalStateException("Ambiguous Apply control");
                    target = button;
                }
            }
            if (target == null) throw new IllegalStateException("Missing Apply control");
            var rect = target.getRectangle();
            if (!target.mouseClicked(new MouseButtonEvent(rect.left() + rect.width() / 2.0,
                rect.top() + rect.height() / 2.0, new MouseButtonInfo(0, 0)), false))
                throw new IllegalStateException("Apply click rejected");
        }
        return action == Sequence.Action.CAPTURE;
    }

    public static void captureState(com.google.gson.JsonObject state) {
        if (!ENABLED) return;
        state.addProperty("guiScaleApplyFixture", "2-3-2-apply-v1");
        state.addProperty("guiScaleApplyComplete", ACTIVE.complete());
    }
}
