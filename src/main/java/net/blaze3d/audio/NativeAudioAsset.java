package net.blaze3d.audio;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public final class NativeAudioAsset implements AutoCloseable {
	private final long generation;
	private long handle;

	private NativeAudioAsset(long handle, long generation) {
		this.handle = handle;
		this.generation = generation;
	}

	public static NativeAudioAsset create(byte[] encoded, String debugName, long generation) {
		return new NativeAudioAsset(NativeAudio.assetCreate(encoded, debugName, generation), generation);
	}

	public static void destroyGeneration(long generation) {
		NativeAudio.assetDestroyGeneration(generation);
	}

	public long handleForPlayback() {
		return this.handle;
	}

	public long generation() {
		return this.generation;
	}

	@Override
	public void close() {
		if (this.handle != 0L) {
			NativeAudio.assetDestroy(this.handle);
			this.handle = 0L;
		}
	}
}
