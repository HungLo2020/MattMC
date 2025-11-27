package mattmc.client.resources.metadata.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents animation metadata from a .mcmeta file.
 * Matches Minecraft's AnimationMetadataSection class.
 * 
 * The .mcmeta file format:
 * {
 *   "animation": {
 *     "interpolate": true/false,  // optional, smooth transitions between frames
 *     "frametime": N,             // optional, default ticks per frame (default: 1)
 *     "width": N,                 // optional, frame width (default: auto-detect)
 *     "height": N,                // optional, frame height (default: auto-detect)
 *     "frames": [...]             // optional, explicit frame order and timing
 *   }
 * }
 */
public class AnimationMetadataSection {
    private static final Logger logger = LoggerFactory.getLogger(AnimationMetadataSection.class);
    
    public static final String SECTION_NAME = "animation";
    public static final int DEFAULT_FRAME_TIME = 1;
    public static final int UNKNOWN_SIZE = -1;
    
    /** Empty animation metadata for non-animated textures */
    public static final AnimationMetadataSection EMPTY = new AnimationMetadataSection(
            new ArrayList<>(), UNKNOWN_SIZE, UNKNOWN_SIZE, DEFAULT_FRAME_TIME, false) {
        @Override
        public FrameSize calculateFrameSize(int width, int height) {
            return new FrameSize(width, height);
        }
    };
    
    private final List<AnimationFrame> frames;
    private final int frameWidth;
    private final int frameHeight;
    private final int defaultFrameTime;
    private final boolean interpolatedFrames;

    public AnimationMetadataSection(List<AnimationFrame> frames, int frameWidth, int frameHeight, 
                                     int defaultFrameTime, boolean interpolatedFrames) {
        this.frames = frames;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.defaultFrameTime = defaultFrameTime;
        this.interpolatedFrames = interpolatedFrames;
    }

    /**
     * Calculate the frame size based on the texture dimensions and metadata.
     * Matches Minecraft's behavior exactly.
     */
    public FrameSize calculateFrameSize(int textureWidth, int textureHeight) {
        if (this.frameWidth != UNKNOWN_SIZE) {
            return this.frameHeight != UNKNOWN_SIZE 
                    ? new FrameSize(this.frameWidth, this.frameHeight) 
                    : new FrameSize(this.frameWidth, textureHeight);
        } else if (this.frameHeight != UNKNOWN_SIZE) {
            return new FrameSize(textureWidth, this.frameHeight);
        } else {
            // Auto-detect: use smallest dimension as frame size (assumes square frames)
            int size = Math.min(textureWidth, textureHeight);
            return new FrameSize(size, size);
        }
    }

    public int getDefaultFrameTime() {
        return this.defaultFrameTime;
    }

    public boolean isInterpolatedFrames() {
        return this.interpolatedFrames;
    }

    public List<AnimationFrame> getFrames() {
        return this.frames;
    }

    /**
     * Iterate over frames, using calculated frame count if no explicit frames specified.
     */
    public void forEachFrame(FrameOutput output) {
        for (AnimationFrame frame : this.frames) {
            output.accept(frame.getIndex(), frame.getTime(this.defaultFrameTime));
        }
    }

    /**
     * Load animation metadata from a .mcmeta file stream.
     * 
     * @param inputStream the input stream for the .mcmeta file (will be closed by this method)
     * @return the parsed AnimationMetadataSection, or EMPTY if parsing fails
     */
    public static AnimationMetadataSection load(InputStream inputStream) {
        try (InputStream is = inputStream;
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            
            if (!root.has(SECTION_NAME)) {
                return EMPTY;
            }
            
            JsonObject animationObj = root.getAsJsonObject(SECTION_NAME);
            return parseAnimation(animationObj);
        } catch (Exception e) {
            logger.error("Failed to load animation metadata: {}", e.getMessage());
            return EMPTY;
        }
    }

    /**
     * Parse the animation section of the mcmeta file.
     * Matches Minecraft's AnimationMetadataSectionSerializer.
     */
    private static AnimationMetadataSection parseAnimation(JsonObject json) {
        List<AnimationFrame> frames = new ArrayList<>();
        
        // Parse frametime (default: 1)
        int frameTime = getInt(json, "frametime", DEFAULT_FRAME_TIME);
        if (frameTime < 1) {
            logger.warn("Invalid frame time {}, using default", frameTime);
            frameTime = DEFAULT_FRAME_TIME;
        }
        
        // Parse frames array if present
        if (json.has("frames")) {
            JsonArray framesArray = json.getAsJsonArray("frames");
            for (int i = 0; i < framesArray.size(); i++) {
                JsonElement element = framesArray.get(i);
                AnimationFrame frame = parseFrame(i, element);
                if (frame != null) {
                    frames.add(frame);
                }
            }
        }
        
        // Parse optional width/height
        int width = getInt(json, "width", UNKNOWN_SIZE);
        int height = getInt(json, "height", UNKNOWN_SIZE);
        
        if (width != UNKNOWN_SIZE && width < 1) {
            logger.warn("Invalid width {}", width);
            width = UNKNOWN_SIZE;
        }
        if (height != UNKNOWN_SIZE && height < 1) {
            logger.warn("Invalid height {}", height);
            height = UNKNOWN_SIZE;
        }
        
        // Parse interpolate flag
        boolean interpolate = getBoolean(json, "interpolate", false);
        
        return new AnimationMetadataSection(frames, width, height, frameTime, interpolate);
    }

    /**
     * Parse a single frame from the frames array.
     */
    private static AnimationFrame parseFrame(int frameIdx, JsonElement element) {
        if (element.isJsonPrimitive()) {
            // Simple format: just the frame index
            return new AnimationFrame(element.getAsInt());
        } else if (element.isJsonObject()) {
            // Object format: {"index": N, "time": N}
            JsonObject obj = element.getAsJsonObject();
            int index = getInt(obj, "index", -1);
            int time = getInt(obj, "time", AnimationFrame.UNKNOWN_FRAME_TIME);
            
            if (index < 0) {
                logger.warn("Invalid frame index in frames[{}]", frameIdx);
                return null;
            }
            
            return new AnimationFrame(index, time);
        }
        return null;
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        return json.has(key) ? json.get(key).getAsInt() : defaultValue;
    }

    private static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        return json.has(key) ? json.get(key).getAsBoolean() : defaultValue;
    }

    @FunctionalInterface
    public interface FrameOutput {
        void accept(int index, int time);
    }
}
