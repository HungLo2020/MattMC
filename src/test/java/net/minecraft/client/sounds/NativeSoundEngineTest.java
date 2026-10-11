package net.minecraft.client.sounds;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the Rust engine through the Java bindings on OpenAL's null device
 * (the test task sets `ALSOFT_DRIVERS=null`): world events, subtitles, ruled
 * tickables, music and the preview, checking each record layout end to end.
 */
class NativeSoundEngineTest {
	private static final int STARTED = 0;
	private static final int STARTED_SILENTLY = 1;
	private static final int NOT_STARTED = 2;
	private static final String SOUNDS = """
		{
		  "block.test": {"subtitle": "subtitles.block.test", "sounds": ["test/a"]},
		  "music.game": {"sounds": [{"name": "music/a", "stream": true}]},
		  "ui.button.click": {"sounds": ["ui/click"]}
		}
		""";

	@BeforeAll
	static void open() {
		long handle = NativeSoundPolicy.buildRegistry(
			1L << 41, List.of(new NativeSoundPolicy.Resource("minecraft", "test", SOUNDS)),
			"minecraft:sounds/test/a.ogg\nminecraft:sounds/music/a.ogg\nminecraft:sounds/ui/click.ogg"
		);
		NativeSoundPolicy.installRegistry(handle);
		assertNull(NativeSoundPolicy.engineInit(null, false));
	}

	@AfterAll
	static void close() {
		NativeSoundClient.subtitlesListen(false, false);
		NativeSoundPolicy.engineDestroy();
	}

	@AfterEach
	void stopEverything() {
		NativeSoundPolicy.stopAll();
		NativeSoundPolicy.takeLogs();
	}

	private static void tick() {
		NativeSoundPolicy.tickBegin(false);
		NativeSoundPolicy.tickEnd(false);
		NativeSoundPolicy.takeReleased();
		NativeSoundPolicy.takeDue();
		NativeSoundPolicy.deviceTick();
	}

	@Test
	void worldEventsPlayRecordSubtitlesAndWarnOnce() {
		NativeSoundClient.subtitlesListen(true, false);
		assertEquals(STARTED, NativeSoundClient.playEvent("minecraft:block.test", 4, 1.0F, 1.0F, 42L, 1.5, 2.0, -3.0, 0,
			NativeSoundClient.EVENT_FLAG_LINEAR));
		List<SoundSubtitle> frame = NativeSoundClient.subtitleFrame(1.5, 2.0, -3.0, 3000.0);
		assertEquals(1, frame.size());
		assertEquals(new SoundSubtitle("subtitles.block.test", 1.5, 2.0, -3.0, frame.get(0).ageMillis()), frame.get(0));

		assertEquals(NOT_STARTED, NativeSoundClient.playEvent("minecraft:nope", 4, 1.0F, 1.0F, 1L, 0.0, 0.0, 0.0, 0, 0));
		assertEquals(NOT_STARTED, NativeSoundClient.playEvent("minecraft:nope", 4, 1.0F, 1.0F, 1L, 0.0, 0.0, 0.0, 0, 0));
		assertEquals(List.of("W Unable to play unknown soundEvent: minecraft:nope"), NativeSoundPolicy.takeLogs());
		assertFalse(NativeSoundPolicy.warnOnce("minecraft:nope"), "Java and Rust share the warned set");

		// A delayed event is queued, not started.
		assertEquals(NOT_STARTED, NativeSoundClient.playEvent("minecraft:block.test", 4, 1.0F, 1.0F, 7L, 0.0, 0.0, 0.0, 3, 0));
		for (int i = 0; i < 4; i++) {
			tick();
		}
		List<SoundSubtitle> refreshed = NativeSoundClient.subtitleFrame(1.5, 2.0, -3.0, 3000.0);
		assertEquals(1, refreshed.size(), "the delayed play refreshed the same subtitle entry");
		assertEquals(new SoundSubtitle("subtitles.block.test", 1.5, 2.0, -3.0, refreshed.get(0).ageMillis()), refreshed.get(0),
			"the closest played-at position is still the first play's");
	}

	@Test
	void ruledInstancesTickInRustAndReportOutcomes() {
		NativeSoundPolicy.Rule bee = new NativeSoundPolicy.Rule(5, 0, 0.0F, 1.0F, 1.0F, 1.0F);
		int flags = NativeSoundPolicy.PLAY_FLAG_LINEAR | NativeSoundPolicy.PLAY_FLAG_LOOPING | NativeSoundPolicy.PLAY_FLAG_CAN_START_SILENT;
		int result = NativeSoundPolicy.play(77L, 4.0, 5.0, 6.0, 0.0F, 1.0F, 0, 16, 6, flags, bee, "minecraft:block.test",
			"minecraft:test/a") & 0xFF;
		assertEquals(STARTED_SILENTLY, result);
		NativeSoundPolicy.TickingBatch batch = new NativeSoundPolicy.TickingBatch();
		batch.addSample(77L, 0, 0.3F, 4.0, 5.0, 6.0);
		assertEquals(0, batch.flush().length);
		batch.addSample(77L, 1 << 6, 0.3F, 4.0, 5.0, 6.0);
		long[] outcomes = batch.flush();
		assertEquals(1, outcomes.length);
		assertEquals(77L | NativeSoundPolicy.OUTCOME_STOPPED | NativeSoundPolicy.OUTCOME_SWITCHED, outcomes[0]);
		batch.addPlain(77L, 0.5F, 1.0F, 4.0, 5.0, 6.0);
		assertEquals(0, batch.flush().length);
		assertTrue(NativeSoundPolicy.knows(77L));
	}

	@Test
	void musicStartsWithAToastAndStopsHidingIt() {
		NativeSoundClient.musicReset(2);
		assertEquals(NativeSoundClient.MUSIC_SHOW_TOAST, NativeSoundClient.musicStart("minecraft:music.game", 1.0F)
			& (NativeSoundClient.MUSIC_SHOW_TOAST | NativeSoundClient.MUSIC_HIDE_TOAST));
		assertTrue(NativeSoundClient.musicIsPlaying("minecraft:music.game"));
		assertEquals("minecraft:music/a", NativeSoundClient.musicCurrentSound());
		assertEquals(0, NativeSoundClient.musicToastIfNeeded());
		assertEquals(0, NativeSoundClient.musicTick("minecraft:music.game", 20, 600, false, 1.0F) & 0xFF);
		assertEquals(NativeSoundClient.MUSIC_HIDE_TOAST, NativeSoundClient.musicStop("minecraft:music.game"));
		assertNull(NativeSoundClient.musicCurrentSound());
		NativeSoundClient.musicSetFrequency(0, null, 0, 0);
	}

	@Test
	void previewPlaysTheCategorySound() {
		NativeSoundClient.preview(10, 0.5F);
		NativeSoundClient.preview(10, 0.5F);
		NativeSoundClient.preview(4, 0.5F);
		assertTrue(NativeSoundPolicy.takeLogs().stream().noneMatch(line -> line.startsWith("W Unable")));
	}
}
