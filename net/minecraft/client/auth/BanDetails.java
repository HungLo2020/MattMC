package net.minecraft.client.auth;

import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/**
 * Stub for ban details from Mojang services.
 * In offline mode, bans are not checked against Mojang servers.
 */
public record BanDetails(Instant expires, String reason, @Nullable String reasonMessage) {
}
