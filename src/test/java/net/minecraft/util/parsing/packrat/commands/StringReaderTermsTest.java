package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringReaderTermsTest {
    @Test
    void singleCharacterTerminalAcceptsOnlyExpectedCharacter() throws CommandSyntaxException {
        Grammar<String> grammar = grammarFor(StringReaderTerms.character('['));

        StringReader accepted = new StringReader("[");
        assertEquals("ok", grammar.parseForCommands(accepted));
        assertEquals(1, accepted.getCursor());

        StringReader rejected = new StringReader("9");
        assertThrows(CommandSyntaxException.class, () -> grammar.parseForCommands(rejected));
    }

    @Test
    void twoCharacterTerminalAcceptsOnlyExpectedCharacters() throws CommandSyntaxException {
        Grammar<String> grammar = grammarFor(StringReaderTerms.characters('u', 'U'));

        StringReader lower = new StringReader("u");
        assertEquals("ok", grammar.parseForCommands(lower));
        assertEquals(1, lower.getCursor());

        StringReader upper = new StringReader("U");
        assertEquals("ok", grammar.parseForCommands(upper));
        assertEquals(1, upper.getCursor());

        StringReader rejected = new StringReader("x");
        assertThrows(CommandSyntaxException.class, () -> grammar.parseForCommands(rejected));
    }

    private static Grammar<String> grammarFor(net.minecraft.util.parsing.packrat.Term<StringReader> term) {
        Atom<String> top = Atom.of("top");
        Dictionary<StringReader> dictionary = new Dictionary<>();
        dictionary.put(top, term, scope -> "ok");
        return new Grammar<>(dictionary, dictionary.getOrThrow(top));
    }
}
