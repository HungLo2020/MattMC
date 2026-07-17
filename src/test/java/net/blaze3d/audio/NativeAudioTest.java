package net.blaze3d.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAudioTest {
	@Test
	void libraryDebugStringIsSafeBeforeInitialization() {
		Library library = new Library();
		assertEquals("Sounds: 0/0 + 0/0", library.getDebugString());
	}

	@Test
	void pendingChannelAttachmentIsNotStopped() {
		Channel channel = new Channel(0L);

		assertFalse(channel.stopped());

		channel.failAttachment();
		assertTrue(channel.stopped());
	}

	@Test
	void stoppingPendingChannelCancelsAttachmentWait() {
		Channel channel = new Channel(0L);

		channel.stop();

		assertTrue(channel.stopped());
	}
}
