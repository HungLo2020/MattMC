package frnsrc.Iris;

public interface RenderTargetInterface {
	default void iris$bindFramebuffer() {
		throw new AssertionError("Impossible to access.");
	}
}
