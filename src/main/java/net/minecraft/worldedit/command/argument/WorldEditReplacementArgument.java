package net.minecraft.worldedit.command.argument;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.worldedit.pattern.BlockPatternParser;
import net.minecraft.worldedit.pattern.BlockPatternParser.ReplacementPatterns;
import net.minecraft.worldedit.pattern.Pattern;

public final class WorldEditReplacementArgument implements ArgumentType<WorldEditReplacementArgument.Result> {
    private static final Collection<String> EXAMPLES = List.of(
        "stone cobblestone",
        "stone,dirt cobblestone,diorite",
        "stone,dirt 70%cobblestone,30%diorite",
        "oak_stairs[facing=east,half=top],stone cobblestone"
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_REPLACEMENT = new DynamicCommandExceptionType(
        message -> Component.literal(String.valueOf(message))
    );

    private final Mode mode;

    private WorldEditReplacementArgument(Mode mode) {
        this.mode = mode;
    }

    public static WorldEditReplacementArgument replacement() {
        return new WorldEditReplacementArgument(Mode.ALLOW_SINGLE_OUTPUT_PATTERN);
    }

    public static WorldEditReplacementArgument replacementPatterns() {
        return new WorldEditReplacementArgument(Mode.REQUIRE_INPUT_AND_OUTPUT_PATTERNS);
    }

    public static Result getReplacement(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Result.class);
    }

    public static ReplacementPatterns getReplacementPatterns(CommandContext<CommandSourceStack> context, String name) {
        return getReplacement(context, name).replacementPatterns()
            .orElseThrow(() -> new IllegalStateException("Expected input and output block patterns"));
    }

    public boolean requiresInputAndOutputPatterns() {
        return this.mode == Mode.REQUIRE_INPUT_AND_OUTPUT_PATTERNS;
    }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String expression = reader.getRemaining();
        reader.setCursor(reader.getTotalLength());

        if (this.mode == Mode.ALLOW_SINGLE_OUTPUT_PATTERN) {
            try {
                return Result.replaceExistingBlocks(BlockPatternParser.parse(expression));
            } catch (IllegalArgumentException ignored) {
                // Fall through to the from-pattern/to-pattern form.
            }
        }

        try {
            return Result.replaceMatchingBlocks(BlockPatternParser.parseReplacementPatterns(expression));
        } catch (IllegalArgumentException e) {
            reader.setCursor(start);
            throw ERROR_INVALID_REPLACEMENT.createWithContext(reader, e.getMessage());
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private enum Mode {
        ALLOW_SINGLE_OUTPUT_PATTERN,
        REQUIRE_INPUT_AND_OUTPUT_PATTERNS
    }

    public record Result(Optional<ReplacementPatterns> replacementPatterns, Pattern toPattern) {
        public Result {
            Objects.requireNonNull(replacementPatterns, "replacementPatterns");
            Objects.requireNonNull(toPattern, "toPattern");
        }

        private static Result replaceExistingBlocks(Pattern pattern) {
            return new Result(Optional.empty(), pattern);
        }

        private static Result replaceMatchingBlocks(ReplacementPatterns replacementPatterns) {
            return new Result(Optional.of(replacementPatterns), replacementPatterns.to());
        }
    }

    public static class Info implements ArgumentTypeInfo<WorldEditReplacementArgument, Info.Template> {
        @Override
        public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
            buffer.writeBoolean(template.requireInputAndOutputPatterns);
        }

        @Override
        public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new Template(buffer.readBoolean());
        }

        @Override
        public void serializeToJson(Template template, JsonObject json) {
            json.addProperty("require_input_pattern", template.requireInputAndOutputPatterns);
        }

        @Override
        public Template unpack(WorldEditReplacementArgument argument) {
            return new Template(argument.requiresInputAndOutputPatterns());
        }

        public final class Template implements ArgumentTypeInfo.Template<WorldEditReplacementArgument> {
            private final boolean requireInputAndOutputPatterns;

            private Template(boolean requireInputAndOutputPatterns) {
                this.requireInputAndOutputPatterns = requireInputAndOutputPatterns;
            }

            @Override
            public WorldEditReplacementArgument instantiate(CommandBuildContext context) {
                return this.requireInputAndOutputPatterns
                    ? WorldEditReplacementArgument.replacementPatterns()
                    : WorldEditReplacementArgument.replacement();
            }

            @Override
            public ArgumentTypeInfo<WorldEditReplacementArgument, ?> type() {
                return Info.this;
            }
        }
    }
}
