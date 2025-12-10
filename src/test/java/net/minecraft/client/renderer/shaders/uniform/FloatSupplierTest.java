package net.minecraft.client.renderer.shaders.uniform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FloatSupplier functional interface.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class FloatSupplierTest {

	@Test
	public void testLambdaImplementation() {
		FloatSupplier supplier = () -> 3.14f;
		assertEquals(3.14f, supplier.getAsFloat(), 0.001f);
	}

	@Test
	public void testMethodReference() {
		class TestClass {
			float getValue() {
				return 2.71f;
			}
		}
		
		TestClass test = new TestClass();
		FloatSupplier supplier = test::getValue;
		assertEquals(2.71f, supplier.getAsFloat(), 0.001f);
	}

	@Test
	public void testDynamicValue() {
		float[] value = {1.0f};
		FloatSupplier supplier = () -> value[0];
		
		assertEquals(1.0f, supplier.getAsFloat(), 0.001f);
		
		value[0] = 5.0f;
		assertEquals(5.0f, supplier.getAsFloat(), 0.001f);
	}

	@Test
	public void testConstantValue() {
		FloatSupplier zero = () -> 0.0f;
		FloatSupplier one = () -> 1.0f;
		FloatSupplier negative = () -> -10.5f;
		
		assertEquals(0.0f, zero.getAsFloat(), 0.001f);
		assertEquals(1.0f, one.getAsFloat(), 0.001f);
		assertEquals(-10.5f, negative.getAsFloat(), 0.001f);
	}
}
