package frnsrc.Iris;

public interface IrisProgram {
	void iris$setupState();

	void iris$clearState();

	int iris$getBlockIndex(int program, CharSequence uniformBlockName);

	boolean iris$isSetUp();
}
