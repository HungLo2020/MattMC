package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.minecraft.client.dev.GraphicsAuditMenuFixture.Decision.*;

class GraphicsAuditMenuFixtureTest {
    @Test
    void videoMustBeOpenedAndPresentedNotMistakenForOptions() {
        var fixture = new GraphicsAuditMenuFixture("video");
        assertEquals(WAIT, fixture.afterPresentation("SodiumOptionsGUI"));
        assertEquals(OPEN_VIDEO, fixture.afterPresentation("TitleScreen"));
        assertEquals(WAIT, fixture.afterPresentation("OptionsScreen"));
        assertEquals(WAIT, fixture.afterPresentation("SodiumOptionsGUI"));
        assertEquals(WAIT, fixture.afterPresentation("OptionsScreen"));
        assertEquals(WAIT, fixture.afterPresentation("SodiumOptionsGUI"));
        assertEquals(CAPTURE, fixture.afterPresentation("SodiumOptionsGUI"));
    }

    @Test
    void maximizeMustBeObservedAndRestoredBeforeCapture() {
        var fixture = new GraphicsAuditMenuFixture.MaximizeSequence();
        var wait = GraphicsAuditMenuFixture.MaximizeSequence.Action.WAIT;
        assertEquals(GraphicsAuditMenuFixture.MaximizeSequence.Action.MAXIMIZE,
            fixture.afterPresentation(false, 1280, 720));
        assertEquals(wait, fixture.afterPresentation(false, 1920, 1056));
        assertEquals(wait, fixture.afterPresentation(true, 1900, 1056));
        assertEquals(wait, fixture.afterPresentation(true, 1920, 1056));
        assertEquals(GraphicsAuditMenuFixture.MaximizeSequence.Action.RESTORE,
            fixture.afterPresentation(true, 1920, 1056));
        assertEquals(wait, fixture.afterPresentation(true, 1280, 720));
        assertEquals(GraphicsAuditMenuFixture.MaximizeSequence.Action.SET_FINAL_SIZE,
            fixture.afterPresentation(false, 1280, 720));
        assertEquals(wait, fixture.afterPresentation(false, 1280, 720));
        assertEquals(wait, fixture.afterPresentation(false, 1279, 720));
        assertEquals(wait, fixture.afterPresentation(false, 1280, 720));
        assertEquals(GraphicsAuditMenuFixture.MaximizeSequence.Action.CAPTURE,
            fixture.afterPresentation(false, 1280, 720));
        assertTrue(fixture.complete());
        assertEquals(1920, fixture.observedWidth());
        assertEquals(1056, fixture.observedHeight());
    }

    @Test
    void resizeFixtureRequiresTwoPresentationsAtEveryRequestedExtent() {
        var fixture = new GraphicsAuditMenuFixture.ResizeSequence();
        assertArrayEquals(new int[] {640, 480}, fixture.afterPresentation(1280, 720));
        assertNull(fixture.afterPresentation(1280, 720));
        assertNull(fixture.afterPresentation(640, 480));
        assertNull(fixture.afterPresentation(639, 480)); // invalidates stability
        assertNull(fixture.afterPresentation(640, 480));
        assertArrayEquals(new int[] {1600, 900}, fixture.afterPresentation(640, 480));
        assertEquals(1, fixture.completedSteps());
        assertNull(fixture.afterPresentation(1600, 900));
        assertArrayEquals(new int[] {1280, 720}, fixture.afterPresentation(1600, 900));
        assertFalse(fixture.complete());
        assertNull(fixture.afterPresentation(1280, 720));
        assertFalse(fixture.complete());
        assertNull(fixture.afterPresentation(1280, 720));
        assertTrue(fixture.complete());
        assertEquals(3, fixture.completedSteps());
    }

    @Test
    void ordinaryTitleCaptureDoesNotNavigate() {
        var fixture = new GraphicsAuditMenuFixture(null);
        assertEquals(CAPTURE, fixture.afterPresentation("TitleScreen"));
        assertEquals(WAIT, fixture.afterPresentation("OptionsScreen"));
    }

    @Test
    void optionsMustBeOpenedAndActuallyPresentedTwice() {
        var fixture = new GraphicsAuditMenuFixture("options");
        assertEquals(WAIT, fixture.afterPresentation("OptionsScreen"));
        assertEquals(OPEN_OPTIONS, fixture.afterPresentation("TitleScreen"));
        assertEquals(WAIT, fixture.afterPresentation("TitleScreen"));
        assertEquals(WAIT, fixture.afterPresentation("OptionsScreen"));
        assertEquals(WAIT, fixture.afterPresentation("OtherScreen"));
        assertEquals(WAIT, fixture.afterPresentation("OptionsScreen"));
        assertEquals(CAPTURE, fixture.afterPresentation("OptionsScreen"));
    }

    @Test
    void unknownFixtureCannotSilentlyCaptureTitle() {
        assertThrows(IllegalArgumentException.class, () -> new GraphicsAuditMenuFixture("typo"));
    }
}
