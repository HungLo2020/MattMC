package kroppeb.stareval.element;

import net.iris.stareval.element.Element;

public interface PriorityOperatorElement extends Element {
	int getPriority();

	ExpressionElement resolveWith(ExpressionElement right);
}
