package mattmc.client.sounds;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for OpenAL error checking.
 * Mirroring Minecraft's OpenAlUtil.
 */
public final class OpenALUtils {

    private static final Logger logger = LoggerFactory.getLogger(OpenALUtils.class);

    private OpenALUtils() {
        // Utility class
    }

    /**
     * Check for and log any OpenAL errors.
     * @param operation Description of the operation being checked
     * @return true if an error occurred, false otherwise
     */
    public static boolean checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            logger.error("OpenAL error during '{}': {} (0x{})",
                operation, getALErrorString(error), Integer.toHexString(error));
            return true;
        }
        return false;
    }

    /**
     * Check for and log any OpenAL context (ALC) errors.
     * @param device The device handle to check errors on
     * @param operation Description of the operation being checked
     * @return true if an error occurred, false otherwise
     */
    public static boolean checkALCError(long device, String operation) {
        int error = ALC10.alcGetError(device);
        if (error != ALC10.ALC_NO_ERROR) {
            logger.error("OpenAL context error during '{}': {} (0x{})",
                operation, getALCErrorString(error), Integer.toHexString(error));
            return true;
        }
        return false;
    }

    /**
     * Get a human-readable string for an AL error code.
     */
    private static String getALErrorString(int error) {
        return switch (error) {
            case AL10.AL_INVALID_NAME -> "Invalid name";
            case AL10.AL_INVALID_ENUM -> "Invalid enum";
            case AL10.AL_INVALID_VALUE -> "Invalid value";
            case AL10.AL_INVALID_OPERATION -> "Invalid operation";
            case AL10.AL_OUT_OF_MEMORY -> "Out of memory";
            default -> "Unknown error";
        };
    }

    /**
     * Get a human-readable string for an ALC error code.
     */
    private static String getALCErrorString(int error) {
        return switch (error) {
            case ALC10.ALC_INVALID_DEVICE -> "Invalid device";
            case ALC10.ALC_INVALID_CONTEXT -> "Invalid context";
            case ALC10.ALC_INVALID_ENUM -> "Invalid enum";
            case ALC10.ALC_INVALID_VALUE -> "Invalid value";
            case ALC10.ALC_OUT_OF_MEMORY -> "Out of memory";
            default -> "Unknown error";
        };
    }
}
