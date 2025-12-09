package frnsrc.Iris;

import java.util.function.BooleanSupplier;

public class BooleanUniform extends IntUniform {
	BooleanUniform(int location, BooleanSupplier value) {
		super(location, () -> value.getAsBoolean() ? 1 : 0);
	}
}
