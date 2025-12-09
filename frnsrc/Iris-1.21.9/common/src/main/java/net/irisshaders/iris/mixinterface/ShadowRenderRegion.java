package frnsrc.Iris;

public interface ShadowRenderRegion {
	void swapToRegularRenderList();

	void swapToShadowRenderList();

	void iris$forceClearAllBatches();
}
