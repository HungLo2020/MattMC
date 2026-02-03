package net.stareval.element;

public interface PriorityOperatorElement extends Element {
	int getPriority();

	ExpressionElement resolveWith(ExpressionElement right);
}
