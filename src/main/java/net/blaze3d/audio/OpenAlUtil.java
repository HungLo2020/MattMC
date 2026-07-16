package net.blaze3d.audio;

import javax.sound.sampled.AudioFormat;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class OpenAlUtil {
	static int audioFormatToOpenAl(AudioFormat audioFormat) {
		return NativeAudio.audioFormatToOpenAl(audioFormat);
	}
}
