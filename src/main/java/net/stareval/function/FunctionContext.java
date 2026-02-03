package net.stareval.function;

import net.stareval.expression.Expression;

public interface FunctionContext {
	Expression getVariable(String name);

	boolean hasVariable(String name);
}
