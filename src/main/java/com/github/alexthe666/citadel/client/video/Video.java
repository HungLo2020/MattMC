package com.github.alexthe666.citadel.client.video;

import com.github.alexthe666.citadel.client.texture.VideoFrameTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Citadel: Video playback feature disabled for 1.21
// This is an optional feature that requires external JCodec library
// To enable, add JCodec dependency and implement proper video decoding
public class Video {

    public static final Logger LOGGER = LogManager.getLogger("citadel-video");

    private boolean paused;
    private boolean repeat;
    private boolean muted;
    private String url;
    private ResourceLocation resourceLocation;
    private VideoFrameTexture texture;

    public Video(VideoFrameTexture texture, String url, double framesPerSecond, boolean muted) {
        this.texture = texture;
        this.url = url;
        this.muted = muted;
        LOGGER.warn("Video playback is disabled - JCodec dependency not available in this build");
    }

    public Video(VideoFrameTexture texture, ResourceLocation resourceLocation, double framesPerSecond, boolean muted) {
        this.texture = texture;
        this.resourceLocation = resourceLocation;
        this.muted = muted;
        LOGGER.warn("Video playback is disabled - JCodec dependency not available in this build");
    }

    public void update() {
        // Citadel: Video playback disabled - would require JCodec implementation
    }

    public void onStart() {
        // Citadel: Video playback disabled
    }

    public void stop() {
        // Citadel: Video playback disabled
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public boolean isMuted() {
        return muted;
    }

    public String getUrl() {
        return url;
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
