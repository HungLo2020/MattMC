package net.minecraft.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

/**
 * Example test class demonstrating the testing infrastructure.
 * This tests basic math utility functions from the Mth class.
 */
@DisplayName("Mth Utility Tests")
class MthTest {
    
    @Test
    @DisplayName("should clamp values within range")
    void testClamp() {
        // Test value within range
        assertThat(Mth.clamp(5, 0, 10)).isEqualTo(5);
        
        // Test value below minimum
        assertThat(Mth.clamp(-5, 0, 10)).isEqualTo(0);
        
        // Test value above maximum
        assertThat(Mth.clamp(15, 0, 10)).isEqualTo(10);
        
        // Test edge cases
        assertThat(Mth.clamp(0, 0, 10)).isEqualTo(0);
        assertThat(Mth.clamp(10, 0, 10)).isEqualTo(10);
    }
    
    @Test
    @DisplayName("should calculate square root correctly")
    void testSqrt() {
        assertThat(Mth.sqrt(0.0f)).isCloseTo(0.0f, within(0.001f));
        assertThat(Mth.sqrt(1.0f)).isCloseTo(1.0f, within(0.001f));
        assertThat(Mth.sqrt(4.0f)).isCloseTo(2.0f, within(0.001f));
        assertThat(Mth.sqrt(9.0f)).isCloseTo(3.0f, within(0.001f));
        assertThat(Mth.sqrt(16.0f)).isCloseTo(4.0f, within(0.001f));
    }
    
    @Test
    @DisplayName("should calculate floor correctly")
    void testFloor() {
        assertThat(Mth.floor(0.0)).isEqualTo(0);
        assertThat(Mth.floor(1.5)).isEqualTo(1);
        assertThat(Mth.floor(2.9)).isEqualTo(2);
        assertThat(Mth.floor(-1.5)).isEqualTo(-2);
        assertThat(Mth.floor(-2.9)).isEqualTo(-3);
    }
}
