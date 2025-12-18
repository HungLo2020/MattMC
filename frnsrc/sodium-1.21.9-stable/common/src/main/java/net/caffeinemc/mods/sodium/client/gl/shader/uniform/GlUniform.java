package frnsrc.sodium;

public abstract class GlUniform<T> {
    protected final int index;

    protected GlUniform(int index) {
        this.index = index;
    }

    public abstract void set(T value);
}
