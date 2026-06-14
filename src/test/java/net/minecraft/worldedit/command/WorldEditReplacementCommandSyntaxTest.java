package net.minecraft.worldedit.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEditReplacementCommandSyntaxTest {
    private static final Path SRC_MAIN_JAVA = Path.of("src/main/java");

    @Test
    void replaceUsesGreedyPatternTailInsteadOfCommaRejectingWordArguments() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/worldedit/command/RegionCommands.java"));

        assertTrue(source.contains("Commands.argument(\"patterns\", StringArgumentType.greedyString())"));
        assertTrue(source.contains("BlockPatternParser.parseReplacementPatterns(patterns)"));
        assertFalse(source.contains("Commands.argument(\"from\", StringArgumentType.word())\n                .then(Commands.argument(\"to\""));
    }

    @Test
    void replaceNearUsesGreedyPatternTailInsteadOfCommaRejectingWordArguments() throws IOException {
        String source = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/worldedit/command/UtilityCommands.java"));

        assertTrue(source.contains("Commands.argument(\"patterns\", StringArgumentType.greedyString())"));
        assertTrue(source.contains("BlockPatternParser.parseReplacementPatterns(patterns)"));
        assertFalse(source.contains("Commands.argument(\"from\", StringArgumentType.word())\n                    .then(Commands.argument(\"to\""));
    }
}
