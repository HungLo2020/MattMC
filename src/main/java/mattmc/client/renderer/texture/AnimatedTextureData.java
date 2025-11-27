package mattmc.client.renderer.texture;

import mattmc.client.resources.metadata.animation.AnimationMetadataSection;
import mattmc.client.resources.metadata.animation.FrameSize;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds data for an animated texture, including all frames and animation metadata.
 * Used by the texture animation system to cycle through frames during rendering.
 */
public class AnimatedTextureData {
    private final String texturePath;
    private final AnimationMetadataSection metadata;
    private final List<BufferedImage> frames;
    private final int frameWidth;
    private final int frameHeight;
    private final int frameCount;
    
    // Animation state
    private int currentFrameIndex;
    private int subFrameTicks;
    private int currentFrame;
    
    /**
     * Create animated texture data from a source image and metadata.
     * 
     * @param texturePath the path to the texture file
     * @param sourceImage the full texture image (vertical strip of frames)
     * @param metadata the animation metadata from .mcmeta file
     */
    public AnimatedTextureData(String texturePath, BufferedImage sourceImage, AnimationMetadataSection metadata) {
        this.texturePath = texturePath;
        this.metadata = metadata;
        
        // Calculate frame size
        FrameSize frameSize = metadata.calculateFrameSize(sourceImage.getWidth(), sourceImage.getHeight());
        this.frameWidth = frameSize.width();
        this.frameHeight = frameSize.height();
        
        // Validate frame dimensions to prevent division by zero
        if (this.frameWidth <= 0 || this.frameHeight <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive: " + frameWidth + "x" + frameHeight);
        }
        
        // Calculate frame count (number of frames that fit in the source image)
        int framesWide = sourceImage.getWidth() / frameWidth;
        int framesHigh = sourceImage.getHeight() / frameHeight;
        this.frameCount = framesWide * framesHigh;
        
        // Extract individual frames
        this.frames = new ArrayList<>(frameCount);
        for (int y = 0; y < framesHigh; y++) {
            for (int x = 0; x < framesWide; x++) {
                BufferedImage frame = sourceImage.getSubimage(
                        x * frameWidth, 
                        y * frameHeight, 
                        frameWidth, 
                        frameHeight
                );
                frames.add(frame);
            }
        }
        
        // Initialize animation state
        this.currentFrameIndex = 0;
        this.subFrameTicks = 0;
        this.currentFrame = getFrameIndex(0);
    }
    
    /**
     * Get the first frame for atlas packing.
     */
    public BufferedImage getFirstFrame() {
        return frames.isEmpty() ? null : frames.get(getFrameIndex(0));
    }
    
    /**
     * Get a specific frame by index.
     */
    public BufferedImage getFrame(int index) {
        if (index < 0 || index >= frames.size()) {
            return frames.isEmpty() ? null : frames.get(0);
        }
        return frames.get(index);
    }
    
    /**
     * Get the current animation frame image.
     */
    public BufferedImage getCurrentFrame() {
        return getFrame(currentFrame);
    }
    
    /**
     * Get the frame index from the animation sequence.
     * If explicit frames are defined, use those; otherwise use sequential order.
     */
    private int getFrameIndex(int sequenceIndex) {
        List<mattmc.client.resources.metadata.animation.AnimationFrame> animFrames = metadata.getFrames();
        if (!animFrames.isEmpty()) {
            int idx = sequenceIndex % animFrames.size();
            return animFrames.get(idx).getIndex();
        }
        // No explicit frames, use sequential order
        return sequenceIndex % frameCount;
    }
    
    /**
     * Get the frame time for a given sequence index.
     */
    private int getFrameTime(int sequenceIndex) {
        List<mattmc.client.resources.metadata.animation.AnimationFrame> animFrames = metadata.getFrames();
        if (!animFrames.isEmpty()) {
            int idx = sequenceIndex % animFrames.size();
            return animFrames.get(idx).getTime(metadata.getDefaultFrameTime());
        }
        return metadata.getDefaultFrameTime();
    }
    
    /**
     * Get the number of frames in the animation sequence.
     */
    private int getSequenceLength() {
        List<mattmc.client.resources.metadata.animation.AnimationFrame> animFrames = metadata.getFrames();
        return animFrames.isEmpty() ? frameCount : animFrames.size();
    }
    
    /**
     * Advance the animation by one tick.
     * 
     * @return true if the frame changed, false otherwise
     */
    public boolean tick() {
        subFrameTicks++;
        int currentFrameTime = getFrameTime(currentFrameIndex);
        
        if (subFrameTicks >= currentFrameTime) {
            subFrameTicks = 0;
            int prevFrame = currentFrame;
            currentFrameIndex = (currentFrameIndex + 1) % getSequenceLength();
            currentFrame = getFrameIndex(currentFrameIndex);
            return prevFrame != currentFrame;
        }
        
        return false;
    }
    
    /**
     * Get interpolated frame data for smooth animation.
     * Returns the interpolation progress (0.0 to 1.0) between current and next frame.
     */
    public float getInterpolationProgress() {
        if (!metadata.isInterpolatedFrames()) {
            return 0.0f;
        }
        int frameTime = getFrameTime(currentFrameIndex);
        return (float) subFrameTicks / (float) frameTime;
    }
    
    /**
     * Get the next frame image for interpolation.
     */
    public BufferedImage getNextFrame() {
        int nextFrameIndex = (currentFrameIndex + 1) % getSequenceLength();
        return getFrame(getFrameIndex(nextFrameIndex));
    }
    
    public String getTexturePath() {
        return texturePath;
    }
    
    public int getFrameWidth() {
        return frameWidth;
    }
    
    public int getFrameHeight() {
        return frameHeight;
    }
    
    public int getFrameCount() {
        return frameCount;
    }
    
    public AnimationMetadataSection getMetadata() {
        return metadata;
    }
    
    public int getCurrentFrameIndex() {
        return currentFrame;
    }
    
    public boolean isInterpolated() {
        return metadata.isInterpolatedFrames();
    }
}
