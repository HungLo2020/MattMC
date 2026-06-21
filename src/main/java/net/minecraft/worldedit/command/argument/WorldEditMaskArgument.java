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
import net.minecraft.worldedit.mask.Mask;
import net.minecraft.worldedit.pattern.BlockPatternParser;

public final class WorldEditMaskArgument implements ArgumentType<Mask> {
    private static final Collection<String> EXAMPLES = List.of(
        "sand",
        "stone,dirt",
        "oak_stairs[facing=east,half=top],stone"
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_MASK = new DynamicCommandExceptionType(
        message -> Component.literal(String.valueOf(message))
    );

    private WorldEditMaskArgument() {
    }

    public static WorldEditMaskArgument mask() {
        return new WorldEditMaskArgument();
    }

    public static Mask getMask(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Mask.class);
    }

    @Override
    public Mask parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String expression = readUntilWhitespace(reader);

        try {
            return BlockPatternParser.parseMask(expression);
        } catch (IllegalArgumentException e) {
            reader.setCursor(start);
            throw ERROR_INVALID_MASK.createWithContext(reader, e.getMessage());
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static String readUntilWhitespace(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }
}
