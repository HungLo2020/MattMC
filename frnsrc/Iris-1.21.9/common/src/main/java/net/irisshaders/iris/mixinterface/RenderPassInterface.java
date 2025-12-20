package frnsrc.Iris;

public interface RenderPassInterface {
	default void iris$setCustomPass(CustomPass pass) {
		throw new UnsupportedOperationException();
	}

	default CustomPass iris$getCustomPass() {
		throw new UnsupportedOperationException();
	}
}
