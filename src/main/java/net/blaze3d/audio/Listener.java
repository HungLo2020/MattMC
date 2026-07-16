package net.blaze3d.audio;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class Listener {
	private ListenerTransform transform = ListenerTransform.INITIAL;
	private long deviceHandle;

	void setDeviceHandle(long deviceHandle) {
		this.deviceHandle = deviceHandle;
	}

	void clearDeviceHandle() {
		this.deviceHandle = 0L;
	}

	public void setTransform(ListenerTransform listenerTransform) {
		this.transform = listenerTransform;
		if (this.deviceHandle != 0L) {
			NativeAudio.listenerUpdate(this.deviceHandle, listenerTransform, 1.0F);
		}
	}

	public void reset() {
		this.transform = ListenerTransform.INITIAL;
		if (this.deviceHandle != 0L) {
			NativeAudio.listenerUpdate(this.deviceHandle, this.transform, 1.0F);
		}
	}

	public ListenerTransform getTransform() {
		return this.transform;
	}
}
