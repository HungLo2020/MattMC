package net.minecraft.worldedit.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.worldedit.pattern.BlockPatternParser;
import net.minecraft.worldedit.pattern.Pattern;

public final class WorldEditPatternArgument implements ArgumentType<Pattern> {
    private static final Collection<String> EXAMPLES = List.of(
        "stone",
        "cobblestone,diorite",
        "70%cobblestone,30%diorite",
        "oak_stairs[facing=east,half=top]"
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_PATTERN = new DynamicCommandExceptionType(
        message -> Component.literal(String.valueOf(message))
    );

    private WorldEditPatternArgument() {
    }

    public static WorldEditPatternArgument pattern() {
        return new WorldEditPatternArgument();
    }

    public static Pattern getPattern(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Pattern.class);
    }

    @Override
    public Pattern parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String expression = reader.getRemaining();
        reader.setCursor(reader.getTotalLength());

        try {
            return BlockPatternParser.parse(expression);
        } catch (IllegalArgumentException e) {
            reader.setCursor(start);
            throw ERROR_INVALID_PATTERN.createWithContext(reader, e.getMessage());
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
