package kroppeb.stareval.element;

import kroppeb.stareval.element.Element;

public interface PriorityOperatorElement extends Element {
	int getPriority();

	ExpressionElement resolveWith(ExpressionElement right);
}
