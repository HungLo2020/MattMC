package kroppeb.stareval.element.tree;

import net.iris.stareval.element.AccessibleExpressionElement;

public record AccessExpressionElement(AccessibleExpressionElement base,
									  String index) implements AccessibleExpressionElement {


	@Override
	public String toString() {
		return "Access{" + this.base + "[" + this.index + "]}";
	}
}
