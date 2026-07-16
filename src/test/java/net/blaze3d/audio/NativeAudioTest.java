package net.blaze3d.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeAudioTest {
	@Test
	void audioFormatMappingMatchesOpenAlConstants() {
		assertEquals(0x1100, NativeAudio.audioFormatToOpenAl(format(1, 8)));
		assertEquals(0x1101, NativeAudio.audioFormatToOpenAl(format(1, 16)));
		assertEquals(0x1102, NativeAudio.audioFormatToOpenAl(format(2, 8)));
		assertEquals(0x1103, NativeAudio.audioFormatToOpenAl(format(2, 16)));
	}

	@Test
	void unsupportedAudioFormatsFailBeforeOpenAlUse() {
		assertThrows(IllegalStateException.class, () -> NativeAudio.audioFormatToOpenAl(format(6, 16)));
		assertThrows(IllegalStateException.class, () -> NativeAudio.audioFormatToOpenAl(format(2, 24)));
	}

	@Test
	void libraryDebugStringIsSafeBeforeInitialization() {
		Library library = new Library();
		assertEquals("Sounds: 0/0 + 0/0", library.getDebugString());
	}

	private static AudioFormat format(int channels, int bits) {
		return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44_100.0F, bits, channels, channels * bits / 8, 44_100.0F, false);
	}
}
