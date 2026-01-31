package net.stareval.element.tree;

import net.stareval.element.ExpressionElement;
import net.stareval.parser.UnaryOp;

public record UnaryExpressionElement(UnaryOp op, ExpressionElement inner) implements ExpressionElement {


	@Override
	public String toString() {
		return "UnaryExpr{" + this.op + " {" + this.inner + "} }";
	}
}
