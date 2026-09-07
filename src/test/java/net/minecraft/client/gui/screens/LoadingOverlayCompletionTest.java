package net.minecraft.client.gui.screens;

import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoadingOverlayCompletionTest {
    @Test
    void fadingCannotDiscardAnUnfinishedReloadOrSuppressItsCallback() throws Exception {
        withMocks(() -> {
            Minecraft game = mock(Minecraft.class);
            ReloadInstance reload = mock(ReloadInstance.class);
            var completions = new ArrayList<Optional<Throwable>>();
            LoadingOverlay overlay = new LoadingOverlay(game, reload, completions::add, false);
            var fade = LoadingOverlay.class.getDeclaredField("fadeOutStart");
            fade.setAccessible(true);
            fade.setLong(overlay, 0L); // render already started fading before reload finished
            overlay.tick();
            verify(game, never()).setOverlay(null);
            assertTrue(completions.isEmpty());
            when(reload.isDone()).thenReturn(true);
            overlay.tick();
            overlay.tick();
            assertEquals(1, completions.size());
            assertTrue(completions.getFirst().isEmpty());
            verify(reload, times(1)).checkExceptions();
        });
    }

    @Test
    void reloadFailureIsDeliveredExactlyOnceEvenAfterFadeStarted() throws Exception {
        withMocks(() -> {
            Minecraft game = mock(Minecraft.class);
            ReloadInstance reload = mock(ReloadInstance.class);
            RuntimeException failure = new RuntimeException("reload failed");
            when(reload.isDone()).thenReturn(true);
            doThrow(failure).when(reload).checkExceptions();
            var completions = new ArrayList<Optional<Throwable>>();
            LoadingOverlay overlay = new LoadingOverlay(game, reload, completions::add, false);
            var fade = LoadingOverlay.class.getDeclaredField("fadeOutStart");
            fade.setAccessible(true);
            fade.setLong(overlay, 0L);
            overlay.tick();
            overlay.tick();
            assertEquals(1, completions.size());
            assertSame(failure, completions.getFirst().orElseThrow());
        });
    }

    private interface Checked { void run() throws Exception; }
    private static void withMocks(Checked test) throws Exception {
        String old = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        try { test.run(); }
        finally {
            if (old == null) System.clearProperty("net.bytebuddy.experimental");
            else System.setProperty("net.bytebuddy.experimental", old);
        }
    }
}
