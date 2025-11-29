package mattmc.client.sounds;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.ALUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OpenAL library wrapper, mirroring Minecraft's Library class.
 * <p>
 * Manages the OpenAL device and context lifecycle, and provides
 * channel pools for sound sources.
 */
public final class OpenALLibrary {

    private static final Logger logger = LoggerFactory.getLogger(OpenALLibrary.class);

    private static final int DEFAULT_CHANNEL_COUNT = 30;
    private static final int MAX_STREAMING_CHANNELS = 8;
    private static final int MAX_STATIC_CHANNELS = 255;

    private long device;
    private long context;
    private boolean initialized;
    private final Listener listener = new Listener();

    // Channel pools
    private final Set<Channel> staticChannels = new HashSet<>();
    private final Set<Channel> streamingChannels = new HashSet<>();
    private int maxStaticChannels = 22;
    private int maxStreamingChannels = 8;

    /**
     * Initialize the OpenAL library.
     * @param deviceName Specific device name to use, or null for default
     * @return true if initialization succeeded
     */
    public boolean init(String deviceName) {
        if (initialized) {
            logger.warn("OpenAL library already initialized");
            return true;
        }

        try {
            // Open device
            device = ALC10.alcOpenDevice(deviceName);
            if (device == 0L) {
                logger.error("Failed to open OpenAL device");
                return false;
            }

            // Check capabilities
            ALCCapabilities alcCapabilities = ALC.createCapabilities(device);
            if (OpenALUtils.checkALCError(device, "Create capabilities")) {
                cleanup();
                return false;
            }

            if (!alcCapabilities.OpenALC11) {
                logger.error("OpenAL 1.1 not supported");
                cleanup();
                return false;
            }

            // Create context
            context = ALC10.alcCreateContext(device, (int[]) null);
            if (context == 0L) {
                logger.error("Failed to create OpenAL context");
                cleanup();
                return false;
            }

            ALC10.alcMakeContextCurrent(context);
            if (OpenALUtils.checkALCError(device, "Make context current")) {
                cleanup();
                return false;
            }

            // Create AL capabilities
            ALCapabilities alCapabilities = AL.createCapabilities(alcCapabilities);
            OpenALUtils.checkALError("Create AL capabilities");

            // Calculate channel counts
            int totalChannels = getChannelCount();
            maxStreamingChannels = Math.min(Math.max(2, (int) Math.sqrt(totalChannels)), MAX_STREAMING_CHANNELS);
            maxStaticChannels = Math.min(totalChannels - maxStreamingChannels, MAX_STATIC_CHANNELS);

            // Reset listener to default state
            listener.reset();

            initialized = true;
            logger.info("OpenAL initialized on device: {}", getCurrentDeviceName());
            logger.info("Channels: {} static, {} streaming", maxStaticChannels, maxStreamingChannels);
            return true;

        } catch (Exception e) {
            logger.error("Failed to initialize OpenAL", e);
            cleanup();
            return false;
        }
    }

    /**
     * Get the number of available mono sources (channels).
     */
    private int getChannelCount() {
        try {
            int attrSize = ALC10.alcGetInteger(device, ALC10.ALC_ATTRIBUTES_SIZE);
            if (attrSize > 0) {
                int[] attrs = new int[attrSize];
                ALC10.alcGetIntegerv(device, ALC10.ALC_ALL_ATTRIBUTES, attrs);
                for (int i = 0; i < attrSize - 1; i += 2) {
                    if (attrs[i] == ALC11.ALC_MONO_SOURCES) {
                        return attrs[i + 1];
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to query channel count", e);
        }
        return DEFAULT_CHANNEL_COUNT;
    }

    /**
     * Clean up OpenAL resources.
     */
    public void cleanup() {
        // Release all channels
        for (Channel channel : staticChannels) {
            channel.destroy();
        }
        staticChannels.clear();

        for (Channel channel : streamingChannels) {
            channel.destroy();
        }
        streamingChannels.clear();

        // Destroy context
        if (context != 0L) {
            ALC10.alcMakeContextCurrent(0L);
            ALC10.alcDestroyContext(context);
            context = 0L;
        }

        // Close device
        if (device != 0L) {
            ALC10.alcCloseDevice(device);
            device = 0L;
        }

        initialized = false;
        logger.info("OpenAL library cleaned up");
    }

    /**
     * Check if the library is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the listener for this audio system.
     */
    public Listener getListener() {
        return listener;
    }

    /**
     * Acquire a static channel for non-streaming sounds.
     * @return A channel, or null if none available
     */
    public Channel acquireStaticChannel() {
        if (!initialized) return null;
        
        if (staticChannels.size() >= maxStaticChannels) {
            logger.debug("Static channel pool exhausted ({} max)", maxStaticChannels);
            return null;
        }

        Channel channel = Channel.create();
        if (channel != null) {
            staticChannels.add(channel);
        }
        return channel;
    }

    /**
     * Acquire a streaming channel for music/long sounds.
     * @return A channel, or null if none available
     */
    public Channel acquireStreamingChannel() {
        if (!initialized) return null;
        
        if (streamingChannels.size() >= maxStreamingChannels) {
            logger.debug("Streaming channel pool exhausted ({} max)", maxStreamingChannels);
            return null;
        }

        Channel channel = Channel.create();
        if (channel != null) {
            streamingChannels.add(channel);
        }
        return channel;
    }

    /**
     * Release a channel back to the pool.
     */
    public void releaseChannel(Channel channel) {
        if (channel == null) return;
        
        channel.destroy();
        staticChannels.remove(channel);
        streamingChannels.remove(channel);
    }

    /**
     * Get the name of the current audio device.
     */
    public String getCurrentDeviceName() {
        if (device == 0L) return "None";
        
        String name = ALC10.alcGetString(device, ALC10.ALC_DEVICE_SPECIFIER);
        return name != null ? name : "Unknown";
    }

    /**
     * Get a list of available audio devices.
     */
    public List<String> getAvailableSoundDevices() {
        try {
            List<String> devices = ALUtil.getStringList(0L, ALC11.ALC_ALL_DEVICES_SPECIFIER);
            return devices != null ? devices : Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Failed to enumerate audio devices", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get a debug string with current channel usage.
     */
    public String getDebugString() {
        return String.format("Sounds: %d/%d + %d/%d",
            staticChannels.size(), maxStaticChannels,
            streamingChannels.size(), maxStreamingChannels);
    }
}
