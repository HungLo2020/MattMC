package mattmc.client.settings;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Tests for KeyNameParser handling punctuation characters.
 */
public class KeyNameParserPunctuationTest {
    
    @Test
    public void testParseForwardSlash() {
        Integer keyCode = KeyNameParser.parseKeyName("/");
        assertNotNull(keyCode, "Forward slash should be recognized");
        assertEquals(GLFW_KEY_SLASH, keyCode.intValue());
    }
    
    @Test
    public void testGetKeyNameForwardSlash() {
        String keyName = KeyNameParser.getKeyName(GLFW_KEY_SLASH);
        assertEquals("/", keyName);
    }
    
    @Test
    public void testParseBackslash() {
        Integer keyCode = KeyNameParser.parseKeyName("\\");
        assertNotNull(keyCode, "Backslash should be recognized");
        assertEquals(GLFW_KEY_BACKSLASH, keyCode.intValue());
    }
    
    @Test
    public void testParsePunctuation() {
        assertEquals(GLFW_KEY_COMMA, KeyNameParser.parseKeyName(",").intValue());
        assertEquals(GLFW_KEY_PERIOD, KeyNameParser.parseKeyName(".").intValue());
        assertEquals(GLFW_KEY_SEMICOLON, KeyNameParser.parseKeyName(";").intValue());
        assertEquals(GLFW_KEY_APOSTROPHE, KeyNameParser.parseKeyName("'").intValue());
        assertEquals(GLFW_KEY_LEFT_BRACKET, KeyNameParser.parseKeyName("[").intValue());
        assertEquals(GLFW_KEY_RIGHT_BRACKET, KeyNameParser.parseKeyName("]").intValue());
        assertEquals(GLFW_KEY_MINUS, KeyNameParser.parseKeyName("-").intValue());
        assertEquals(GLFW_KEY_EQUAL, KeyNameParser.parseKeyName("=").intValue());
        assertEquals(GLFW_KEY_GRAVE_ACCENT, KeyNameParser.parseKeyName("`").intValue());
    }
    
    @Test
    public void testGetKeyNamePunctuation() {
        assertEquals(",", KeyNameParser.getKeyName(GLFW_KEY_COMMA));
        assertEquals(".", KeyNameParser.getKeyName(GLFW_KEY_PERIOD));
        assertEquals(";", KeyNameParser.getKeyName(GLFW_KEY_SEMICOLON));
        assertEquals("'", KeyNameParser.getKeyName(GLFW_KEY_APOSTROPHE));
        assertEquals("[", KeyNameParser.getKeyName(GLFW_KEY_LEFT_BRACKET));
        assertEquals("]", KeyNameParser.getKeyName(GLFW_KEY_RIGHT_BRACKET));
        assertEquals("-", KeyNameParser.getKeyName(GLFW_KEY_MINUS));
        assertEquals("=", KeyNameParser.getKeyName(GLFW_KEY_EQUAL));
        assertEquals("`", KeyNameParser.getKeyName(GLFW_KEY_GRAVE_ACCENT));
    }
    
    @Test
    public void testRoundTripForwardSlash() {
        // Parse "/" to key code, then convert back to name
        Integer keyCode = KeyNameParser.parseKeyName("/");
        String keyName = KeyNameParser.getKeyName(keyCode);
        assertEquals("/", keyName);
    }
}
