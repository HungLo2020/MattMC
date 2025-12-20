package frnsrc.Iris;

public record UnaryOp(String name) {


	@Override
	public String toString() {
		return this.name;
	}
}
