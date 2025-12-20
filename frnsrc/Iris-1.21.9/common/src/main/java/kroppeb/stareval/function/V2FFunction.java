package frnsrc.Iris;

import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.function.FunctionContext;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.TypedFunction;

@FunctionalInterface
public interface V2FFunction extends TypedFunction {
	float eval();

	@Override
	default void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
		functionReturn.floatReturn = this.eval();
	}

	@Override
	default Type getReturnType() {
		return Type.Float;
	}

	@Override
	default Parameter[] getParameters() {
		return new Parameter[]{};
	}
}
