package net.minecraft.client.sounds;

import java.io.IOException;
import java.nio.ByteBuffer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface FiniteAudioStream extends AudioStream {
	ByteBuffer readAll() throws IOException;
}
