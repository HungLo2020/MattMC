package frnsrc.sodium;

public interface UploadResourceBudget {
    boolean isAvailable();

    void consume(long duration, long size);
}
