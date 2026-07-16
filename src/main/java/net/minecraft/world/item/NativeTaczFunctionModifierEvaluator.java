package net.minecraft.world.item;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

final class NativeTaczFunctionModifierEvaluator {
	private static final int OK = 0;
	private static final MethodHandle EVAL = NativeLibraryLoader.downcallHandle("mattmc_rust",
			"mattmc_tacz_function_modifier_eval",
			FunctionDescriptor.of(ValueLayout.JAVA_INT,
					ValueLayout.ADDRESS,
					ValueLayout.JAVA_LONG,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.JAVA_DOUBLE,
					ValueLayout.ADDRESS));

	private NativeTaczFunctionModifierEvaluator() {
	}

	static double eval(double value, double input, String function) {
		try {
			return evalNative(value, input, function);
		} catch (Throwable throwable) {
			return value;
		}
	}

	private static double evalNative(double value, double input, String function) throws Throwable {
		byte[] expression = function.getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment expressionSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, expression);
			MemorySegment output = arena.allocate(ValueLayout.JAVA_DOUBLE);
			int status = (int)EVAL.invokeExact(expressionSegment, (long)expression.length, value, input, output);
			return status == OK ? output.get(ValueLayout.JAVA_DOUBLE, 0) : value;
		}
	}
}
