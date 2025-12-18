package frnsrc.Iris;

import kroppeb.stareval.expression.Expression;

public interface FunctionContext {
	Expression getVariable(String name);

	boolean hasVariable(String name);
}
