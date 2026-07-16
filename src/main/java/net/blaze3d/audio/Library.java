package net.blaze3d.audio;

import net.logging.LogUtils;

import java.util.List;
import java.util.Locale;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Library {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final long NO_DEVICE = 0L;
	private long currentDevice;
	@Nullable
	private String defaultDeviceName;
	private final Listener listener = new Listener();

	public Library() {
		this.defaultDeviceName = getDefaultDeviceName();
	}

	public void init(@Nullable String string, boolean bl) {
		this.currentDevice = NativeAudio.deviceCreate(string, bl);
		this.listener.setDeviceHandle(this.currentDevice);
		this.listener.reset();
		LOGGER.info("OpenAL initialized on device {}", this.getCurrentDeviceName());
	}

	@Nullable
	public static String getDefaultDeviceName() {
		String name = NativeAudio.defaultDeviceName();
		return name.isEmpty() ? null : name;
	}

	public String getCurrentDeviceName() {
		if (this.currentDevice == NO_DEVICE) {
			return "Unknown";
		}

		String name = NativeAudio.currentDeviceName(this.currentDevice);
		return name.isEmpty() ? "Unknown" : name;
	}

	public synchronized boolean hasDefaultDeviceChanged() {
		if (this.currentDevice == NO_DEVICE) {
			String string = getDefaultDeviceName();
			if (java.util.Objects.equals(this.defaultDeviceName, string)) {
				return false;
			}

			this.defaultDeviceName = string;
			return true;
		}

		boolean changed = NativeAudio.deviceHasDefaultChanged(this.currentDevice);
		if (changed) {
			this.defaultDeviceName = getDefaultDeviceName();
		}

		return changed;
	}

	public void cleanup() {
		this.listener.clearDeviceHandle();
		if (this.currentDevice != NO_DEVICE) {
			NativeAudio.deviceDestroy(this.currentDevice);
			this.currentDevice = NO_DEVICE;
		}
	}

	public Listener getListener() {
		return this.listener;
	}

	@Nullable
	public Channel acquireChannel(Library.Pool pool) {
		if (this.currentDevice == NO_DEVICE) {
			return null;
		}

		return new Channel(this.currentDevice, pool);
	}

	public void releaseChannel(Channel channel) {
		channel.destroy();
	}

	public String getDebugString() {
		if (this.currentDevice == NO_DEVICE) {
			return "Sounds: 0/0 + 0/0";
		}

		int[] counts = NativeAudio.devicePoolCounts(this.currentDevice);
		return String.format(Locale.ROOT, "Sounds: %d/%d + %d/%d", counts[0], counts[1], counts[2], counts[3]);
	}

	public List<String> getAvailableSoundDevices() {
		return NativeAudio.availableDevices();
	}

	public boolean isCurrentDeviceDisconnected() {
		return this.currentDevice != NO_DEVICE && NativeAudio.deviceIsDisconnected(this.currentDevice);
	}

	@Environment(EnvType.CLIENT)
	public static enum Pool {
		STATIC,
		STREAMING
	}
}
