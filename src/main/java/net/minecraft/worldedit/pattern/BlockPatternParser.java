package net.minecraft.worldedit.pattern;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.mask.BlockSetMask;
import net.minecraft.worldedit.mask.Mask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the compact WorldEdit block pattern syntax supported by MattMC.
 */
public final class BlockPatternParser {
    private BlockPatternParser() {
    }

    public static Pattern parse(String expression) {
        List<Entry> entries = parseEntries(expression);
        if (entries.size() == 1) {
            return new SingleBlockPattern(entries.get(0).state);
        }

        RandomPattern pattern = new RandomPattern();
        for (Entry entry : entries) {
            pattern.add(entry.state, entry.weight);
        }
        return pattern;
    }

    public static Mask parseMask(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing input block pattern");
        }

        List<String> parts = splitTopLevel(expression, ',');
        Set<BlockState> states = new LinkedHashSet<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty block in input pattern: " + expression);
            }
            if (firstTopLevelChar(token, '%') >= 0) {
                throw new IllegalArgumentException("Input block patterns do not support weights: " + token);
            }
            states.add(parseSingleBlockState(token));
        }

        return new BlockSetMask(states);
    }

    public static ReplacementPatterns parseReplacementPatterns(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Missing replacement patterns");
        }

        int split = firstTopLevelWhitespace(trimmed);
        if (split < 0) {
            throw new IllegalArgumentException("Expected input and output block patterns");
        }

        String fromExpression = trimmed.substring(0, split).trim();
        String toExpression = trimmed.substring(split).trim();
        return new ReplacementPatterns(parseMask(fromExpression), parse(toExpression));
    }

    public static BlockState parseSingleBlockState(String blockName) {
        String trimmed = blockName == null ? "" : blockName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Missing block name");
        }

        StringReader reader = new StringReader(trimmed);
        try {
            BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, reader, true);
            if (reader.canRead()) {
                throw new IllegalArgumentException("Invalid block: " + trimmed);
            }
            return result.blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Invalid block: " + trimmed, e);
        }
    }

    private static List<Entry> parseEntries(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing block pattern");
        }

        List<String> parts = splitTopLevel(expression, ',');
        List<PartialEntry> partialEntries = new ArrayList<>(parts.size());

        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty block in pattern: " + expression);
            }

            PartialEntry entry = parseEntry(token);
            partialEntries.add(entry);
        }

        if (partialEntries.size() == 1 && partialEntries.get(0).weight != null) {
            throw new IllegalArgumentException("Weighted block patterns require at least two entries");
        }

        List<Entry> entries = new ArrayList<>(partialEntries.size());
        for (PartialEntry partialEntry : partialEntries) {
            double weight = partialEntry.weight == null ? 1 : partialEntry.weight;
            entries.add(new Entry(partialEntry.state, weight));
        }
        return entries;
    }

    private static PartialEntry parseEntry(String token) {
        int percentIndex = firstTopLevelChar(token, '%');
        if (percentIndex < 0) {
            return new PartialEntry(parseSingleBlockState(token), null);
        }
        if (percentIndex == 0 || percentIndex == token.length() - 1) {
            throw new IllegalArgumentException("Invalid weighted block pattern entry: " + token);
        }

        String weightText = token.substring(0, percentIndex).trim();
        String blockText = token.substring(percentIndex + 1).trim();
        double weight;
        try {
            weight = Double.parseDouble(weightText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid block pattern weight: " + weightText, e);
        }
        if (!Double.isFinite(weight) || weight <= 0) {
            throw new IllegalArgumentException("Block pattern weight must be greater than zero: " + weightText);
        }

        return new PartialEntry(parseSingleBlockState(blockText), weight);
    }

    private static int firstTopLevelWhitespace(String value) {
        return firstTopLevelMatch(value, '\0', true);
    }

    private static int firstTopLevelChar(String value, char match) {
        return firstTopLevelMatch(value, match, false);
    }

    private static int firstTopLevelMatch(String value, char match, boolean matchWhitespace) {
        int squareDepth = 0;
        int braceDepth = 0;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quote != 0) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '[') {
                squareDepth++;
                continue;
            }
            if (c == ']' && squareDepth > 0) {
                squareDepth--;
                continue;
            }
            if (c == '{') {
                braceDepth++;
                continue;
            }
            if (c == '}' && braceDepth > 0) {
                braceDepth--;
                continue;
            }
            if (squareDepth == 0 && braceDepth == 0 && (matchWhitespace ? Character.isWhitespace(c) : c == match)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String value, char separator) {
        List<String> parts = new ArrayList<>();
        int squareDepth = 0;
        int braceDepth = 0;
        int start = 0;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quote != 0) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '[') {
                squareDepth++;
                continue;
            }
            if (c == ']' && squareDepth > 0) {
                squareDepth--;
                continue;
            }
            if (c == '{') {
                braceDepth++;
                continue;
            }
            if (c == '}' && braceDepth > 0) {
                braceDepth--;
                continue;
            }
            if (squareDepth == 0 && braceDepth == 0 && c == separator) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }

        parts.add(value.substring(start));
        return parts;
    }

    private record PartialEntry(BlockState state, Double weight) {
    }

    private record Entry(BlockState state, double weight) {
    }

    public record ReplacementPatterns(Mask from, Pattern to) {
    }
}
