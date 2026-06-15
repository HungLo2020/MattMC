package net.minecraft.world.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrowelItemTest {
    private static final Path SRC_MAIN_JAVA = Path.of("src/main/java");

    @Test
    void trowelEchoesPlaceSoundToUsingPlayer() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/world/item/TrowelItem.java"));

        assertTrue(source.contains("InteractionResult result = blockItem.place(placeContext)"));
        assertTrue(source.contains("result.consumesAction() && player instanceof ServerPlayer serverPlayer"));
        assertTrue(source.contains("playPlaceSoundForPlayer(serverPlayer, level, placeContext.getClickedPos())"));
        assertTrue(source.contains("new ClientboundSoundPacket"));
        assertTrue(source.contains("BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundType.getPlaceSound())"));
        assertTrue(source.contains("(soundType.getVolume() + 1.0F) / 2.0F"));
        assertTrue(source.contains("soundType.getPitch() * 0.8F"));
    }
}
