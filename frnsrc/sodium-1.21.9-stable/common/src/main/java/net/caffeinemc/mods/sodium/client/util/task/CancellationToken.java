package frnsrc.sodium;

public interface CancellationToken {
    boolean isCancelled();

    void setCancelled();
}
