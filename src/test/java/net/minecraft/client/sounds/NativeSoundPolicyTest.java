package net.minecraft.client.sounds;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSoundPolicyTest {
	private static final String SOUNDS = """
		{
		  "test.outer": {
		    "subtitle": "subtitles.test.outer",
		    "sounds": [
		      {"name": "test:a", "weight": 3},
		      {"name": "test:test.inner", "type": "event", "volume": 0.5, "weight": 2}
		    ]
		  },
		  "test.inner": {
		    "sounds": [
		      {"name": "test:b", "volume": 0.5, "pitch": 2.0, "stream": true},
		      {"name": "test:missing"}
		    ]
		  }
		}
		""";

	private static void install() {
		long handle = NativeSoundPolicy.buildRegistry(
			1L << 40, List.of(new NativeSoundPolicy.Resource("test", "test-pack", SOUNDS)), "test:sounds/a.ogg\ntest:sounds/b.ogg"
		);
		assertTrue(NativeSoundPolicy.registryWarnings(handle).stream().anyMatch(warning -> warning.contains("test:sounds/missing.ogg")));
		NativeSoundPolicy.installRegistry(handle);
	}

	@Test
	void resolutionRequestsTheCallersDrawsInOrder() {
		install();
		List<Integer> bounds = new ArrayList<>();
		int[] answers = {3, 0};
		NativeSoundPolicy.Resolved resolved = NativeSoundPolicy.resolve("test:test.outer", bound -> {
			bounds.add(bound);
			return answers[bounds.size() - 1];
		});

		// A nested entry weighs its target event's total, not its own weight: the
		// inner event dropped its missing file, so the outer event weighs 3 + 1.
		assertEquals(List.of(4, 1), bounds);
		assertEquals(NativeSoundPolicy.RESOLVE_SOUND, resolved.kind());
		assertEquals("test:b", resolved.location());
		assertEquals(0.25F, resolved.volume());
		assertEquals(2.0F, resolved.pitch());
		assertTrue((resolved.flags() & NativeSoundPolicy.RESOLVED_STREAM) != 0);
	}

	@Test
	void directEntryNeedsOneDraw() {
		install();
		NativeSoundPolicy.Resolved resolved = NativeSoundPolicy.resolve("test:test.outer", bound -> 2);

		assertEquals("test:a", resolved.location());
		assertEquals(1.0F, resolved.volume());
	}

	@Test
	void eventMetadataComesFromTheInstalledRegistry() {
		install();

		assertEquals("subtitles.test.outer", NativeSoundPolicy.lookupEvent("test:test.outer").subtitle());
		assertNull(NativeSoundPolicy.lookupEvent("test:test.inner").subtitle());
		assertNull(NativeSoundPolicy.lookupEvent("test:absent"));
		assertEquals(4, NativeSoundPolicy.eventWeight("test:test.outer"));
		assertEquals(NativeSoundPolicy.RESOLVE_UNKNOWN, NativeSoundPolicy.resolve("test:absent", bound -> 0).kind());
		assertTrue(NativeSoundPolicy.eventNames().contains("test:test.inner"));
	}
}
