package net.minecraft.client.auth;

/**
 * Stub exception for Minecraft client errors - not used in offline mode.
 */
public class MinecraftClientException extends Exception {
    public MinecraftClientException(String message) {
        super(message);
    }
    
    public MinecraftClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
